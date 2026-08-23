# RagLaw API 概览

Base URL（本地）：`http://localhost:8080`

所有 JSON 接口返回统一结构：

```json
{ "success": true, "data": { ... } }
```

错误时：

```json
{ "success": false, "error": { "code": "...", "message": "..." } }
```

鉴权：除登录/刷新/健康检查外，请求头需带 `Authorization: Bearer <accessToken>`。Refresh token 通过 HttpOnly Cookie 续期。

## 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | `{ email, password }` |
| POST | `/api/v1/auth/refresh` | Cookie 续期 access token |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/me` | 当前用户 |

## 会话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/conversations` | 会话列表 |
| POST | `/api/v1/conversations` | `{ agentCode?, contextDocumentId? }` |
| GET | `/api/v1/conversations/{id}/messages` | 消息历史 |
| PATCH | `/api/v1/conversations/{id}` | 更新标题 |

## AG-UI 对话（SSE）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agui/run` | `{ conversationId, message?, agentCode?, regenerate? }` |
| POST | `/api/v1/agui/stop` | `{ taskId }` |

SSE 事件：`meta` / `status` / `text` / `reference` / `recommend` / `done` / `error`

## 知识检索

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/knowledge/search?q=&docType=&l2Path=&page=&pageSize=` | 全文检索（分页） |
| GET | `/api/v1/knowledge/documents/{id}` | 文档详情 + 关联文档 |
| GET | `/api/v1/knowledge/documents/{id}/download` | 下载原件 |

## 合同审查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/contracts/{documentId}/text` | 合同全文 |
| GET | `/api/v1/contracts/{documentId}/file` | 原件预览（PDF inline） |
| GET | `/api/v1/contracts/{documentId}/risks` | 风险列表 |
| POST | `/api/v1/contracts/{documentId}/review` | 重新分析风险 |
| POST | `/api/v1/contracts/{documentId}/ingest-review` | 入库 + 分类 + 风险分析 |
| POST | `/api/v1/contracts/{documentId}/accept-revisions` | 采纳全部修订建议（返回修订文本） |
| GET | `/api/v1/contracts/{documentId}/export?format=docx\|pdf` | 导出修订版 |

上传仍走 Admin 文档接口：`POST /api/v1/admin/documents/upload` + ingest-review。

## 管理端（ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| CRUD | `/api/v1/admin/categories` | 类目 |
| POST | `/api/v1/admin/documents/upload` | 上传 |
| POST | `/api/v1/admin/documents/{id}/ingest` | 入库 |
| GET | `/api/v1/admin/approvals/pending` | 待审批案例 |
| POST | `/api/v1/admin/approvals/{id}/approve` | 审批 |
| GET/PUT | `/api/v1/admin/agents` | Agent 配置 |
| POST | `/api/v1/admin/agents/reload` | 热加载 |
| GET/POST | `/api/v1/admin/users` | 用户列表 / 创建账号 |
| GET | `/api/v1/admin/documents/recent` | 最近文档 |
| GET | `/api/v1/admin/documents/{id}` | 文档详情（含 ingest 状态） |
| POST | `/api/v1/admin/documents/{id}/retry-ingest` | 失败重试入库 |
| GET | `/api/v1/admin/traces` | Trace 列表 |
| GET | `/api/v1/admin/traces/{id}` | Trace 详情 |
| POST | `/api/v1/admin/knowledge/refs` | 案例-法条关联 |

## 健康检查

`GET /api/v1/health` — 含 `rag.hybridRetrievalReady` 等状态。
