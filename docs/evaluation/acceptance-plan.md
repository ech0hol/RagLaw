# RagLaw 项目验收评估计划

以 **as-built**（根目录 [README.md](../../README.md) 功能概览）为必须通过基线；以原始规格 §17 为对照表。评审人按三层门禁执行，将结果填入 [acceptance-scorecard.template.json](acceptance-scorecard.template.json)（复制为带日期的 `acceptance-scorecard-YYYY-MM-DD.json`）。

**不在本次范围：** 补齐 CopilotKit、pgvector、8+4+4 独立专家拆分、独立类目管理页。

## 1. 环境

| 门禁 | 环境 | LLM |
|------|------|-----|
| Gate A | 本机 JDK 17 + pnpm；召回 IT 需 Docker | 不需要 Key |
| Gate B / C | Docker 中间件 + 后端 `8080` + 前端 `5173` | **演示验收必须** `DASHSCOPE_API_KEY` 且 `RAGLAW_LLM_MOCK=false` |

推荐启动：

```powershell
cd docker
docker compose up -d
cd ..
Copy-Item .env.example .env   # 填入 DASHSCOPE_API_KEY
$env:RAGLAW_SEED_ADMIN_PASSWORD = "raglaw-eval"
cd backend
mvn -pl raglaw-server spring-boot:run
```

另开终端：`pnpm dev:web`。语料：`.\scripts\reset-mvp-corpus.ps1`，评测脚本还会补社会法 fixture。

管理员：`admin@raglaw.local` / `raglaw-eval`（或你设置的 `RAGLAW_SEED_ADMIN_PASSWORD`）。

## 2. 已接受例外（不挡 as-built 通过，必须写入 scorecard）

- 对话走原生 AG-UI SSE，未接入 CopilotKit。
- 向量检索为 Elasticsearch，不是 pgvector；Redis 未使用。
- 种子 Agent 为 `GENERAL` / `STATUTE` / `CASE` / `CONTRACT`；A2A 白名单为上述三专家。`STATUTE_CIVIL` 是类目/scope，不是独立 Agent。
- README 示例路由 `/chat/STATUTE_CIVIL` 与种子码不一致，Gate C 必须实测。
- Tavily MCP、Langfuse L2、扫描件 OCR 为可选能力。
- `/admin/*` 仍有占位页；无独立「类目管理」路由（类目在文档管理树中）。

## 3. 判定

- **通过：** Gate A 全绿 + Gate B `FAIL=0` 且黄金查询命中率 ≥ 90% + Gate C as-built 必须项全 PASS。
- **有条件通过：** 仅可选能力失败（Langfuse、Tavily、真实向量、OCR），且已记录例外。
- **不通过：** 鉴权/IDOR 失败、SSE 无有效回复、语料已入库但黄金查询大面积 0 命中、合同审查无法完成、刷新后对话历史丢失。

---

## Gate A — 离线自动化

**通过条件：** 下列命令均为 exit 0。Playwright 冒烟需要后端已启动。

```powershell
cd backend
mvn -q test
mvn -pl raglaw-server test -Dtest=RagHybridRecallIT
cd ..
pnpm --filter raglaw-web test
pnpm lint:web
pnpm build:web
pnpm e2e
```

| ID | 项 | 命令/说明 |
|----|----|-----------|
| A1 | 后端单测 + IT | `mvn -q test`（含 `RagPipelineEvaluationIT`、`ContractIdorSecurityIT`） |
| A2 | 混合召回 IT | `RagHybridRecallIT`（需 Docker Testcontainers） |
| A3 | 前端 Vitest | `pnpm --filter raglaw-web test` |
| A4 | 类型检查 + 构建 | `pnpm lint:web && pnpm build:web` |
| A5 | Playwright 冒烟 | `pnpm e2e`：登录并发一条消息（[`raglaw-web/e2e/smoke.spec.ts`](../../raglaw-web/e2e/smoke.spec.ts)） |

覆盖：编译、鉴权/IDOR、入库切片、检索内核、前端类型。不覆盖真实 LLM 质量。

---

## Gate B — 在线 API 质量

**前置：** 后端健康；管理员密码已设。

### B1 健康与混合检索

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

| ID | 项 | 通过标准 |
|----|----|----------|
| B1-health | `data.status` | `UP` |
| B1-hybrid | 若宣称混合检索 | `rag.hybridRetrievalReady=true` 且 `rag.elasticsearchIndexSyncReady=true` |
| B1-fulltext | 未开 ES | scorecard 标记「仅 FULLTEXT」，不挡 as-built |
| B1-es-audit | 可选 | `.\scripts\audit-es-migration.ps1` |

### B2 严格验收脚本

```powershell
$env:RAGLAW_ADMIN_PASSWORD = "raglaw-eval"
.\scripts\acceptance-qa.ps1
```

**通过条件：** 输出 `FAIL=0`。`WARN` 须在 scorecard 说明。

脚本覆盖：health（含混合检索标志）、登录、建律师号、律师 403、未登录 401、知识搜索、三轮 SSE、引用持久化、重复段落门禁、合同列表/上传审查/导出、案例审批、Agent reload/禁用、专家码探测、知识图谱关联、合同 IDOR、trace 阶段。

质量门：助手回复长度 > 50；无重复 `1. **` 块；有引用时不得出现「未检索到」。

### B3 RAG 召回

基准：[recall-benchmark.json](recall-benchmark.json)（`text`、`minHitCount`、可选 `top1PathPrefix` / `minDistinctDocuments`）。

```powershell
$env:RAGLAW_ADMIN_PASSWORD = "raglaw-eval"
.\scripts\eval-rag-quality.ps1 -RetrievalMode both
# 真实向量：
.\scripts\eval-rag-quality.ps1 -RetrievalMode both -UseRealEmbedding
```

脚本会 ingest 仓库内全部 fixture（劳动法、民法典合同、劳动案例、社会保险、医保异地），再对 17 条黄金查询调用 `GET /api/v1/knowledge/search`。

| 指标 | 阈值 |
|------|------|
| 黄金查询通过率 | ≥ 90%（16/17） |
| AG-UI 带 `reference` | ≥ 1/2 条评测问答 |

**语料说明：** 仓库 fixture **不包含** 刑法/危化品全文。`刑法中刑罚种类`、`附加刑的种类`、`买化学品是否违法`、`危险化学品购买`、`行政处罚` 依赖环境中已入库的扩展语料（此前演示库曾有 statute≈8）。无扩展语料时这些项会 FAIL，可用 `-AllowIncompleteCorpus` 只出报告不把进程码打成失败，但 **演示验收不得使用该开关放行**。

报告：`docs/evaluation/rag-pipeline-eval-YYYY-MM-DD*.json`。

---

## Gate C — UI / 人工 UAT

### C0 Playwright（加深）

后端与语料就绪后：

```powershell
$env:E2E_ADMIN_PASSWORD = "raglaw-eval"
$env:E2E_WITH_CORPUS = "1"
$env:E2E_CONTRACT_REVIEW = "1"
pnpm e2e
```

- [`chat-multiturn.spec.ts`](../../raglaw-web/e2e/chat-multiturn.spec.ts)：三轮一致性、引用卡片
- [`contract-review.spec.ts`](../../raglaw-web/e2e/contract-review.spec.ts)：分栏工作台、上传跳转

### C1–C9 人工清单

每条记录证据（截图或 `traceId`）。

| ID | 场景 | 步骤与期望 |
|----|------|------------|
| C1 | 对话 | `/` 问「劳动合同试用期」→ 流式、引用可点原文、复制/重新生成、刷新后历史仍在；`recommend` 建议问题可见 |
| C2 | A2A | GENERAL 问劳动问题；`/admin/observability` 该次 trace 含 `knowledge_funnel` / `dual_channel_retrieval`（及 A2A 相关阶段若有） |
| C3 | 专家直达 | 打开 `/chat/STATUTE` 应进入法规助手；打开 `/chat/STATUTE_CIVIL` 记录实际（标题回退码名 / 运行时报错 / 意外可用） |
| C4 | 知识检索 | `/knowledge/statutes` 搜「拖欠工资」有结果；有关联文档时详情页出现知识图谱 |
| C5 | 入库审批 | Admin 上传法规 → INDEXED 可检索；上传案例 → 待审批 → 批准后可检索 |
| C6 | 合同 | `/contracts` 上传 → 审查页左右栏、风险、高亮、采纳修订、导出 docx/pdf；合同对话绑定该文档 |
| C7 | 管理 | `/admin/agents` 改 scope 后 reload；禁用 Agent 后该码不可运行（随后恢复）；`/admin/users` 建号；可观测打开瀑布 |
| C8 | L2 可选 | Langfuse profile 启动后 L1 可跳转且 `langfuse_trace_id` 有值；失败记 WARN |
| C9 | 横切 | 免责声明、深色主题、律师访问 `/admin/*` 被重定向到 `/` |

---

## 4. 安全与运维

| ID | 项 | 方式 |
|----|----|------|
| S1 | 合同 IDOR | Gate A：`ContractIdorSecurityIT`；Gate B：acceptance-qa 复测 |
| S2 | 律师禁访管理 API | `GET /api/v1/admin/users` → 403 |
| S3 | 未登录 | `GET /api/v1/conversations` → 401 |
| S4 | 生产清单 | 对照 [deployment.md](../deployment.md) 第 8 节；本地演示可标 NA |
| S5 | 备份 | 对照 [backup.md](../backup.md)；本地演示可标 NA |

---

## 5. 原始 §17 对照

| # | 原标准 | as-built 状态 |
|---|--------|----------------|
| 1 | Admin 建 L3、上传法规、Agent 引用并跳转原文 | **部分**：L3 由种子/Flyway 提供，无独立类目管理页；上传与引用跳转已实现 |
| 2 | 律师上传案例 → 审批 → CASE 可检索 | **部分**：案例 ingest 后待审批、Admin 批准已实现；律师不能调 `/api/v1/admin/documents`（仅 ADMIN），律师经管理流程由 Admin 代传或需后续开律师上传入口 |
| 3 | `/` 问试用期 → A2A → SSE + 引用 + 问题推荐 | **已实现**（专家为 `STATUTE` 等，非 `STATUTE_*` 拆分实例） |
| 4 | 复制 / 重新生成；刷新恢复历史 | **已实现** |
| 5 | 上传扫描 PDF → 高亮 → 采纳 → 导出 DOCX/PDF | **部分**：文本/PDF 工作台、采纳、导出已实现；扫描 OCR 依赖 DashScope，失败记可选 WARN |
| 6 | 案例详情关联法条图谱 | **已实现**（[`KnowledgeGraphView`](../../raglaw-web/src/components/KnowledgeGraphView.tsx)） |
| 7 | L1 可观测 + 跳转 Langfuse | **部分**：L1 必须；L2 可选 |
| 8 | Langfuse UI 与 `langfuse_trace_id` | **可选** |
| 9 | 关闭 Agent `enabled` 后不可达、A2A 不调用 | **部分**：reload 后 registry 不含该码；`AguiRunService` 对未知码回退 `GENERAL`，直达不一定硬失败 |
| 10 | knowledgeScopes 限制 RagTool 检索范围 | **已实现** |

---

## 6. 建议执行顺序（约半天）

1. Gate A（20–40 min）
2. 起栈 + 语料
3. Gate B1–B3
4. Gate C Playwright + 人工走查
5. 填写 scorecard，给出通过 / 有条件通过 / 不通过
