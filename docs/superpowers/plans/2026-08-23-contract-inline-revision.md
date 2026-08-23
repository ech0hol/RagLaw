# 合同条款级 Inline 修订 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accepted risk revisions replace the corresponding excerpt inline in contract body text and exports, instead of appending an appendix section.

**Architecture:** On accept, `ContractReviewService` computes `revised_excerpt` by replacing `excerpt` with `suggestion` inside the bound chunk. `ContractTextService.buildFullText()` applies accepted revisions when concatenating chunks. Export uses the same revised text. Frontend shows `<del>`/`<ins>` styling in text view.

**Tech Stack:** Java 17, Spring Boot, React 19, shared CSS in `packages/ui`.

**Spec:** [`docs/superpowers/specs/2026-08-23-contract-inline-revision-design.md`](../specs/2026-08-23-contract-inline-revision-design.md)

## Global Constraints

- Flyway **V15** for `revised_excerpt` (V13=ingest stage, V14=highlight rects)
- MVP: rule-based `excerpt → suggestion` replace; no LLM-generated replacement endpoint
- Export must **not** include `## 修订建议（已采纳）` appendix
- PDF preview still uses extracted text + H2 highlights; does not modify PDF binary
- Styles in `packages/ui/src/theme/components.css`

---

## File Map

| File | Responsibility |
|------|----------------|
| `V15__contract_risk_revised_excerpt.sql` | Add `revised_excerpt TEXT` |
| `ContractRiskEntity.java` | `revisedExcerpt` field |
| `ContractReviewService.java` | Compute on accept, clear on unaccept |
| `ContractTextService.java` | Inline merge in `buildFullText()` |
| `ContractExportService.java` | Use revised full text |
| `ContractController.java` | Optional unaccept endpoint |
| `ContractTextViewer.tsx` | del/ins rendering |
| `ContractReviewPage.tsx` | Refresh text after accept |

---

### Task 1: Schema + entity

**Files:**
- Create: `backend/raglaw-server/src/main/resources/db/migration/V15__contract_risk_revised_excerpt.sql`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/ContractRiskEntity.java`

**Interfaces:**
- Produces: `ContractRiskEntity.revisedExcerpt` (`String`, nullable)

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE raglaw_contract_risk
  ADD COLUMN revised_excerpt TEXT NULL AFTER accepted;
```

- [ ] **Step 2: Add JPA field**

```java
@Column(name = "revised_excerpt", columnDefinition = "TEXT")
private String revisedExcerpt;
```

- [ ] **Step 3: Verify Flyway V15 on startup**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add revised_excerpt column for inline contract revisions"
```

---

### Task 2: Revision computation on accept

**Files:**
- Create: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/RevisionCalculator.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractReviewService.java`
- Create: `backend/raglaw-rag/src/test/java/com/raglaw/rag/contract/RevisionCalculatorTest.java`

**Interfaces:**
- Produces: `String computeRevisedExcerpt(String chunkContent, String excerpt, String suggestion)`

- [ ] **Step 1: Write failing tests**

```java
@Test
void replacesExcerptWithSuggestion() {
    String out = RevisionCalculator.compute(
        "甲方应支付违约金十万元。",
        "十万元",
        "五万元"
    );
    assertEquals("甲方应支付违约金五万元。", out);
}

@Test
void appendsSuggestionWhenExcerptMissing() {
    String out = RevisionCalculator.compute("原文。", "不存在", "建议补充");
    assertTrue(out.contains("【建议】建议补充"));
}
```

- [ ] **Step 2: Run tests — expect FAIL**

- [ ] **Step 3: Implement per spec**

```java
public static String compute(String chunkContent, String excerpt, String suggestion) {
    int idx = chunkContent.indexOf(excerpt);
    if (idx >= 0) {
        return chunkContent.substring(0, idx) + suggestion
            + chunkContent.substring(idx + excerpt.length());
    }
    return chunkContent + "\n【建议】" + suggestion;
}
```

- [ ] **Step 4: Update `acceptRisk()`**

```java
risk.setAccepted(true);
String chunkText = chunkRepository.findById(risk.getChunkId())
    .map(DocumentChunkEntity::getContent).orElse("");
risk.setRevisedExcerpt(RevisionCalculator.compute(chunkText, risk.getExcerpt(), risk.getSuggestion()));
riskRepository.save(risk);
```

- [ ] **Step 5: Run tests — expect PASS**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: compute revised_excerpt on risk accept"
```

---

### Task 3: `ContractTextService` inline assembly

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractTextService.java`
- Create: `backend/raglaw-rag/src/test/java/com/raglaw/rag/contract/ContractTextServiceTest.java`

**Interfaces:**
- Produces: `buildFullText(documentId)` with inline revisions applied
- `buildRevisedText(documentId)` delegates to `buildFullText()` (no appendix)

- [ ] **Step 1: Write failing test**

```java
@Test
void buildFullText_appliesAcceptedRevisionInline() {
  // seed chunk + accepted risk with revised_excerpt
  String text = service.buildFullText(docId);
  assertFalse(text.contains("修订建议（已采纳）"));
  assertTrue(text.contains("五万元"));
}
```

- [ ] **Step 2: Implement merge logic**

When building each chunk segment, if an accepted risk exists for that `chunk_id` with non-null `revised_excerpt`, substitute the excerpt portion (or full chunk per spec) in output.

- [ ] **Step 3: Remove appendix block from `buildRevisedText()`**

Delete or bypass the `## 修订建议（已采纳）` section.

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: inline revised text assembly without appendix"
```

---

### Task 4: Export + accept-all

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractExportService.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractReviewService.java`

- [ ] **Step 1: `ContractExportService` uses `buildFullText()` / revised path**

Verify exported DOCX body has no appendix heading.

- [ ] **Step 2: `acceptAllRisks()` applies `RevisionCalculator` per risk**

Mirror single-accept logic in batch.

- [ ] **Step 3: Manual export test**

Accept one risk → download DOCX → inline change visible, no appendix.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: export inline-revised contract text"
```

---

### Task 5: API + frontend

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/web/ContractController.java`
- Modify: `raglaw-web/src/pages/ContractReviewPage.tsx`
- Modify: `raglaw-web/src/components/ContractTextViewer.tsx`
- Modify: `packages/ui/src/theme/components.css`

- [ ] **Step 1: Optional `POST /contracts/{id}/risks/{riskId}/unaccept`**

Clears `accepted` and `revised_excerpt`.

- [ ] **Step 2: After accept, refetch `GET /contracts/{id}/text`**

- [ ] **Step 3: `ContractTextViewer` render accepted segments**

```tsx
<span className="rl-revision">
  <del className="rl-revision__old">{excerpt}</del>
  <ins className="rl-revision__new">{suggestion}</ins>
</span>
```

- [ ] **Step 4: CSS**

```css
.rl-revision__old { text-decoration: line-through; opacity: 0.7; }
.rl-revision__new { text-decoration: underline; color: var(--rl-color-success); }
```

- [ ] **Step 5: `pnpm lint:web && pnpm build:web`**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: inline revision UI for contract text view"
```

---

### Task 6: Acceptance checklist

- [ ] **Step 1: Accept single risk — body text changes, not appendix**
- [ ] **Step 2: Accept all — all inline revisions applied**
- [ ] **Step 3: Unaccept restores original text**
- [ ] **Step 4: Export DOCX/PDF without appendix section**

---

## Self-Review

| Spec requirement | Task |
|------------------|------|
| revised_excerpt persistence | Task 1, 2 |
| excerpt→suggestion replace | Task 2 |
| buildFullText inline | Task 3 |
| No appendix export | Task 3, 4 |
| accept-all | Task 4 |
| Frontend del/ins | Task 5 |
