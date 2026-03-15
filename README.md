# MySQL 字段容量风险检查平台

一个用于检查 MySQL 数据库字段容量风险的全栈 Web 应用，帮助 DBA 和开发团队及时发现潜在的字段溢出问题。

## 功能特性

### 核心功能
- **数据库连接管理** - 支持多数据库连接配置，密码 AES 加密存储，连接测试
- **检查任务管理** - 创建、编辑、删除检查任务，支持数据库和表名模式匹配
- **定时调度执行** - 基于 Quartz 的定时任务调度，支持 Cron 表达式
- **实时日志监控** - WebSocket 实时推送执行日志，支持任务执行中监控
- **风险结果展示** - 仪表盘可视化、风险列表、详情查看，支持按执行记录筛选
- **风险结果导出** - 支持导出 Excel (xlsx) 格式，可按条件筛选后导出
- **执行记录管理** - 独立执行记录菜单，支持查看日志、下载日志文件
- **白名单管理** - 支持表级和字段级白名单，减少误报
- **告警通知** - 支持钉钉和邮件告警，任务可关联多个告警配置
- **用户权限管理** - 基于 RBAC 的用户管理，支持 ADMIN/USER 角色
- **审计日志** - 记录所有操作行为，支持按用户、操作类型、时间筛选

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
- Quartz Scheduler
- WebSocket (STOMP)
- Apache POI (Excel 导出)
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
编辑 `backend/src/main/resources/application.yml`：n```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fieldcheck?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password
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
- 任务关联告警配置

### 系统管理
- 用户管理：添加、编辑、删除用户，重置密码
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

### 日志配置
```yaml
app:
  log-path: ./logs/executions  # 执行日志存储路径
```

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
| 风险 | GET /api/risks | 获取风险列表 |
| 风险 | GET /api/risks/export | 导出 Excel |
| 风险 | PUT /api/risks/{id}/status | 更新状态 |
| 告警 | GET /api/alerts | 获取告警配置 |
| 告警 | POST /api/alerts/{id}/test | 测试告警 |

## 部署

### Docker 部署
```bash
# 构建镜像
docker build -t fieldcheck-backend ./backend
docker build -t fieldcheck-frontend ./frontend

# 使用 docker-compose
docker-compose up -d
```

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
- [ ] 更多字段类型支持（ENUM、SET、JSON 等）
- [ ] 历史趋势分析
- [ ] 风险修复建议
- [ ] 多语言支持

## 许可证

MIT License
