# Archiver 脚本结构化归档设计

## 背景

仓库里有一批历史归档脚本，集中放在 `archiver/` 目录下。这些脚本直接调用 `pt-archiver`，承担线上多业务库的数据归档、清理、迁移到 legacy 库等工作。

当前系统已经有归档任务、归档步骤、变量查询、Quartz 调度、执行日志和告警配置，但现有模型只覆盖一种默认场景：每个归档步骤都有源库和目标库，也就是始终带 `--dest` 的普通归档。

新的方向是不再在系统里运行 bash，也不做运行时 bash 导入器。我们先补齐系统的结构化归档能力，再把历史脚本转换成可审查的 SQL seed 文件，用 SQL 直接写入标准的归档任务、变量、批次配置和步骤。

## 目标

- 将现有所有 `archiver/**/*.sh` 脚本表达为系统托管的结构化归档任务。
- 不建设通用 bash 导入器，也不把 shell 脚本执行作为长期集成方式。
- 支持 `pt-archiver --purge` 这类没有 `--dest` 的纯删除任务。
- 支持步骤级源表索引名，例如 `i=idx_c_userid`。
- 支持单值变量查询，例如 `maxid=$(mysql ... "select max(id) ...")`。
- 支持订单类复杂脚本，将 while 分批和临时表逻辑拆解为系统内的批次归档能力。
- 产出可 review、可重复执行策略清晰的 SQL seed 文件。
- SQL seed 不扩散旧脚本里的数据库明文密码。

## 非目标

- 不做运行时 bash 解析器或导入页面。
- 不在后端实现通用 shell 解释器。
- 不从旧脚本中自动提取、加密并写入数据库连接密码。
- 不改动归档模块之外的字段风险扫描任务逻辑。

## 归档模式

归档能力分为三类：

- `ARCHIVE`：普通归档。执行 `pt-archiver` 时同时带 `--source` 和 `--dest`。
- `PURGE`：纯删除。执行 `pt-archiver` 时带 `--source` 和 `--purge`，不带 `--dest`。
- `BATCH_ARCHIVE`：批次归档。先执行批次查询，将本批 key 写入指定的辅助表，然后多个步骤共用这批 key 执行 `ARCHIVE` 或 `PURGE`。

`ARCHIVE` 和 `PURGE` 是步骤模式；`BATCH_ARCHIVE` 是任务执行模式，因为同一个批次会被多个步骤共享。

## 数据模型变更

### `archive_task`

新增字段：

- `task_mode varchar(30) not null default 'NORMAL'`

取值：

- `NORMAL`：现有归档任务行为。
- `BATCH_ARCHIVE`：重复执行批次查询，直到没有新数据。

### `archive_task_step`

新增字段：

- `step_mode varchar(30) not null default 'ARCHIVE'`
- `index_name varchar(100) null`

规则：

- `ARCHIVE` 要求配置 `dest_database` 和 `dest_table`。
- `PURGE` 不要求配置 `dest_database` 和 `dest_table`。
- `index_name` 渲染进 source DSN，格式为 `i=<index_name>`。
- 继续复用现有字段：`where_template`、`charset`、`limit_size`、`progress_size`、`bulk_insert`、`commit_each`、`extra_options`。

### `archive_task_variable`

新增字段：

- `connection_id bigint null`

规则：

- 如果配置了 `connection_id`，变量查询使用该连接。
- 如果为空，保持现有行为，使用任务源连接查询变量。

### `archive_batch_config`

新增表：

- `id bigint primary key auto_increment`
- `task_id bigint not null`
- `query_connection_id bigint not null`
- `target_connection_id bigint not null`
- `batch_query text not null`
- `target_database varchar(100) not null`
- `target_table varchar(100) not null`
- `truncate_sql text not null`
- `load_sql text not null`
- `batch_size int not null default 2000`
- `max_rounds int null`
- `enabled bit not null default b'1'`

用途：

- `query_connection_id` 只用于执行 `batch_query`，可以指向 TiDB 或其他只读查询库。
- `target_connection_id` 只用于执行 `truncate_sql` 和 `load_sql`，通常指向承载辅助表的 MySQL 源库。
- `batch_query` 查询一批 key，例如 `order_id,pay_id`。
- 执行器把查询结果写入辅助表，例如 `tmp_arch_order_id`。
- 后续步骤可以在 `where_template` 里引用这个辅助表。

第一版优先支持当前脚本里的模式：辅助表是 MySQL 源库里的普通表，每一轮批次执行前先清空，再写入本批 key。批次查询可以单独配置到 TiDB，但辅助表写入必须落到 MySQL 连接。

## 命令生成规则

`ArchiveCommandBuilder` 需要扩展：

- `ARCHIVE`：保持现有 source/dest DSN 行为。
- `PURGE`：省略 `--dest`，追加 `--purge`，不追加 `--no-delete`。
- `index_name`：追加到 source DSN，形如 `,i=<index_name>`。
- `bulk_insert`：保留现有 `--bulk-insert` 和 DSN `L=1` 行为。
- 日志里的命令继续脱敏密码。

## 执行流程

### 普通任务

1. 加载启用的变量。
2. 将变量渲染到每个启用步骤的 `where_template`。
3. 每个步骤生成并执行一条 `pt-archiver` 命令。
4. 遇到第一个非 0 退出码即停止任务。
5. 持久化日志、已处理步骤数、跳过步骤数和退出码。

### 批次归档任务

1. 批次开始前先执行启用的单值变量查询。
2. 开始第 `1` 轮批次。
3. 执行 `batch_query`。
4. 如果没有返回行，任务结束。
5. 清空配置的辅助表。
6. 将本批结果写入辅助表。
7. 按 `sort_order` 顺序执行所有启用步骤。
8. 如果任一步骤失败，停止任务并保留失败批次日志。
9. 继续下一轮，直到没有数据或达到 `max_rounds`。

批次日志必须包含：批次轮次、本批行数、辅助表名、每个步骤名。

## SQL Seed 策略

历史脚本转换后的 SQL 放在：

`mysql/archive-seed/`

建议目录结构：

- `00_connections_required.sql`：列出需要预先创建的连接名称和变量。
- `haoshiqi_arch_cart.sql`
- `haoshiqi_table_arch.sql`
- `bzbc_table_arch.sql`
- 每个源 bash 脚本或一组强相关脚本对应一个 SQL 文件。

Seed SQL 要求：

- 通过连接名称从 `db_connection` 查询 ID。
- 使用确定性的任务名，例如 `haoshiqi/arch_cart`。
- 使用确定性的步骤名，例如 `cart purge by user_id`。
- 在 `remark` 中记录原始脚本路径。
- 不写入数据库密码。
- 尽量采用幂等策略，例如按任务名删除后重建，或通过任务名判断是否已存在。

## 脚本映射规则

### 固定 `pt-archiver` 命令

每条命令转换成一个步骤。

以 `arch_cart.sh` 为例：

- `cart` + `--purge` 转为 `PURGE` 步骤。
- `cart_sku` + `--purge` 转为 `PURGE` 步骤。
- `cart_sku` + `--dest legacy_hsq_online` 转为 `ARCHIVE` 步骤。

### 表循环脚本

一个脚本转换成一个任务。循环里的每张表转换成一个步骤。`case` 分支提供该步骤的 `where_template`、`index_name` 和额外参数。

### 单值变量脚本

shell 中通过 `mysql -N -e "select ..."` 得到的变量，转换成 `archive_task_variable`。

示例：

- `maxid=$(mysql ... "select max(id) ...")`
- 步骤条件写成 `id < ${maxid}`

### 复杂批次脚本

类似 `pt_arch_hsq_order.sh` 的脚本转换成 `BATCH_ARCHIVE` 任务。

批次查询保留原始 ID 选择语义：

- `select id,pay_id from trade_order where created_at < unix_timestamp('2023-01-01 00:00:00') order by id limit 2000`

辅助表配置为：

- 源库：`hsq_online`
- 辅助表：`tmp_arch_order_id`
- 查询连接：可选 TiDB 连接。
- 写入连接：MySQL 源库连接。

每个 `order_archiver` 调用转换成一个步骤，并在 `where_template` 中引用 `tmp_arch_order_id`。

## 前端变更

归档任务表单需要增加：

- 任务模式：普通任务 / 批次归档任务。
- 步骤模式：归档 / 纯删除。
- 源表索引名。
- 变量查询连接。
- 当任务模式为 `BATCH_ARCHIVE` 时展示批次配置区域，包括批次查询连接和辅助表写入连接。

当步骤模式为 `PURGE` 时，前端隐藏目标库和目标表输入项。

## 测试策略

后端测试：

- `ArchiveCommandBuilder` 可以生成不带 `--dest` 的 `PURGE` 命令。
- `ArchiveCommandBuilder` 可以在 source DSN 中包含 `i=<index_name>`。
- `ArchiveCommandBuilder` 保持现有 `ARCHIVE` 行为不变。
- `ArchiveTaskService` 可以保存并返回步骤模式、索引名、变量连接和批次配置。
- `ArchiveTaskExecutor` 可以按批次执行，直到批次查询返回空结果。

前端测试或构建检查：

- 归档任务表单可以提交 `PURGE` 步骤。
- 归档任务表单可以提交源表索引名。
- 归档任务表单可以展示并提交批次配置。

Seed SQL 检查：

- SQL 文件通过连接名称引用已有连接，不包含密码。
- 每个历史脚本都映射到结构化任务。
- `arch_cart.sh` 被一个任务的三个步骤完整覆盖。

## 推进计划

1. 扩展数据库 schema 和 Java 实体。
2. 扩展命令生成，支持 `PURGE` 和源表索引。
3. 扩展普通任务执行器。
4. 增加批次归档模型和执行流程。
5. 扩展 API DTO 和前端表单。
6. 先为代表性脚本补 SQL seed，从 `arch_cart.sh` 开始。
7. 按目录分批把剩余脚本转换成 SQL seed。
8. 运行后端测试和前端构建。

## 已确认决策

- 所有脚本都用结构化任务表达。
- 不建设运行时 bash 导入器。
- 不保留复杂脚本托管执行兜底。
- SQL seed 是历史脚本迁移产物。
