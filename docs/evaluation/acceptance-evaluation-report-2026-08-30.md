# RagLaw 项目验收评估报告

**评估时间**：2026-08-30 13:45–13:55 (UTC+8)  
**评估人**：Cursor Agent（严格按 [acceptance-plan.md](acceptance-plan.md) 三层门禁执行）  
**环境**：Windows 本地；后端 `:8080`；前端 `:5173`；Docker MySQL/ES/MinIO/RabbitMQ 运行中  
**裁决**：**不通过（FAIL）**

---

## 1. 执行摘要

本次验收在真实 LLM（`llmMock=false`）下完成 Gate A/B/C 自动化执行。核心 API 对话质量、权限隔离、可观测性链路**达标**；但严格验收规则要求 **Gate A 全绿 + Gate B 黄金查询 ≥90% + Gate C as-built 必须项全 PASS**，当前有三处硬性阻断：

| 阻断项 | 结果 | 影响 |
|--------|------|------|
| Gate A2 `RagHybridRecallIT` | FAIL（拖欠工资 hits=0） | 混合召回集成测试未通过 |
| Gate B3 黄金查询基准 | **14/17（82.35%）** < 90% | 危化品/行政处罚语料缺失 |
| Gate A5/C0 Playwright E2E | **6/11 失败** | UI 冒烟、合同工作台、刷新持久化未全绿 |

Gate B2 `acceptance-qa.ps1` 为 **34 PASS / 0 FAIL / 6 WARN**，表明 as-built API 主路径可用，但不足以单独判定项目通过。

---

## 2. 环境与预检

| 检查项 | 状态 | 说明 |
|--------|------|------|
| `GET /api/v1/health` | PASS | `status=UP`, `llmMock=false` |
| Docker 中间件 | PASS | mysql、elasticsearch、minio、rabbitmq healthy |
| 混合检索 | WARN | `hybridRetrievalReady=false`，仅 MySQL FULLTEXT |
| 语料统计 | PASS | `statute=8, case=2`；indexedDocuments=16, chunks=2003 |
| 语料种子 | PARTIAL | 劳动/民法/社保 fixture 已 INDEXED；案例上传 400；eval 脚本跳过 2 条社保 fixture（类目 NOT_FOUND） |

---

## 3. Gate A — 离线自动化

| ID | 项 | 状态 | 证据 |
|----|----|------|------|
| A1 | `mvn -q test` | **PASS** | exit 0 |
| A2 | `RagHybridRecallIT` | **FAIL** | `ingestedLaborFixtureSupportsRecallBenchmark`: 拖欠工资 hits=0，期望 ≥1 |
| A3 | raglaw-web Vitest | **PASS** | 32 passed / 5 files |
| A3-ui | @raglaw/ui Vitest | **PASS** | 75 passed / 10 files（含 answerQualityLint、citationMarkdownUtils） |
| A4 | lint + build | **PASS** | `tsc --noEmit` + Vite build exit 0 |
| A5 | Playwright E2E | **FAIL** | 4 passed, 6 failed, 1 skipped（见 §6） |

**Gate A 结论**：FAIL

---

## 4. Gate B — 在线 API 质量

### 4.1 B1 健康检查

- `status=UP`，`llmMock=false` → PASS
- `hybridRetrievalReady=false` → WARN（已接受例外，不挡 as-built）

### 4.2 B2 acceptance-qa.ps1（主 API 门禁）

**汇总：34 PASS / 0 FAIL / 6 WARN**

#### 按七大质量维度

| 维度 | 状态 | 关键证据 |
|------|------|----------|
| **整体质量** | PASS | health UP；admin 登录；modules 齐全 |
| **回答质量** | **PASS** | 3 轮均无 `DUPLICATE_BLOCKS`；`persist_quality_gates duplicate_blocks=0`；无矛盾免责声明 |
| **对话质量** | **PASS** | turn1–3 SSE 有效（len=513/1093/864）；`assistant_count=3`；`citations_persist 3/3`；`agui_stop` PASS |
| **合同审查** | PARTIAL | `upload` PASS；`export_docx` 2432 bytes PASS；**`ingest_review NOT_RUN`**；**`review_rag_hits=0`** |
| **法条/案例检索** | PASS（探针） | 4 条探针 search 均有命中；`knowledge_stats statute=8 case=2` |
| **权限管理** | **PASS** | lawyer→admin 403；未登录 401；contract IDOR 403 |
| **可观测性** | **PASS** | trace 5 stages；`trace_llm_output len=864`；stages 含 `knowledge_funnel,dual_channel_retrieval,rag_search,llm,knowledge_presearch` |

#### 6 项 WARN 说明

| 用例 | 详情 | 严重性 |
|------|------|--------|
| `hybrid_ready` | fulltext-only | 低（已接受） |
| `review_rag_hits` | ragHitCount=0，status=NOT_RUN | 中（合同 RAG 未触发） |
| `disable_CASE_run` | 禁用 CASE 后 AG-UI 回退 GENERAL | 低（已知 PARTIAL） |
| `case_pending` | 案例已 INDEXED，未重走审批流 | 低 |
| `graph_related` | related=0 | 中（图谱无关联边） |
| `ingest_review` | 新上传合同 status=NOT_RUN | 中（审查流水线未执行） |

#### 三轮对话质量详情（历史 P0 修复点验证）

| 轮次 | 问题 | len | refs | sections_1 | 状态 |
|------|------|-----|------|------------|------|
| turn1 | 刑法中刑罚种类有哪些 | 513 | 2 | 1 | PASS |
| turn2 | 那我想买社保呢 | 1093 | 5 | 1 | PASS |
| turn3 | 城乡居民医保可以异地报销吗 | 864 | 3 | 1 | PASS |

- 无重复 `1.**` 段落块
- turn3 引用路径正确（SOCIAL/GENERAL），未出现宪法噪声

**Gate B2 结论**：PASS（FAIL=0）

### 4.3 B3 RAG 召回基准

**汇总：14/17（82.35%）— FAIL（阈值 ≥90%）**

| 结果 | 查询 |
|------|------|
| PASS (14) | 拖欠工资、违约金过高、加班费、劳动合同解除、经济补偿、借贷利息、租赁合同违约、定金、劳动争议案例、违约责任、刑法中刑罚种类、附加刑的种类、社会保险参保、城乡居民医保异地报销 |
| **FAIL (3)** | **买化学品是否违法**、**危险化学品购买**、**行政处罚** |

失败原因：环境中无危化品/行政法扩展语料（仓库 fixture 不包含此类全文）。这不是检索算法回归，而是**语料覆盖缺口**。

AG-UI 评测问答：2/2 条均产生 reference 事件 → PASS

报告文件：[rag-pipeline-eval-2026-08-30-compare.json](rag-pipeline-eval-2026-08-30-compare.json)

**Gate B 结论**：FAIL（B3 未达标）

---

## 5. Gate C — UI / E2E

```
E2E_WITH_CORPUS=1  E2E_CONTRACT_REVIEW=1  pnpm e2e
结果：4 passed / 6 failed / 1 skipped（共 11）
```

| 用例 | 状态 | 失败原因 |
|------|------|----------|
| chat-multiturn session A（三轮一致性） | PASS | — |
| chat-multiturn session B（新会话隔离） | PASS | — |
| chat-multiturn session C（刷新保留历史） | **FAIL** | reload 后 `.rl-message--assistant` 期望 2 实际 1 |
| contract-review requires doc id | PASS | — |
| contract-review workbench upload page | PASS | — |
| contract-review split panels | **FAIL** | 登录后未出现「欢迎使用 RagLaw」标题 |
| contract upload → review auto pipeline | **FAIL** | 「开始审查」按钮 90s 超时 |
| accept revision | SKIP | 前置失败跳过 |
| contracts-delete confirm dialog | **FAIL** | 「历史合同」按钮 90s 超时 |
| smoke login and chat | **FAIL** | `.rl-bubble--assistant` 30s 不可见 |
| smoke reference cards | **FAIL** | 输入框 90s 超时 |

**Gate C 结论**：FAIL

人工清单 C1–C9 中 C9（免责声明/主题/律师重定向）本次未执行，记 NOT_RUN。

---

## 6. Gate S — 安全

| ID | 项 | 状态 |
|----|----|------|
| S1 | 合同 IDOR | PASS（API 403 + `ContractIdorSecurityIT`） |
| S2 | 律师禁访管理 API | PASS（403） |
| S3 | 未登录拦截 | PASS（401） |
| S4/S5 | 生产/备份清单 | NA（本地演示） |

**Gate S 结论**：PASS

---

## 7. 原始 §17 对照

| # | 标准 | 状态 | 说明 |
|---|------|------|------|
| 1 | Admin 建 L3、上传法规、Agent 引用跳转 | PARTIAL | L3 由种子提供，无独立类目管理页 |
| 2 | 律师上传案例 → 审批 → 可检索 | PARTIAL | 审批流存在；律师不能调 admin upload |
| 3 | 问试用期 → A2A → SSE + 引用 + 推荐 | **PASS** | API 三轮验证通过 |
| 4 | 复制/重新生成；刷新恢复历史 | PARTIAL | API persist PASS；UI reload E2E 失败 |
| 5 | 合同审查 → 高亮 → 采纳 → 导出 | PARTIAL | export PASS；ingest-review NOT_RUN |
| 6 | 案例详情关联法条图谱 | PARTIAL | 组件存在；当前样本 related=0 |
| 7 | L1 可观测 + Langfuse | PARTIAL | L1 PASS；L2 未配置 |
| 8 | Langfuse UI | NA | 可选 |
| 9 | 禁用 Agent 后不可达 | PARTIAL | registry 更新但回退 GENERAL |
| 10 | knowledgeScopes 限制检索 | **PASS** | turn3 社保/医保路径正确 |

---

## 8. 与历史验收对比

| 运行 | acceptance-qa | 黄金查询 | E2E | 裁决 |
|------|---------------|----------|-----|------|
| 修复前（会话记录） | 17P/1F/2W | — | — | FAIL |
| 修复后（会话记录） | **25P/0F/0W** | 9/9 或 16/17 | — | 接近 PASS |
| **本次（严格全量）** | **34P/0F/6W** | **14/17** | **4/11** | **FAIL** |

本次脚本覆盖面更广（新增合同 upload/export/IDOR、agui_stop 等），API 质量稳定；新增失败主要来自 B3 语料缺口、A2 IT 环境、E2E 稳定性。

---

## 9. 阻断项与修复建议（按优先级）

### P0 — 挡验收通过

1. **补充危化品/行政法语料**（或从 benchmark 移除无 fixture 的 3 条并在 scorecard 记录语料范围变更）→ 使 B3 ≥ 16/17
2. **修复 `RagHybridRecallIT`**：确认 Testcontainers ES 与 FULLTEXT 索引在 IT 环境可命中「拖欠工资」
3. **合同 ingest-review 流水线**：调查 `POST /contracts/{id}/ingest-review` 返回 `NOT_RUN` 的原因，确保 `ragHitCount>0`

### P1 — 改善 WARN / E2E

4. **E2E 稳定性**：smoke 助手气泡超时可能与 LLM 延迟或登录态有关；contract-review 登录断言需与当前首页文案对齐
5. **chat-multiturn session C**：刷新后 assistant 消息计数不一致，需排查消息持久化/前端 hydrate
6. **知识图谱 `related=0`**：为劳动案例 fixture 补充法条关联边

### P2 — 可选能力

7. 启用 ES 混合检索（`elasticsearchEnabled=true`）消除 `hybrid_ready` WARN
8. Agent 禁用后硬拒绝而非回退 GENERAL（§17-9 完整实现）

---

## 10. 最终裁决

依据 [acceptance-plan.md](acceptance-plan.md) 判定规则：

| 门禁 | 要求 | 实际 | 结果 |
|------|------|------|------|
| Gate A | 全绿 | A2 FAIL, A5 FAIL | **FAIL** |
| Gate B | FAIL=0 且召回 ≥90% | B2 PASS, B3 **82.35%** | **FAIL** |
| Gate C | as-built 必须项全 PASS | E2E 6 项失败 | **FAIL** |
| Gate S | 鉴权/IDOR | 全 PASS | PASS |

### 裁决：**不通过（FAIL）**

### 分项评价（七大维度）

| 维度 | 评级 | 说明 |
|------|------|------|
| 整体质量 | 部分达标 | 编译/单测/健康检查通过；IT 与 E2E 未全绿 |
| 回答质量 | **达标** | 无重复块、无矛盾免责、引用持久化完整 |
| 对话质量 | 部分达标 | API 三轮 SSE 优秀；UI 刷新持久化失败 |
| 合同审查 | 部分达标 | 上传/导出可用；审查流水线未跑通 |
| 法条/案例检索 | 部分达标 | 劳动/合同/刑法/社保探针通过；危化品 3 条 0 命中 |
| 权限管理 | **达标** | 403/401/IDOR 全部通过 |
| 可观测性 | **达标** | 5 阶段 trace、LLM 输出落库、分页正确 |

### 若需「有条件通过」

仅当评审方书面接受以下例外时可降为 CONDITIONAL_PASS：

- B1-hybrid fulltext-only
- B3 中 3 条危化品/行政法语料不在 as-built 承诺范围
- Gate C 人工 UAT C1–C9 补测通过且 E2E 失败归因于环境/超时

**本次未授予有条件通过**，因 B3 低于 90% 且 E2E 多项硬失败。

---

## 11. 证据文件

| 文件 | 说明 |
|------|------|
| [acceptance-report-latest.json](acceptance-report-latest.json) | B2 API 走查原始结果 |
| [rag-pipeline-eval-2026-08-30-compare.json](rag-pipeline-eval-2026-08-30-compare.json) | B3 召回基准 + AG-UI 评测 |
| [acceptance-scorecard-2026-08-30-eval.json](acceptance-scorecard-2026-08-30-eval.json) | 结构化 scorecard |
| Playwright | `raglaw-web/test-results/`（失败用例 error-context） |

---

*报告生成于自动化验收流水线，遵循严格阈值，未使用 `-AllowIncompleteCorpus` 等放行开关。*
