import { useEffect, useMemo, useRef } from 'react';
import { ContractPdfViewer, type HighlightRect } from './ContractPdfViewer';

type ContractRisk = {
  id: string;
  excerpt: string;
  suggestion?: string;
  accepted?: boolean;
  pageNumber?: number | null;
  highlightRects?: HighlightRect[];
};

type ContractTextViewerProps = {
  content: string;
  pdfUrl?: string | null;
  activeRisk?: ContractRisk | null;
  acceptedRisks?: ContractRisk[];
};

function escapeHtml(text: string) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function applyAcceptedRevisions(content: string, risks: ContractRisk[]) {
  let html = escapeHtml(content);
  for (const risk of risks) {
    if (!risk.accepted || !risk.suggestion) {
      continue;
    }
    const suggestion = escapeHtml(risk.suggestion);
    const excerpt = escapeHtml(risk.excerpt);
    const replacement = `<span class="rl-revision"><del class="rl-revision__old">${excerpt}</del><ins class="rl-revision__new">${suggestion}</ins></span>`;
    if (html.includes(suggestion)) {
      html = html.replace(suggestion, replacement);
    }
  }
  return html;
}

function highlightActiveExcerpt(html: string, excerpt?: string | null) {
  if (!excerpt) {
    return html;
  }
  const needle = escapeHtml(excerpt.length > 80 ? excerpt.slice(0, 80) : excerpt);
  const index = html.indexOf(needle);
  if (index < 0) {
    return html;
  }
  return `${html.slice(0, index)}<mark class="rl-highlight">${needle}</mark>${html.slice(index + needle.length)}`;
}

export function ContractTextViewer({
  content,
  pdfUrl,
  activeRisk,
  acceptedRisks = [],
}: ContractTextViewerProps) {
  const viewerRef = useRef<HTMLDivElement>(null);

  const html = useMemo(() => {
    const revised = applyAcceptedRevisions(content, acceptedRisks);
    if (activeRisk && !activeRisk.accepted) {
      return highlightActiveExcerpt(revised, activeRisk.excerpt);
    }
    return revised;
  }, [content, acceptedRisks, activeRisk]);

  useEffect(() => {
    const target = viewerRef.current?.querySelector('mark, .rl-revision');
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [activeRisk?.id, acceptedRisks.length]);

  if (pdfUrl) {
    return <ContractPdfViewer pdfUrl={pdfUrl} activeRisk={activeRisk} />;
  }

  return (
    <div
      ref={viewerRef}
      className="rl-contract-viewer"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
