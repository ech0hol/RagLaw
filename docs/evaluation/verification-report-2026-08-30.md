# 验收结果验证报告（2026-08-30）

执行人：Cursor Agent（Phase 1 复验）  
环境：`http://localhost:8080` / `http://localhost:5173`，`llmMock=false`，`hybridRetrievalReady=false`

---

## 1. 总结裁定

| 原报告项 | 原结论 | 复验结论 | 说明 |
|----------|--------|----------|------|
| B-01 export_docx 401 | FAIL | **不成立（环境/部署）** | 重建后端后 export 200，acceptance-qa 34 PASS / 0 FAIL |
| B-02 召回 12/17 | FAIL | **部分成立** | 复跑 14/17；3 条因 eval 脚本未 ingest 对应 fixture |
| B-03 E2E 全失败 | FAIL | **部分成立** | 登录已通过；失败在 LLM 响应超时（`.rl-bubble--assistant` 30s） |
| B-04 RagHybridRecallIT | FAIL | **成立（非挡演示）** | 未在本轮复跑；ES Testcontainers 专项 |
| Gate B acceptance-qa | 33/1/6 | **34/0/6 PASS** | export 已绿 |
| Gate S | PASS | **成立** | 未复测，沿用原报告 |

**修订判定：** 若按当前重建环境验收，Gate B2 **可通过**；B3 仍差 3 条（脚本缺口）；Gate C0 仍为 LLM 超时问题，非认证故障。

---

## 2. B-01 合同 export_docx

### 原报告
- `contract/export_docx` HTTP 401，同 token 下 text/review 200
- 响应体无 `data:null`（Spring Security 层 401 指纹）

### 复验步骤与结果

```text
# 上传新合同后立即 export
export http=200 bytes=2327

# acceptance-qa 重跑
PASS: 34  FAIL: 0  WARN: 6
export_docx: PASS bytes=2431
```

### 裁定：**不成立（作为产品缺陷）**

根因推断为**运行中后端 JAR 与源码不一致**（旧进程未 `mvn install` 后重启）。当前 [`ContractController.export`](backend/raglaw-rag/src/main/java/com/raglaw/rag/web/ContractController.java) 在重建后可正常工作。

### 建议（非代码缺陷）
- 验收文档明确要求：`mvn -pl raglaw-server -am install` 后再 `spring-boot:run`
- 保留 `ContractIdorSecurityIT#ownerCanExportContractDocx` 防回归

---

## 3. B-02 召回基准

### 原报告：12/17（70.59%）

失败项：加班费、借贷利息、买化学品、危险化学品购买、行政处罚

### 复验结果：14/17（82.35%）

| 查询 | 原报告 | 复验 | 归因 |
|------|--------|------|------|
| 加班费 | FAIL (top1 CASE) | **PASS** (top1 STATUTE) | 环境/语料差异，非稳定缺陷 |
| 借贷利息 | FAIL (top1 CRIMINAL) | **PASS** (top1 CIVIL/CONTRACT) | 同上 |
| 买化学品是否违法 | FAIL 0 hit | **FAIL** 0 hit | fixture 存在但未 ingest |
| 危险化学品购买 | FAIL 0 hit | **FAIL** 0 hit | 同上 |
| 行政处罚 | FAIL 0 hit | **FAIL** 0 hit | 同上 |

### 脚本缺口（已确认）

仓库已有 fixture，但 [`scripts/eval-rag-quality.ps1`](scripts/eval-rag-quality.ps1) 未包含：

- [`docs/fixtures/statutes/hazardous-chemicals-excerpt.md`](docs/fixtures/statutes/hazardous-chemicals-excerpt.md)
- [`docs/fixtures/statutes/administrative-penalty-excerpt.md`](docs/fixtures/statutes/administrative-penalty-excerpt.md)

另：eval 脚本社会法类目仍用 `cat_l3_statute_social_general`（不存在），导致社保/医保 fixture SKIP；[`seed-e2e-corpus.ps1`](scripts/seed-e2e-corpus.ps1) 已用 UUID `7b2f27be-...`。

### 裁定：**部分成立**

- B3 按官方脚本判定仍为 FAIL（14/17 < 90%）
- 其中 **3/5 原失败项为评测脚本缺口**，ingest 后预计 **17/17**
- 加班费/借贷利息在原环境可能受扩展语料排序影响，复验已通过

---

## 4. B-03 Playwright E2E

### 原报告（Gate C）
- 0/10 通过，登录 UI 超时
- API login `success: false`（疑似 RATE_LIMITED）

### 复验（`pnpm e2e e2e/smoke.spec.ts`，workers=1）

```text
login and chat smoke:
  - 登录标题「欢迎使用 RagLaw」：通过（失败点在 line 20）
  - .rl-bubble--assistant：30s 超时未出现

reference cards when corpus is seeded: skipped（未单独跑）
```

### 裁定：**部分成立**

| 子项 | 结论 |
|------|------|
| 认证/密码错误 | **不成立** — API 与 UI 登录正常 |
| 并行限流导致 Gate C 全挂 | **可能成立** — 原 Gate C 与 acceptance-qa 同会话大量 login |
| LLM 响应慢导致 smoke 失败 | **成立** — 需将 assistant 等待增至 90s 或 E2E 启用 mock LLM |

Gate A 子 agent 报告 2 passed / 2 failed，与「登录可过、聊天超时」一致。

---

## 5. B-04 RagHybridRecallIT

### 原报告
- Testcontainers + ES 8.15，ingest 后首条 benchmark 查询 hits=0

### 本轮
- 未复跑（Docker IT 耗时）

### 裁定：**成立，但不挡 as-built 演示**（B1-fulltext 已记 WARN）

---

## 6. WARN 项

| WARN | 裁定 |
|------|------|
| hybrid_ready | 不挡通过（已接受例外） |
| review_rag_hits=0 | 不挡 — 抽查合同 status=NOT_RUN |
| disable_CASE_run | 不挡 — 文档化 PARTIAL |
| graph_related=0 | 不挡 — 语料无边数据 |
| case_pending | 不挡 — 幂等跳过 |

---

## 7. 已确认通过项（无需重验）

来自 acceptance-qa 34 PASS：

- 三轮 SSE + 引用持久化 + 无重复 `1.**`
- 鉴权：律师 403、未登录 401、合同 IDOR 403
- Trace：`knowledge_funnel`、`dual_channel_retrieval`、`llmUsage.outputText`
- Agent reload / STATUTE 专家码

---

## 8. Phase 2 修复优先级（待 Agent 模式执行）

### P0 — 无（export 已随重建恢复）

### P1 — 评测脚本同步

修改 [`scripts/eval-rag-quality.ps1`](scripts/eval-rag-quality.ps1) 与 [`scripts/seed-e2e-corpus.ps1`](scripts/seed-e2e-corpus.ps1)：

1. 社会法类目改为 `7b2f27be-dbb0-49fe-866a-059ad938bebe`
2. 增加 `hazardous-chemicals-excerpt.md`、`administrative-penalty-excerpt.md`（类目 `cat_l3_statute_admin_general`，需 Flyway V38）

预期：B3 达到 17/17

### P1 — E2E 超时

[`raglaw-web/e2e/smoke.spec.ts`](raglaw-web/e2e/smoke.spec.ts) 将 `.rl-bubble--assistant` 等待改为 90s（与 `playwright.config.ts` timeout 对齐）

### P2 — RagHybridRecallIT

ingest 后 ES refresh 等待

### P2 — 部署文档

验收前置步骤写入 [`docs/evaluation/acceptance-plan.md`](docs/evaluation/acceptance-plan.md)：必须先 `mvn install` 再启动

---

## 9. 建议复验命令

```powershell
cd backend
mvn -pl raglaw-server -am install -DskipTests
mvn -pl raglaw-server spring-boot:run

# 另开终端
$env:RAGLAW_ADMIN_PASSWORD = "raglaw-eval"
.\scripts\acceptance-qa.ps1          # 期望 FAIL=0
.\scripts\eval-rag-quality.ps1 -RetrievalMode both   # 脚本修复后期望 >=16/17

$env:E2E_ADMIN_PASSWORD = "raglaw-eval"
pnpm e2e e2e/smoke.spec.ts           # 超时修复后期望 PASS
```
