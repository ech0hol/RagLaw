# 合同 PDF 坐标级高亮 Design Spec

**Date:** 2026-08-23  
**Status:** Approved for implementation planning  
**Scope:** H2 — bbox 存储、PDFBox 定位、react-pdf overlay

## Goal

用户在合同审查页点击风险项时，PDF 预览除跳转页码外，在对应文本区域显示高亮框；纯文本合同保持现有 `<mark>` 行为。

## Current State

- [`PdfPageLocator`](backend/raglaw-rag/src/main/java/com/raglaw/rag/ingest/PdfPageLocator.java)：页级字符串匹配
- [`ContractRiskEntity`](backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/ContractRiskEntity.java)：`page_number` only
- [`ContractPdfViewer`](raglaw-web/src/components/ContractPdfViewer.tsx)：翻页 + text layer，无 highlight

## Decisions

| Topic | Decision |
|-------|----------|
| bbox 来源 | 后端 **PDFBox `TextPosition`** 在风险分析时计算，存 JSON |
| 存储 | `raglaw_contract_risk.highlight_rects_json` TEXT，格式见下 |
| OCR PDF | 无可靠坐标时仅 `page_number`，前端降级页码跳转 |
| 前端 | `react-pdf` `Page` 上绝对定位 overlay `div`（不用 DOM text layer 搜索，避免字体差异） |
| 坐标系 | PDF 用户空间，原点左下；前端转换为 top-left CSS |

### highlight_rects_json Schema

```json
[
  {
    "page": 2,
    "x": 72.0,
    "y": 410.5,
    "width": 180.0,
    "height": 12.0
  }
]
```

多个矩形支持同一风险跨行摘录。

## Backend Changes

1. **Flyway V13**（或与 H3 合并编号）：`highlight_rects_json TEXT NULL`
2. **`PdfTextLocator`**（新）：`findRects(byte[] pdf, String excerpt) -> List<Rect>`
3. **`ContractRiskAnalyzer`**：分析时写入 `pageNumber` + `highlightRectsJson`
4. **`ContractRiskDto`**：增加 `highlightRects: Rect[]`

## Frontend Changes

1. **`ContractPdfViewer`**：接收 `highlightRects`，按页过滤，渲染 `.rl-pdf-highlight` overlay
2. **`ContractReviewPage`**：传递 DTO 字段
3. **样式**：`packages/ui/src/theme/components.css` 半透明黄色框 + active 风险加粗边框

## Error Handling

- PDF 无法解析：不写 rects，仅 page
- excerpt 未匹配：page=null，前端显示「未能定位原文」
- 扫描件 OCR 文本与 PDF 文本层不一致：不强行高亮

## Success Criteria

1. 数字 PDF 合同：点击风险 → 对应页出现可见高亮框
2. 非 PDF：文本 `<mark>` 行为不变
3. API 返回 `highlightRects` 数组；无坐标时为空数组

## Out of Scope

- 合同 PDF 导出内嵌高亮
- 手写批注工具

## Spec References

- Implementation plan: [`docs/superpowers/plans/2026-08-23-contract-pdf-highlight.md`](../plans/2026-08-23-contract-pdf-highlight.md)
