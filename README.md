# MySQL 数据治理平台

一个面向 MySQL 的数据治理全栈 Web 应用，覆盖字段容量风险检查和数据归档两类场景，帮助 DBA 和开发团队及时发现字段溢出风险，并通过可配置任务完成历史数据归档。

## 功能特性

### 核心功能
- **数据库连接管理** - 支持多数据库连接配置，密码 AES 加密存储，连接测试
- **检查任务管理** - 创建、编辑、删除检查任务，支持数据库和表名模式匹配
- **数据归档任务** - 基于 `pt-archiver` 执行跨库/跨表归档，支持多步骤、变量、复制保留源数据或移动删除源数据
- **归档执行记录** - 独立记录归档执行状态、步骤进度、退出码、日志路径，支持日志查看和下载
- **定时调度执行** - 基于 Quartz 的定时任务调度，支持 Cron 表达式
- **实时日志监控** - WebSocket 实时推送执行日志，支持任务执行中监控
- **风险结果展示** - 仪表盘可视化、风险列表、详情查看，支持按执行记录筛选
- **风险结果导出** - 支持导出 Excel (xlsx) 格式，可按条件筛选后导出
- **执行记录管理** - 独立执行记录菜单，支持查看日志、下载日志文件
- **白名单管理** - 支持表级和字段级白名单，减少误报
- **告警通知** - 支持钉钉和邮件告警，任务可关联多个告警配置
- **用户权限管理** - 基于 RBAC 的用户管理，支持 ADMIN/USER 角色
- **审计日志** - 记录所有操作行为，支持按用户、操作类型、时间筛选
- **LDAP 认证** - 支持 LDAP/Active Directory 登录，页面化配置

### 风险检测类型
- **整型溢出** - TINYINT/SMALLINT/MEDIUMINT/INT/BIGINT 及其 UNSIGNED
- **小数溢出** - DECIMAL(M,D) 整数部分使用比例检查
- **Y2038 问题** - TIMESTAMP 字段 2038 年溢出风险预警
- **字符串截断** - VARCHAR/CHAR/TEXT 类型长度使用比例检查

## 技术栈

### 后端
- Java 8
- Spring Boot 2.7.18
- Spring Security + JWT
- Spring Data JPA
- Spring LDAP
- Quartz Scheduler
- WebSocket (STOMP)
- Apache POI (Excel 导出)
- Percona Toolkit `pt-archiver` (数据归档)
- MySQL 5.7+ / 8.0+
- Maven

### 前端
- Vue 3 + TypeScript
- Vite
- Element Plus
- Pinia
- Axios
- ECharts
- SockJS + STOMP

## 环境要求

- JDK 8+
- Node.js 16+
- MySQL 5.7+ 或 MySQL 8.0+
- Maven 3.6+
- Percona Toolkit 3.x（需要提供 `pt-archiver` 可执行文件）

## 快速开始

### 1. 克隆项目
```bash
git clone <repository-url>
cd mysql-risk-filed-check
```

### 2. 创建数据库
```bash
mysql -u root -p -e "CREATE DATABASE fieldcheck DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 3. 配置后端
编辑 `backend/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fieldcheck?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password

archive:
  pt-archiver-path: /opt/homebrew/bin/pt-archiver  # 按实际安装路径调整
```

### 4. 启动后端
```bash
cd backend
mvn clean package -DskipTests
java -jar target/mysql-field-capacity-check-1.0.0.jar
```

后端服务默认运行在 http://localhost:8080

### 5. 启动前端
```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 http://localhost:5173

### 6. 访问应用
打开浏览器访问 http://localhost:5173

默认管理员账号：`admin` / `admin123`

## 项目结构

```
mysql-risk-filed-check/
├── backend/                       # 后端项目
│   ├── src/main/java/
│   │   └── com/fieldcheck/
│   │       ├── aspect/             # AOP 切面（审计日志）
│   │       ├── archive/            # 数据归档执行引擎
│   │       ├── config/             # 配置类
│   │       ├── controller/         # REST API 控制器
│   │       ├── dto/                # 数据传输对象
│   │       ├── entity/             # JPA 实体
│   │       ├── engine/             # 检查引擎
│   │       ├── repository/         # 数据访问层
│   │       ├── scheduler/          # 定时任务
│   │       ├── security/           # 安全相关
│   │       └── service/            # 业务逻辑层
│   ├── src/main/resources/
│   │   └── application.yml         # 配置文件
│   └── pom.xml
├── frontend/                      # 前端项目
│   ├── src/
│   │   ├── api/                   # API 接口
│   │   ├── components/            # 公共组件
│   │   ├── router/                # 路由配置
│   │   ├── stores/                # Pinia 状态管理
│   │   ├── views/                 # 页面视图
│   │   │   ├── alert/             # 告警配置
│   │   │   ├── archive/           # 数据归档任务和执行记录
│   │   │   ├── connection/        # 数据库连接
│   │   │   ├── execution/         # 执行记录
│   │   │   ├── risk/              # 风险结果
│   │   │   ├── system/            # 系统管理
│   │   │   ├── task/              # 任务管理
│   │   │   └── whitelist/         # 白名单
│   │   └── utils/                 # 工具函数
│   ├── package.json
│   └── vite.config.ts
└── README.md
```

## 功能模块说明

### 仪表盘
- 风险统计概览（待处理、已忽略、已解决）
- 风险类型分布图表
- 最近 7 天风险趋势
- 快速导航入口

### 数据库连接管理
- 添加、编辑、删除数据库连接
- 测试连接可用性
- 密码 AES 加密存储

### 检查任务管理
- 创建检查任务，配置数据库连接、扫描范围
- 配置白名单类型（全局/自定义/无）
- 关联告警配置
- 立即执行或定时调度
- 实时监控执行进度

### 数据归档任务
- 创建归档任务，配置源连接、目标连接、Cron 表达式和告警配置
- 每个任务支持多个归档步骤，步骤可以指定源库表、目标库表、WHERE 条件模板、批次大小和进度输出间隔
- 支持变量查询，执行时先计算变量，再替换步骤中的条件模板
- 支持两种归档模式：
  - `deleteSource=false`：只复制到目标表，不删除源表数据
  - `deleteSource=true`：复制到目标表后删除符合条件的源表数据
- 归档底层调用 `pt-archiver`，目标表需要提前创建，系统不会自动创建目标库或同源表
- 如果目标表不存在、连接失败或 `pt-archiver` 返回非 0 退出码，归档执行会标记为失败，并触发任务关联的告警配置

### 归档执行记录
- 查看所有归档执行记录
- 按任务名称、状态、触发方式筛选
- 查看执行日志、下载日志文件
- 实时监控执行中的步骤日志

### 执行记录
- 查看所有执行记录
- 按任务名称、状态、触发方式筛选
- 查看执行日志
- 下载日志文件
- 跳转到风险结果

### 风险结果
- 按执行记录、数据库、风险类型、状态筛选
- 查看风险详情
- 更新风险状态（待处理/已忽略/已解决）
- 导出 Excel 报告

### 白名单管理
- 添加、编辑、删除白名单规则
- 支持数据库级、表级、字段级规则
- 支持通配符 `*` 和 `?`

### 告警配置
- 配置钉钉机器人告警
- 配置邮件告警
- 测试告警发送
- 字段风险检查任务和数据归档任务都可以关联告警配置

### 系统管理
- 用户管理：添加、编辑、删除用户，重置密码
- LDAP 配置：页面化配置 LDAP/AD 认证，支持连接测试
- 审计日志：查看所有操作记录

## 配置说明

### 数据库配置 (MySQL 5.7+)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fieldcheck?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### JWT 配置
```yaml
app:
  jwt:
    secret: your-jwt-secret-key-at-least-256-bits
    expiration: 86400000  # 24 hours
```

### LDAP 配置
在系统管理页面配置 LDAP/AD 认证：
- 启用/禁用 LDAP 认证
- 服务器地址（支持 ldap:// 和 ldaps://）
- Base DN、用户搜索基础、搜索过滤器
- 管理员账号（用于同步用户信息）
- 邮件和显示名称属性映射
- 默认角色分配

### 日志配置
```yaml
app:
  log-path: ./logs/executions  # 执行日志存储路径
```

### 数据归档配置
```yaml
archive:
  pt-archiver-path: /usr/bin/pt-archiver
```

本地开发时请按实际路径配置，例如 macOS Homebrew 常见路径为 `/opt/homebrew/bin/pt-archiver`。Docker 部署时可通过环境变量 `PT_ARCHIVER_PATH` 覆盖。

### 线程池配置
```yaml
app:
  max-concurrent-tasks: 5  # 最大并发任务数
```

## API 接口

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | POST /api/auth/login | 用户登录 |
| 认证 | POST /api/auth/logout | 用户登出 |
| 连接 | GET /api/connections | 获取连接列表 |
| 连接 | POST /api/connections | 创建连接 |
| 连接 | POST /api/connections/{id}/test | 测试连接 |
| 任务 | GET /api/tasks | 获取任务列表 |
| 任务 | POST /api/tasks | 创建任务 |
| 任务 | POST /api/tasks/{id}/run | 执行任务 |
| 执行 | GET /api/executions | 获取执行记录 |
| 执行 | GET /api/executions/{id}/log | 获取日志内容 |
| 执行 | GET /api/executions/{id}/log/download | 下载日志 |
| 归档任务 | GET /api/archive-tasks | 获取归档任务列表 |
| 归档任务 | POST /api/archive-tasks | 创建归档任务 |
| 归档任务 | PUT /api/archive-tasks/{id} | 更新归档任务 |
| 归档任务 | DELETE /api/archive-tasks/{id} | 删除归档任务 |
| 归档任务 | POST /api/archive-tasks/{id}/run | 执行归档任务 |
| 归档任务 | POST /api/archive-tasks/{id}/stop | 停止归档任务 |
| 归档执行 | GET /api/archive-executions | 获取归档执行记录 |
| 归档执行 | GET /api/archive-executions/{id} | 获取归档执行详情 |
| 归档执行 | GET /api/archive-executions/{id}/log | 获取归档日志内容 |
| 归档执行 | GET /api/archive-executions/{id}/log/download | 下载归档日志 |
| 风险 | GET /api/risks | 获取风险列表 |
| 风险 | GET /api/risks/export | 导出 Excel |
| 风险 | PUT /api/risks/{id}/status | 更新状态 |
| 告警 | GET /api/alerts | 获取告警配置 |
| 告警 | POST /api/alerts/{id}/test | 测试告警 |
| LDAP | GET /api/ldap-config | 获取 LDAP 状态 |
| LDAP | GET /api/ldap-config/detail | 获取 LDAP 配置（ADMIN） |
| LDAP | POST /api/ldap-config | 保存 LDAP 配置（ADMIN） |
| LDAP | POST /api/ldap-config/test | 测试 LDAP 连接（ADMIN） |

## 部署

### Docker 部署
```bash
# 构建镜像
docker build -t fieldcheck-backend ./backend
docker build -t fieldcheck-frontend ./frontend

# 使用 docker-compose
docker-compose up -d
```

Docker 部署时后端容器需要能访问 `pt-archiver`。如果镜像中安装路径不是 `/usr/bin/pt-archiver`，请设置环境变量：

```bash
PT_ARCHIVER_PATH=/path/to/pt-archiver docker-compose up -d
```

## 已验证行为

### 大数据归档
- 使用真实 `pt-archiver 3.7.1` 通过后端 API 执行过 20 万行数据归档验证
- 测试任务分为两步：
  - 前 10 万行只复制，源表保留
  - 后 10 万行移动归档，复制后删除源表数据
- 执行结果为 `SUCCESS`，目标表最终 20 万行完整，源表仅保留前 10 万行
- 第二步日志显示 `SELECT 100000 / INSERT 100000 / DELETE 100000`

### 源数据删除
- `deleteSource=true` 会删除符合 WHERE 条件的源表数据
- 当前功能不会 `DROP TABLE`，也不会删除整张源表结构
- 如果 WHERE 条件覆盖整张表，则效果是清空符合条件的数据行，而不是删除表

### 目标表创建
- 归档不会自动创建目标库或目标表
- 目标表不存在时，`pt-archiver` 会失败，归档执行状态会变为 `FAILED`
- 建议先用 `CREATE TABLE target LIKE source` 或正式建表脚本准备目标表结构

### 归档失败告警
- 归档失败后会调用任务关联的告警配置
- 本地验证中，缺失目标表导致归档失败，后端实际发出了 DINGTALK 类型的 HTTP POST 告警请求
- 真实外部钉钉 webhook 发送需要在部署环境中确认网络、机器人安全设置和密钥配置

### 生产环境配置
1. 修改数据库连接配置
2. 配置 JWT 密钥（建议 256 位以上）
3. 配置 AES 加密密钥
4. 配置邮件服务器（可选）
5. 使用 Nginx 反向代理

### Nginx 配置示例
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## 开发计划

- [x] 基础框架搭建
- [x] 数据库连接管理
- [x] 检查任务管理
- [x] 定时调度执行
- [x] 实时日志监控
- [x] 风险结果展示
- [x] 风险结果导出
- [x] 执行记录管理
- [x] 白名单管理
- [x] 告警配置
- [x] 用户权限管理
- [x] 审计日志
- [x] LDAP 认证
- [x] 数据归档任务
- [x] 归档执行记录
- [x] 归档失败告警
- [ ] 更多字段类型支持（ENUM、SET、JSON 等）
- [ ] 历史趋势分析
- [ ] 风险修复建议
- [ ] 多语言支持

## 许可证

MIT License
