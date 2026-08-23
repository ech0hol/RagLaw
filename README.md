# RagLaw

基于 AgentScope Java + AG-UI 的法律咨询多 Agent 平台。

## 结构

```
RagLaw/
├── backend/          # Spring Boot 模块化单体
├── raglaw-web/       # React 单应用（律师 + 管理）
├── packages/ui/      # 共享 UI 组件
├── docker/           # 本地中间件
├── scripts/          # 评测与 E2E 种子脚本
└── docs/sql/         # 数据库脚本
```

## 快速开始

> **Docker Desktop** 需先启动，再执行 `docker compose up -d`。

### 1. 中间件

```bash
cd docker
docker compose up -d
```

> **Schema 迁移**：MySQL 表结构由后端 **Flyway** 在启动时自动迁移（`backend/raglaw-server/src/main/resources/db/migration/`）。若你之前用旧版 docker SQL init 建过库，需 `docker volume rm raglaw_raglaw-mysql-data` 后重建，或手动 baseline。

```bash
# 可选可观测性
docker compose --profile observability up -d
```

### 2. 后端

```bash
cd backend
mvn -q test
cp ../.env.example ../.env   # 填入 DASHSCOPE_API_KEY（可选，开发可用 mock）
```

> **运行测试**：集成测试通过 `@RagLawIntegrationTest` 强制使用 `admin-test-password`，无需（也不应依赖）shell 中的 `RAGLAW_SEED_ADMIN_PASSWORD`。`RagPipelineEvaluationIT` 覆盖上传→入库→分块索引链路。

启动（推荐设置固定管理员密码）：

```bash
# PowerShell
$env:RAGLAW_SEED_ADMIN_PASSWORD="raglaw-eval"
$env:RAGLAW_LLM_MOCK="true"
mvn -pl raglaw-server spring-boot:run
```

首次启动会创建 `admin@raglaw.local`。若设置了 `RAGLAW_SEED_ADMIN_PASSWORD`，使用该密码登录；否则查看启动日志中的随机密码。

### 3. 前端

```bash
pnpm install
pnpm dev:web
```

访问 http://localhost:5173

默认管理员：`admin@raglaw.local` / `raglaw-eval`（或你设置的 `RAGLAW_SEED_ADMIN_PASSWORD`）

## 启用 pgvector 混合检索（可选）

1. 在 `.env` 中设置：
   ```env
   POSTGRES_ENABLED=true
   EMBEDDING_ENABLED=true
   DASHSCOPE_API_KEY=sk-...
   RAGLAW_LLM_MOCK=false
   ```
2. 确保 `docker compose` 中 postgres 已启动
3. **重新 ingest** 已有文档（embedding 在入库时写入）
4. 检查健康接口：`GET /api/v1/health` → `rag.hybridRetrievalReady: true`
5. **真实向量对比评测**（与 mock 区分）：
   ```powershell
   $env:RAGLAW_ADMIN_PASSWORD="raglaw-eval"
   $env:RAGLAW_LLM_MOCK="false"
   .\scripts\eval-rag-quality.ps1 -RetrievalMode both -UseRealEmbedding
   ```
   报告写入 `docs/evaluation/rag-pipeline-eval-*-compare.json`，其中 `embeddingMock: false` 表示使用 DashScope 向量。

## RAG 质量评测

导入 fixture 并评测全文检索 / AG-UI 引用：

```powershell
$env:RAGLAW_ADMIN_PASSWORD="raglaw-eval"
.\scripts\eval-rag-quality.ps1
# 混合检索对比（需先启用 pgvector + 重 ingest）
.\scripts\eval-rag-quality.ps1 -RetrievalMode hybrid
```

报告输出到 `docs/evaluation/rag-pipeline-eval-*.json`。黄金查询基准见 `docs/evaluation/recall-benchmark.json`。

API 文档见 [`docs/API.md`](docs/API.md)，备份说明见 [`docs/backup.md`](docs/backup.md)。

## E2E 测试

```bash
# 终端 1：后端（见上）
# 终端 2：种子语料（可选，用于引用卡片断言）
bash scripts/seed-e2e-corpus.sh

# 终端 3
pnpm install
cd raglaw-web && pnpm exec playwright install chromium
E2E_WITH_CORPUS=1 E2E_ADMIN_PASSWORD=raglaw-eval pnpm e2e
```

## 功能概览

| 模块 | 路由/入口 | 状态 |
|------|-----------|------|
| 智能对话 + A2A | `/` | GENERAL 自动委派法规/案例/合同专家 |
| 专家直达 | `/chat/:agentCode` | 如 `/chat/STATUTE_CIVIL` |
| 法规/案例检索 | `/knowledge/statutes` | 全文检索 API + UI |
| 合同审查 MVP | `/contracts` → `/contracts/review` | 上传、风险规则分析、专项对话 |
| 可观测 L1 | `/admin/observability` | trace 列表 + 阶段/片段详情 |
| 可观测 L2 | Langfuse（可选） | 设置 `LANGFUSE_ENABLED=true` + keys，见 `.env.example` |
| Agent 配置 | `/admin/agents` | 编辑 knowledgeScopes / a2aPeers + reload |
| 用户管理 | `/admin/users` | 创建律师/管理员账号 |

生产部署见 [`docs/deployment.md`](docs/deployment.md)。

## 技术栈

- Java 17, Spring Boot 3, AgentScope Java 2, DashScope
- React 19, Vite, React Router, AG-UI SSE（**未集成 CopilotKit**，以原生 SSE 替代，见下方说明）
- MySQL 8, pgvector, MinIO, RabbitMQ, Redis, Langfuse (optional)

## CopilotKit 说明（Phase 1.5 / B2）

当前对话链路基于 **原生 AG-UI SSE**（`POST /api/v1/agui/run`），未引入 CopilotKit Provider。原因：现有 SSE 已覆盖流式对话、引用、A2A 状态与重新生成；CopilotKit 适配成本较高。后续若需要 clientTool（如合同修订采纳 UI 深化）再评估接入。

## Langfuse 可观测（Phase 5.4）

```bash
cd docker
docker compose --profile observability up -d
```

在 `.env` 中配置 `LANGFUSE_ENABLED=true`、`LANGFUSE_PUBLIC_KEY`、`LANGFUSE_SECRET_KEY`，管理端 trace 详情可跳转 Langfuse UI。L2 双写包含 A2A span、RAG tool 命中、LLM generation（含 token 用量）。

## 旧代码

历史实现保留在 `RagLaw/` 子目录，新架构在仓库根目录构建。
