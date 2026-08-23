# RagLaw 生产部署指南

本文档描述将 RagLaw 从本地 Docker 开发环境迁移到生产/预发环境的推荐步骤。当前仓库以**模块化单体**为主，适合单节点或少量节点部署。

## 架构概览

| 组件 | 说明 |
|------|------|
| `raglaw-server` | Spring Boot 单体（API + Agent + RAG） |
| `raglaw-web` | 静态前端（Vite build → Nginx/CDN） |
| MySQL 8 | 业务库、会话、trace |
| PostgreSQL + pgvector | 向量索引（可选，混合检索） |
| MinIO | 文档原件 |
| RabbitMQ | 异步入库（推荐生产开启） |
| Redis | Agent 运行时缓存 |
| Langfuse | 可观测 L2（可选 profile） |

## 1. 环境变量清单

复制 [`.env.example`](../.env.example) 并按环境填写：

| 变量 | 生产要求 |
|------|----------|
| `RAGLAW_SEED_ADMIN_PASSWORD` | **必填**（`prod` profile 会校验） |
| `MYSQL_*` / `SPRING_DATASOURCE_*` | 指向生产 MySQL |
| `DASHSCOPE_API_KEY` | 真实 LLM + embedding |
| `RAGLAW_LLM_MOCK` | `false` |
| `POSTGRES_ENABLED` / `EMBEDDING_ENABLED` | 混合检索时均为 `true` |
| `RABBITMQ_ENABLED` | 生产建议 `true` |
| `MINIO_*` | 对象存储凭证 |
| `LANGFUSE_ENABLED` + keys | 可选，配合 observability profile |

启动 profile：

```bash
java -jar raglaw-server.jar --spring.profiles.active=prod
```

`application-prod.yml` 使用 `ddl-auto: validate`，schema 由 Flyway 管理。

## 2. 中间件

### 最小生产栈

- MySQL（高可用或托管 RDS）
- MinIO（或 S3 兼容对象存储，需确认 `RagProperties` 端点配置）
- 可选：Postgres、RabbitMQ、Redis、Langfuse

### Docker Compose（单机预发）

```bash
cd docker
docker compose up -d
# 可选可观测
docker compose --profile observability up -d
```

生产环境建议将各服务拆为托管服务，而非单节点 Compose。

## 3. 后端部署

```bash
cd backend
mvn -q -pl raglaw-server -am package -DskipTests
# 产物：backend/raglaw-server/target/raglaw-server-*.jar
```

健康检查：`GET /api/v1/health` → `status: UP`，`rag.hybridRetrievalReady` 按配置为 `true`。

### 安全

- 使用强随机 `RAGLAW_SEED_ADMIN_PASSWORD`，首次启动后通过 `/admin/users` 创建律师账号
- JWT secret 通过环境变量配置（见 `application.yml`）
- 仅暴露 443（Nginx 反代），内网访问 MySQL/MinIO
- CORS：修改 `SecurityConfig` 中 `allowedOrigins` 为生产前端域名

## 4. 前端部署

```bash
pnpm install
pnpm build:web
# 静态文件：raglaw-web/dist/
```

Nginx 示例：

```nginx
server {
  listen 443 ssl;
  server_name raglaw.example.com;
  root /var/www/raglaw-web/dist;
  location / {
    try_files $uri /index.html;
  }
  location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_buffering off;  # SSE /agui/run
  }
}
```

## 5. 入库与向量

1. 启动后登录 Admin，在「文档管理」上传语料（Rabbit 开启时自动异步入库）
2. 启用 pgvector 后需**重新 ingest** 已有文档
3. 失败文档可在 Admin 页查看 `ingestStage=FAILED` 并重试

## 6. 可观测性

- **L1**：`/admin/observability` — MySQL trace、A2A、检索片段
- **L2**：Langfuse UI（`docker compose --profile observability`），trace 详情页可跳转外链

## 7. 备份

见 [`backup.md`](backup.md)：MySQL `mysqldump`、MinIO bucket、Postgres volume。

## 8. 发布检查清单

- [ ] `mvn test` 通过
- [ ] `pnpm lint:web && pnpm build:web` 通过
- [ ] `RAGLAW_SEED_ADMIN_PASSWORD` 已设置且非默认值
- [ ] Flyway 迁移在目标库执行成功
- [ ] 健康检查与一次完整问答（含引用）验证
- [ ] RabbitMQ 异步入库端到端验证（上传 → INDEXED）
