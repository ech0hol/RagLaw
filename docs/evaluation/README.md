# RAG 评测与项目验收

## 项目验收（入口）

完整门禁、用例、阈值与 §17 对照见 [acceptance-plan.md](acceptance-plan.md)。

执行顺序：

1. **Gate A** 离线：`mvn test`、前端 Vitest/lint/build、Playwright 冒烟
2. **Gate B** 在线：`.\scripts\acceptance-qa.ps1`，然后 `.\scripts\eval-rag-quality.ps1 -RetrievalMode both`
3. **Gate C** UI：`E2E_WITH_CORPUS=1 E2E_CONTRACT_REVIEW=1 pnpm e2e` + 人工清单
4. 复制 [acceptance-scorecard.template.json](acceptance-scorecard.template.json) 为 `acceptance-scorecard-YYYY-MM-DD.json` 填写 `verdict`

最新 API 走查报告：`acceptance-report-latest.json`（由 `acceptance-qa.ps1` 覆盖写入）。

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
$env:ELASTICSEARCH_ENABLED="true"
$env:EMBEDDING_ENABLED="true"
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

`RagPipelineEvaluationIT`（H2）覆盖上传→入库→分块，不依赖 MySQL FULLTEXT 或 Elasticsearch。

## 黄金查询基准

[`recall-benchmark.json`](recall-benchmark.json) 定义黄金查询（`text`、`minHitCount`、可选 `top1PathPrefix` / `minDistinctDocuments`）。仓库 fixture 覆盖劳动/合同/案例/社会保险；刑法与危化品查询依赖环境中的扩展语料。

- **规范校验**：`RecallBenchmarkSpecTest`（`mvn test`）断言 JSON 结构
- **命中率实测**：`eval-rag-quality.ps1` 会 ingest 仓库内全部 fixture，并对 17 条查询调用知识搜索 API（验收阈值 ≥ 90%，见 [acceptance-plan.md](acceptance-plan.md) Gate B3）

```powershell
$env:RAGLAW_ADMIN_PASSWORD="raglaw-eval"
.\scripts\eval-rag-quality.ps1 -RetrievalMode both
```

对比报告中 `benchmark.queries[]` 的 `pass`、`hitCount`、`top1Path`。无扩展语料时可用 `-AllowIncompleteCorpus` 仅出报告（演示验收不得用该开关放行）。

## A2A 路由（Wave 5 + MVP 精简）

GENERAL 对话通过 `A2aPeerSelector`：规则 L2 映射优先（MVP 3 专家：`STATUTE_CIVIL` / `CASE_CIVIL` / `CONTRACT_GENERAL`），0/多匹配时 LLM（`qwen-turbo`）从 `a2aPeers` 白名单选择；mock/test 无 API key 时回退规则优先级。劳动类问题路由至 `STATUTE_CIVIL`（与 fixture 语料 scope 对齐）。
