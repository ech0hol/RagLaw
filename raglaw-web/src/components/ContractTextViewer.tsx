import { useEffect, useMemo, useRef } from 'react';

type ContractRisk = {
  id: string;
  excerpt: string;
};

type ContractTextViewerProps = {
  content: string;
  pdfUrl?: string | null;
  activeRisk?: ContractRisk | null;
};

function highlightExcerpt(content: string, excerpt?: string | null) {
  if (!excerpt || !content.includes(excerpt.slice(0, 40))) {
    return content;
  }
  const needle = excerpt.length > 80 ? excerpt.slice(0, 80) : excerpt;
  const index = content.indexOf(needle);
  if (index < 0) {
    return content;
  }
  return `${content.slice(0, index)}<mark class="rl-highlight">${needle}</mark>${content.slice(index + needle.length)}`;
}

export function ContractTextViewer({ content, pdfUrl, activeRisk }: ContractTextViewerProps) {
  const viewerRef = useRef<HTMLDivElement>(null);

  const html = useMemo(
    () => highlightExcerpt(content, activeRisk?.excerpt),
    [content, activeRisk?.excerpt],
  );

  useEffect(() => {
    const mark = viewerRef.current?.querySelector('mark');
    mark?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [activeRisk?.id]);

  if (pdfUrl) {
    return <iframe className="rl-contract-viewer__pdf" src={pdfUrl} title="合同 PDF 预览" />;
  }

  return (
    <div
      ref={viewerRef}
      className="rl-contract-viewer"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
