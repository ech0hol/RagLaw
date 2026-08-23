# RAG 评测报告说明

## 文件命名

| 文件 | 含义 |
|------|------|
| `rag-pipeline-eval-YYYY-MM-DD.json` | fulltext 基线 |
| `rag-pipeline-eval-YYYY-MM-DD-hybrid.json` | hybrid 模式（mock 或真实向量） |
| `rag-pipeline-eval-YYYY-MM-DD-compare.json` | fulltext vs hybrid 对比摘要 |

## Mock vs 真实 DashScope 向量

**Mock（开发默认）**

```powershell
$env:RAGLAW_LLM_MOCK="true"
$env:EMBEDDING_ENABLED="true"
$env:POSTGRES_ENABLED="true"
.\scripts\eval-rag-quality.ps1 -RetrievalMode hybrid
```

报告字段：`embeddingMock: true`

**真实向量（生产对比）**

1. 设置 `DASHSCOPE_API_KEY`，关闭 mock：`RAGLAW_LLM_MOCK=false`
2. 重启后端并重 ingest 语料
3. 运行：

```powershell
$env:RAGLAW_LLM_MOCK="false"
.\scripts\eval-rag-quality.ps1 -RetrievalMode both -UseRealEmbedding
```

报告字段：`embeddingMock: false`, `useRealEmbedding: true`

## 集成测试

`RagPipelineEvaluationIT`（H2）覆盖上传→入库→分块，不依赖 MySQL FULLTEXT 或 Postgres。
