# 合同 PDF 坐标级高亮 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a user selects a contract risk, the PDF viewer shows a bounding-box highlight on the matching text region (not just page navigation).

**Architecture:** During risk analysis, `PdfTextLocator` uses PDFBox `TextPosition` to compute rectangles for the risk excerpt. Rectangles are stored as JSON on `ContractRiskEntity`. `ContractPdfViewer` renders absolute-position overlays per page, converting PDF bottom-left coordinates to CSS top-left.

**Tech Stack:** Java 17, PDFBox, Spring Boot, React 19, react-pdf, shared CSS in `packages/ui`.

**Spec:** [`docs/superpowers/specs/2026-08-23-contract-pdf-highlight-design.md`](../specs/2026-08-23-contract-pdf-highlight-design.md)

## Global Constraints

- Flyway **V14** for `highlight_rects_json` (H3 async ingest uses V13)
- Coordinate system: PDF user space, origin bottom-left; frontend converts to CSS top-left
- OCR PDFs: page-only fallback when no text layer match
- Styles in `packages/ui/src/theme/components.css` — not duplicated in raglaw-web
- Non-PDF contracts: existing `ContractTextViewer` `<mark>` behavior unchanged

---

## File Map

| File | Responsibility |
|------|----------------|
| `V14__contract_risk_highlight_rects.sql` | Add `highlight_rects_json` column |
| `HighlightRect.java` | DTO record `{page,x,y,width,height}` |
| `PdfTextLocator.java` | PDFBox rect lookup |
| `ContractRiskEntity.java` | Persist JSON column |
| `ContractRiskAnalyzer.java` | Populate rects during analysis |
| `ContractRiskDto.java` | API field `highlightRects` |
| `ContractPdfViewer.tsx` | Overlay rendering |
| `ContractReviewPage.tsx` | Pass rects to viewer |
| `components.css` | `.rl-pdf-highlight` styles |

---

### Task 1: Schema + domain model

**Files:**
- Create: `backend/raglaw-server/src/main/resources/db/migration/V14__contract_risk_highlight_rects.sql`
- Create: `backend/raglaw-rag/src/main/java/com/raglaw/rag/dto/HighlightRect.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/ContractRiskEntity.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/dto/ContractRiskDto.java`

**Interfaces:**
- Produces: `HighlightRect(int page, double x, double y, double width, double height)`
- Entity: `highlightRectsJson` (`String`, nullable)

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE raglaw_contract_risk
  ADD COLUMN highlight_rects_json TEXT NULL AFTER page_number;
```

- [ ] **Step 2: Add record + entity field**

```java
public record HighlightRect(int page, double x, double y, double width, double height) {}
```

- [ ] **Step 3: Map in `ContractRiskDto`**

```java
private List<HighlightRect> highlightRects = List.of();
```

- [ ] **Step 4: Start app — Flyway V14 applies**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add highlight_rects_json column for contract risks"
```

---

### Task 2: `PdfTextLocator` with PDFBox

**Files:**
- Create: `backend/raglaw-rag/src/main/java/com/raglaw/rag/ingest/PdfTextLocator.java`
- Create: `backend/raglaw-rag/src/test/java/com/raglaw/rag/ingest/PdfTextLocatorTest.java`

**Interfaces:**
- Produces: `List<HighlightRect> findRects(byte[] pdfBytes, String excerpt)`

- [ ] **Step 1: Write failing test with minimal PDF fixture**

Place a one-page PDF under `backend/raglaw-rag/src/test/resources/fixtures/sample-contract.pdf` or generate in test with PDFBox.

```java
@Test
void findRects_returnsNonEmptyForKnownExcerpt() throws Exception {
    byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample-contract.pdf"));
    List<HighlightRect> rects = locator.findRects(pdf, "保密义务");
    assertFalse(rects.isEmpty());
    assertEquals(1, rects.get(0).page());
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `cd backend && mvn -q -pl raglaw-rag test -Dtest=PdfTextLocatorTest`

- [ ] **Step 3: Implement locator**

Use `PDFTextStripper` with custom `writeString` collecting `TextPosition`. Match excerpt substring across positions; merge adjacent rects on same line.

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: PdfTextLocator for contract excerpt bounding boxes"
```

---

### Task 3: Wire into `ContractRiskAnalyzer`

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractRiskAnalyzer.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/contract/ContractReviewService.java` (mapping to DTO)

**Interfaces:**
- Consumes: `PdfTextLocator`, existing `PdfPageLocator` for page fallback
- Produces: risks saved with `pageNumber` + serialized `highlightRectsJson`

- [ ] **Step 1: Inject `PdfTextLocator`**

When document mime is PDF and raw bytes available from MinIO:

```java
List<HighlightRect> rects = pdfTextLocator.findRects(pdfBytes, risk.getExcerpt());
risk.setHighlightRectsJson(objectMapper.writeValueAsString(rects));
if (!rects.isEmpty()) {
    risk.setPageNumber(rects.get(0).page());
}
```

- [ ] **Step 2: Deserialize in DTO mapper**

```java
List<HighlightRect> rects = parseRects(entity.getHighlightRectsJson());
dto.setHighlightRects(rects);
```

- [ ] **Step 3: Re-analyze fixture contract — API returns `highlightRects` array**

Run contract review on digital PDF fixture; `GET /api/v1/contracts/{id}/risks` includes rects.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: populate highlight rects during contract risk analysis"
```

---

### Task 4: Frontend PDF overlay

**Files:**
- Modify: `raglaw-web/src/components/ContractPdfViewer.tsx`
- Modify: `raglaw-web/src/pages/ContractReviewPage.tsx`
- Modify: `raglaw-web/src/lib/api.ts`
- Modify: `packages/ui/src/theme/components.css`

**Interfaces:**
- Consumes: `highlightRects: HighlightRect[]`, `activeRiskId`
- Produces: visible `.rl-pdf-highlight` divs per page

- [ ] **Step 1: Add TypeScript type**

```typescript
export interface HighlightRect {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}
```

- [ ] **Step 2: Coordinate conversion helper**

```typescript
function pdfRectToCss(rect: HighlightRect, pageHeight: number) {
  return {
    left: rect.x,
    top: pageHeight - rect.y - rect.height,
    width: rect.width,
    height: rect.height,
  };
}
```

- [ ] **Step 3: Render overlay inside `Page` wrapper**

```tsx
{highlightRects
  .filter((r) => r.page === pageNumber)
  .map((r, i) => (
    <div key={i} className="rl-pdf-highlight" style={toStyle(r, pageHeight)} />
  ))}
```

- [ ] **Step 4: `ContractReviewPage` passes selected risk rects + scrolls to page**

- [ ] **Step 5: Add CSS**

```css
.rl-pdf-highlight {
  position: absolute;
  background: rgba(255, 235, 59, 0.35);
  border: 2px solid var(--rl-color-warning, #f9a825);
  pointer-events: none;
}
.rl-pdf-highlight--active {
  border-width: 3px;
}
```

- [ ] **Step 6: Build frontend**

Run: `pnpm build:web`  
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git commit -m "feat: PDF bbox highlight overlay in contract review"
```

---

### Task 5: Acceptance + edge cases

- [ ] **Step 1: Digital PDF — click risk → highlight visible on correct page**
- [ ] **Step 2: No match — `highlightRects: []`, page jump only if `pageNumber` set**
- [ ] **Step 3: Text-only contract — `ContractTextViewer` unchanged**
- [ ] **Step 4: Commit any fixes**

```bash
git commit -m "fix: PDF highlight edge cases for unmatched excerpts"
```

---

## Self-Review

| Spec requirement | Task |
|------------------|------|
| PDFBox TextPosition backend | Task 2, 3 |
| highlight_rects_json storage | Task 1 |
| react-pdf overlay (not DOM search) | Task 4 |
| OCR fallback page-only | Task 3, 5 |
| CSS in packages/ui | Task 4 |
