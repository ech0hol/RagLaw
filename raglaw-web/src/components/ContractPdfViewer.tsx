import { useEffect, useMemo, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import type { PageProps } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

export type HighlightRect = {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
};

type ContractRisk = {
  id: string;
  excerpt: string;
  pageNumber?: number | null;
  highlightRects?: HighlightRect[];
};

type ContractPdfViewerProps = {
  pdfUrl: string;
  activeRisk?: ContractRisk | null;
};

type PageMetrics = {
  pdfHeight: number;
  scale: number;
};

function pdfRectToStyle(rect: HighlightRect, metrics: PageMetrics) {
  return {
    left: rect.x * metrics.scale,
    top: (metrics.pdfHeight - rect.y - rect.height) * metrics.scale,
    width: rect.width * metrics.scale,
    height: rect.height * metrics.scale,
  };
}

export function ContractPdfViewer({ pdfUrl, activeRisk }: ContractPdfViewerProps) {
  const [numPages, setNumPages] = useState(0);
  const [pageMetrics, setPageMetrics] = useState<PageMetrics | null>(null);
  const targetPage = activeRisk?.highlightRects?.[0]?.page ?? activeRisk?.pageNumber ?? 1;
  const pageNumber = useMemo(
    () => Math.min(Math.max(targetPage, 1), numPages || targetPage),
    [targetPage, numPages],
  );
  const highlightRects = useMemo(
    () => activeRisk?.highlightRects?.filter((rect) => rect.page === pageNumber) ?? [],
    [activeRisk?.highlightRects, pageNumber],
  );

  useEffect(() => {
    setPageMetrics(null);
  }, [pageNumber]);

  useEffect(() => {
    const container = document.getElementById(`rl-pdf-page-${pageNumber}`);
    container?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [pageNumber, activeRisk?.id]);

  const handlePageLoad: NonNullable<PageProps['onLoadSuccess']> = (page) => {
    const viewport = page.getViewport({ scale: 1 });
    setPageMetrics({
      pdfHeight: viewport.height,
      scale: 720 / viewport.width,
    });
  };

  return (
    <div className="rl-contract-pdf">
      <Document file={pdfUrl} onLoadSuccess={({ numPages: pages }) => setNumPages(pages)}>
        {numPages > 0 && (
          <div
            id={`rl-pdf-page-${pageNumber}`}
            className="rl-contract-pdf__page-wrap"
          >
            <Page
              key={pageNumber}
              pageNumber={pageNumber}
              width={720}
              renderAnnotationLayer
              renderTextLayer
              onLoadSuccess={handlePageLoad}
            />
            {pageMetrics && highlightRects.map((rect, index) => (
              <div
                key={`${activeRisk?.id ?? 'risk'}-${index}`}
                className="rl-pdf-highlight rl-pdf-highlight--active"
                style={pdfRectToStyle(rect, pageMetrics)}
              />
            ))}
          </div>
        )}
      </Document>
      {numPages > 1 && (
        <p className="rl-text-muted rl-contract-pdf__pager">
          第 {pageNumber} / {numPages} 页
          {activeRisk?.pageNumber || activeRisk?.highlightRects?.length
            ? ' · 已定位风险条款'
            : ''}
        </p>
      )}
    </div>
  );
}
