# RagLaw

基于 AgentScope Java + AG-UI 的法律咨询多 Agent 平台。

## 结构

```
RagLaw/
├── backend/          # Spring Boot 模块化单体
├── raglaw-web/       # React 单应用（律师 + 管理）
├── packages/ui/      # 共享 UI 组件
├── docker/           # 本地中间件
├── scripts/          # 启动、评测与 E2E 脚本
└── docs/sql/         # 数据库脚本
```

## 前置条件

- Docker Desktop（MySQL、Elasticsearch、MinIO、RabbitMQ）
- Java 17、Maven 3.9+
- Node.js 20+、pnpm 9+

## 本地启动

> 先启动 Docker Desktop，再执行下列步骤。

### 1. 中间件

```bash
cd docker
docker compose up -d
```

MySQL 表结构由后端 **Flyway** 在启动时自动迁移。若曾用旧版 SQL init 建库，需 `docker volume rm raglaw_raglaw-mysql-data` 后重建。

### 2. 环境配置

```bash
cp .env.example .env
```

在 `.env` 中按需填写 `DASHSCOPE_API_KEY`（真实 LLM / 向量）。**无 API Key 时**请设置 `RAGLAW_LLM_MOCK=true`。

默认已启用混合检索（Elasticsearch + Embedding）、MinIO、RabbitMQ，详见 [`.env.example`](.env.example)。

### 3. 后端

```bash
cd backend
mvn -pl raglaw-server spring-boot:run
```

Spring Boot 不会自动读取根目录 `.env`。若需加载 `.env` 中的配置（ES、Embedding 等），可用：

```bash
pnpm dev:backend
# 或：pwsh scripts/dev-backend.ps1
```

首次编译：`mvn -pl raglaw-server -am install -DskipTests`

### 4. 前端

```bash
pnpm install
pnpm dev:web
```

访问 http://localhost:5173 。开发模式下登录页会**自动填写**默认管理员账密。

## 默认账号

| 项 | 值 |
|----|-----|
| 邮箱 | `admin@raglaw.local` |
| 密码 | `admin12345` |

首次启动由种子创建。若曾用其他密码建库导致无法登录，删除 MySQL volume 重建，或在用户管理中重置密码。

## 功能概览

| 模块 | 路由/入口 |
|------|-----------|
| 智能对话 + A2A | `/` |
| 专家直达 | `/chat/:agentCode` |
| 法规/案例检索 | `/knowledge/statutes` |
| 合同审查 | `/contracts` → `/contracts/review` |
| 可观测 | `/admin/observability` |
| Agent 配置 | `/admin/agents` |
| 用户管理 | `/admin/users` |
| 文档管理 | `/admin/documents` |

## 技术栈

Java 17 · Spring Boot 3 · AgentScope Java · DashScope · React 19 · Vite · MySQL 8 · Elasticsearch 8 · MinIO · RabbitMQ

