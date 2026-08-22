# RagLaw

基于 AgentScope Java + CopilotKit/AG-UI 的法律咨询多 Agent 平台。

## 结构

```
RagLaw/
├── backend/          # Spring Boot 模块化单体
├── raglaw-web/       # React 单应用（律师 + 管理）
├── packages/ui/      # 共享 UI 组件
├── docker/           # 本地中间件
└── docs/sql/         # 数据库脚本
```

## 快速开始

> **Docker Desktop** 需先启动，再执行 `docker compose up -d`。

### 1. 中间件

```bash
cd docker
docker compose up -d
# 可选可观测性
docker compose --profile observability up -d
```

### 2. 后端

```bash
cd backend
# 项目已含 .mvn/settings.xml 使用 Maven Central（覆盖失效私服镜像）
mvn -q test
cp ../.env.example ../.env   # 填入 DASHSCOPE_API_KEY
mvn -pl raglaw-server spring-boot:run
```

首次启动会在日志中打印 `admin@raglaw.local` 的随机密码。

### 3. 前端

```bash
pnpm install
pnpm dev:web
```

访问 http://localhost:5173

## 技术栈

- Java 17, Spring Boot 3, AgentScope Java 2, DashScope
- React 19, Vite, CopilotKit, React Router
- MySQL 8, pgvector, MinIO, RabbitMQ, Redis, Langfuse (optional)

## 旧代码

历史实现保留在 `RagLaw/` 子目录，新架构在仓库根目录构建。
