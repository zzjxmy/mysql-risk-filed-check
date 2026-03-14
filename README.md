# MySQL 字段容量风险检查平台

一个用于检查 MySQL 数据库字段容量风险的全栈 Web 应用，帮助 DBA 和开发团队及时发现潜在的字段溢出问题。

## 功能特性

- **数据库连接管理** - 支持多数据库连接配置，密码 AES 加密存储
- **检查任务管理** - 创建、编辑、删除检查任务，支持数据库和表名模式匹配
- **定时调度执行** - 基于 Quartz 的定时任务调度，支持 Cron 表达式
- **实时日志监控** - WebSocket 实时推送执行日志，支持任务执行中监控
- **风险结果展示** - 仪表盘可视化、风险列表、详情查看，支持按执行记录筛选
- **白名单管理** - 支持表级和字段级白名单，减少误报
- **告警通知** - 支持钉钉和邮件告警，可配置告警阈值
- **用户权限管理** - 基于 RBAC 的用户管理，支持 ADMIN/USER 角色

## 技术栈

### 后端
- Java 8
- Spring Boot 2.7.18
- Spring Security + JWT
- Spring Data JPA
- Quartz Scheduler
- WebSocket (STOMP)
- MySQL 8.0
- Maven

### 前端
- Vue 3 + TypeScript
- Vite
- Element Plus
- Pinia
- Axios
- ECharts
- SockJS + STOMP

## 快速开始

### 环境要求
- JDK 8+
- Node.js 16+
- MySQL 8.0
- Maven 3.6+

### 1. 克隆项目
```bash
git clone <repository-url>
cd mysql-field-capacity-check
```

### 2. 初始化数据库
```bash
mysql -u root -p < backend/sql/01_init_schema.sql
```

### 3. 启动后端
```bash
cd backend
mvn clean package -DskipTests
java -jar target/mysql-field-capacity-check-1.0.0.jar
```

后端服务默认运行在 http://localhost:8080

### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 http://localhost:5173

### 5. 访问应用
打开浏览器访问 http://localhost:5173

默认管理员账号：`admin` / `admin123`

## 项目结构

```
mysql-field-capacity-check/
├── backend/                    # 后端项目
│   ├── src/main/java/
│   │   └── com/fieldcheck/
│   │       ├── config/         # 配置类
│   │       ├── controller/     # REST API 控制器
│   │       ├── dto/            # 数据传输对象
│   │       ├── entity/         # JPA 实体
│   │       ├── engine/         # 检查引擎
│   │       ├── repository/     # 数据访问层
│   │       ├── scheduler/      # 定时任务
│   │       ├── security/       # 安全相关
│   │       └── service/        # 业务逻辑层
│   ├── src/main/resources/
│   │   └── sql/                # SQL 脚本
│   └── pom.xml
├── frontend/                   # 前端项目
│   ├── src/
│   │   ├── api/                # API 接口
│   │   ├── components/         # 公共组件
│   │   ├── router/             # 路由配置
│   │   ├── stores/             # Pinia 状态管理
│   │   ├── views/              # 页面视图
│   │   └── utils/              # 工具函数
│   ├── package.json
│   └── vite.config.ts
└── docker-compose.yml          # Docker 部署配置
```

## 核心功能说明

### 检查任务配置
- **数据库模式**：支持通配符，如 `db_*` 匹配所有 db_ 开头的数据库
- **表模式**：支持通配符，如 `t_user*` 匹配所有 t_user 开头的表
- **阈值设置**：可配置风险阈值百分比，默认 80%
- **定时调度**：支持 Cron 表达式，如 `0 0 2 * * ?` 每天凌晨 2 点执行

### 风险检测规则
当前支持以下字段类型的容量检查：
- `TINYINT` / `TINYINT UNSIGNED`
- `SMALLINT` / `SMALLINT UNSIGNED`
- `MEDIUMINT` / `MEDIUMINT UNSIGNED`
- `INT` / `INT UNSIGNED`
- `BIGINT` / `BIGINT UNSIGNED`
- `DECIMAL(M,D)` - 检查整数部分使用比例
- `VARCHAR(N)` / `CHAR(N)` - 检查字符长度使用比例
- `TEXT` / `TINYTEXT` / `MEDIUMTEXT` / `LONGTEXT` - 检查文本长度

### 告警配置
- **钉钉告警**：支持 Webhook 地址和密钥配置
- **邮件告警**：支持 SMTP 配置，可配置收件人列表
- **告警阈值**：可配置触发告警的风险等级和数量阈值

## 部署

### Docker 部署
```bash
docker-compose up -d
```

### 生产环境配置
1. 修改 `application.yml` 中的数据库连接配置
2. 配置 JWT 密钥和 AES 加密密钥
3. 配置邮件服务器
4. 使用 Nginx 反向代理前端静态资源

## 开发计划

- [x] 基础框架搭建
- [x] 数据库连接管理
- [x] 检查任务管理
- [x] 定时调度执行
- [x] 实时日志监控
- [x] 风险结果展示
- [x] 白名单管理
- [x] 告警配置
- [x] 用户权限管理
- [ ] 更多字段类型支持（ENUM、SET、JSON 等）
- [ ] 历史趋势分析
- [ ] 风险修复建议
- [ ] 多语言支持

## 许可证

MIT License
