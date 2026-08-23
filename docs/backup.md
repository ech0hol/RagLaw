# RagLaw 备份与恢复

## MySQL 业务库

```bash
# 导出
docker exec raglaw-mysql mysqldump -uraglaw -praglaw raglaw > backup/raglaw-$(date +%F).sql

# 恢复（先停后端）
docker exec -i raglaw-mysql mysql -uraglaw -praglaw raglaw < backup/raglaw-2026-08-23.sql
```

Windows PowerShell：

```powershell
docker exec raglaw-mysql mysqldump -uraglaw -praglaw raglaw > backup\raglaw.sql
Get-Content backup\raglaw.sql | docker exec -i raglaw-mysql mysql -uraglaw -praglaw raglaw
```

## Docker 数据卷

| 卷名 | 内容 |
|------|------|
| `raglaw_raglaw-mysql-data` | MySQL 业务数据 |
| `raglaw_raglaw-postgres-data` | pgvector 向量 |
| `raglaw_raglaw-minio-data` | MinIO 对象（若启用） |
| `raglaw_raglaw-langfuse-db-data` | Langfuse 元数据 |

```bash
# 列出卷
docker volume ls | grep raglaw

# 完整重置（会丢失数据）
docker compose -f docker/docker-compose.yml down
docker volume rm raglaw_raglaw-mysql-data
docker compose -f docker/docker-compose.yml up -d
```

重置 MySQL 后，后端启动时 Flyway 会重新迁移 `V1`–`V8`。

## 本地上传目录

未启用 MinIO 时，原件在：

```
./tmp/raglaw-uploads/<documentId>/<filename>
```

建议定期打包该目录，或与 MySQL 备份同步。

## Langfuse（可选）

可观测 profile 启动后，Langfuse 数据在 `raglaw_raglaw-langfuse-db-data`。可按 Postgres 常规方式 `pg_dump` 备份 `langfuse` 库。
