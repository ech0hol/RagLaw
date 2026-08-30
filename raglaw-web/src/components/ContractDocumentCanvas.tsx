import { useEffect, useMemo, useRef, useState } from 'react';
import { Minus, Plus } from 'lucide-react';
import type { ContractRisk } from '../lib/api';

const MIN_FONT_SCALE = 0.85;
const MAX_FONT_SCALE = 1.4;
const FONT_SCALE_STEP = 0.1;

type ContractDocumentCanvasProps = {
  content: string;
  filename?: string;
  risks: ContractRisk[];
  activeRiskId?: string | null;
  acceptedRisks?: ContractRisk[];
  ocrUsed?: boolean;
  onRiskSelect?: (riskId: string) => void;
};

function escapeHtml(text: string) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function normalizeWhitespace(text: string) {
  return text.replace(/[\u00a0\u3000]/g, ' ').replace(/\s+/g, ' ').trim();
}

function escapeRegex(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildFlexibleWhitespacePattern(escapedExcerpt: string) {
  const parts = escapedExcerpt.split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return null;
  }
  const pattern = parts.map((part) => escapeRegex(part)).join('[\\s\u00a0\u3000]+');
  return new RegExp(pattern);
}

function stripTrailingEllipsis(excerpt: string) {
  return excerpt.endsWith('…') ? excerpt.slice(0, -1).trimEnd() : excerpt;
}

function findExcerptMatch(html: string, rawExcerpt: string): { index: number; length: number } | null {
  if (!rawExcerpt.trim()) {
    return null;
  }

  const candidates = [rawExcerpt, stripTrailingEllipsis(rawExcerpt)];
  for (const candidate of candidates) {
    const escaped = escapeHtml(candidate);
    const index = html.indexOf(escaped);
    if (index >= 0) {
      return { index, length: escaped.length };
    }
  }

  const flexibleSource = escapeHtml(stripTrailingEllipsis(rawExcerpt));
  const pattern = buildFlexibleWhitespacePattern(flexibleSource);
  if (pattern) {
    const match = pattern.exec(html);
    if (match) {
      return { index: match.index, length: match[0].length };
    }
  }

  const prefix = normalizeWhitespace(stripTrailingEllipsis(rawExcerpt));
  if (prefix.length >= 12) {
    const prefixEscaped = escapeHtml(prefix.slice(0, Math.min(40, prefix.length)));
    const prefixPattern = buildFlexibleWhitespacePattern(prefixEscaped);
    if (prefixPattern) {
      const match = prefixPattern.exec(html);
      if (match) {
        return { index: match.index, length: match[0].length };
      }
    }
  }

  return null;
}

function severityClass(severity: string) {
  const lower = severity.toLowerCase();
  if (lower === 'high' || lower === 'critical') {
    return 'rl-risk-mark--high';
  }
  if (lower === 'medium') {
    return 'rl-risk-mark--medium';
  }
  return 'rl-risk-mark--low';
}

function applyAcceptedRevisions(html: string, risks: ContractRisk[]) {
  let result = html;
  for (const risk of risks) {
    if (!risk.accepted || !risk.suggestion) {
      continue;
    }
    const suggestion = escapeHtml(risk.suggestion);
    const excerpt = escapeHtml(risk.excerpt);
    const replacement = `<span class="rl-revision"><del class="rl-revision__old">${excerpt}</del><ins class="rl-revision__new">${suggestion}</ins></span>`;
    if (result.includes(excerpt)) {
      result = result.replace(excerpt, replacement);
    }
  }
  return result;
}

function applyRiskMarks(
  html: string,
  risks: ContractRisk[],
  activeRiskId?: string | null,
) {
  const pending = risks
    .filter((risk) => !risk.accepted && risk.excerpt?.trim())
    .sort((a, b) => b.excerpt.length - a.excerpt.length);

  let result = html;
  for (const risk of pending) {
    const match = findExcerptMatch(result, risk.excerpt);
    if (!match) {
      continue;
    }
    const active = risk.id === activeRiskId;
    const excerptHtml = result.slice(match.index, match.index + match.length);
    const mark = `<span id="risk-mark-${risk.id}" class="rl-risk-mark ${severityClass(risk.severity)}${active ? ' rl-risk-mark--active' : ''}" data-risk-id="${risk.id}">${excerptHtml}</span>`;
    result = `${result.slice(0, match.index)}${mark}${result.slice(match.index + match.length)}`;
  }
  return result;
}

export function ContractDocumentCanvas({
  content,
  filename,
  risks,
  activeRiskId,
  acceptedRisks = [],
  ocrUsed = false,
  onRiskSelect,
}: ContractDocumentCanvasProps) {
  const viewerRef = useRef<HTMLDivElement>(null);
  const [fontScale, setFontScale] = useState(1);

  const html = useMemo(() => {
    const escaped = escapeHtml(content);
    const revised = applyAcceptedRevisions(escaped, acceptedRisks);
    return applyRiskMarks(revised, risks, activeRiskId);
  }, [content, acceptedRisks, risks, activeRiskId]);

  useEffect(() => {
    const target = viewerRef.current?.querySelector('.rl-risk-mark--active, .rl-revision');
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [activeRiskId, acceptedRisks.length]);

  useEffect(() => {
    const container = viewerRef.current;
    if (!container || !onRiskSelect) {
      return;
    }
    function onClick(e: MouseEvent) {
      const target = (e.target as HTMLElement).closest<HTMLElement>('[data-risk-id]');
      if (target?.dataset.riskId) {
        onRiskSelect?.(target.dataset.riskId);
      }
    }
    container.addEventListener('click', onClick);
    return () => container.removeEventListener('click', onClick);
  }, [onRiskSelect, html]);

  function changeFontScale(delta: number) {
    setFontScale((prev) => Math.min(MAX_FONT_SCALE, Math.max(MIN_FONT_SCALE, +(prev + delta).toFixed(2))));
  }

  return (
    <div className="rl-contract-doc-canvas">
      <div className="rl-contract-doc-canvas__toolbar">
        {filename && <span className="rl-contract-doc-canvas__filename">{filename}</span>}
        <div className="rl-contract-doc-canvas__toolbar-group">
          <button type="button" className="rl-icon-btn" title="缩小字号" onClick={() => changeFontScale(-FONT_SCALE_STEP)}>
            <Minus size={16} />
          </button>
          <span className="rl-contract-doc-canvas__zoom">{Math.round(fontScale * 100)}%</span>
          <button type="button" className="rl-icon-btn" title="放大字号" onClick={() => changeFontScale(FONT_SCALE_STEP)}>
            <Plus size={16} />
          </button>
        </div>
      </div>
      {ocrUsed && (
        <p className="rl-document-preview__hint">本文档由 OCR 识别，请以原件为准核对内容。</p>
      )}
      <div
        ref={viewerRef}
        className="rl-contract-doc-canvas__body rl-contract-viewer"
        style={{ fontSize: `calc(0.9rem * ${fontScale})` }}
        dangerouslySetInnerHTML={{ __html: html }}
      />
    </div>
  );
}
