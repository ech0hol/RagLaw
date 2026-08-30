import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Minus, Plus } from 'lucide-react';
import { Document, Page, pdfjs } from 'react-pdf';
import type { PageProps } from 'react-pdf';
import type { ContractRisk } from '../lib/api';
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

type ContractPdfViewerProps = {
  pdfUrl: string;
  filename?: string;
  risks: ContractRisk[];
  activeRiskId?: string | null;
  onRiskSelect?: (riskId: string) => void;
};

type PageMetrics = {
  pdfHeight: number;
  scale: number;
};

type PageMark = {
  riskId: string;
  severity: string;
  rect: HighlightRect;
  index: number;
};

const BASE_WIDTH = 720;
const MIN_ZOOM = 0.8;
const MAX_ZOOM = 1.6;
const ZOOM_STEP = 0.1;
const UNDERLINE_HEIGHT = 3;

function severityUnderlineClass(severity: string) {
  const lower = severity.toLowerCase();
  if (lower === 'high' || lower === 'critical') {
    return 'rl-pdf-underline--high';
  }
  if (lower === 'medium') {
    return 'rl-pdf-underline--medium';
  }
  return 'rl-pdf-underline--low';
}

function pdfRectToBoxStyle(rect: HighlightRect, metrics: PageMetrics) {
  return {
    left: rect.x * metrics.scale,
    top: (metrics.pdfHeight - rect.y - rect.height) * metrics.scale,
    width: rect.width * metrics.scale,
    height: rect.height * metrics.scale,
  };
}

function pdfRectToUnderlineStyle(rect: HighlightRect, metrics: PageMetrics) {
  const box = pdfRectToBoxStyle(rect, metrics);
  return {
    left: box.left,
    top: box.top + box.height - UNDERLINE_HEIGHT,
    width: box.width,
    height: UNDERLINE_HEIGHT,
  };
}

function withPdfViewerParams(url: string) {
  const params = 'navpanes=0&toolbar=1&view=FitH';
  return url.includes('#') ? url : `${url}#${params}`;
}

function NativePdfViewer({ pdfUrl, filename }: { pdfUrl: string; filename?: string }) {
  return (
    <div className="rl-contract-pdf rl-contract-pdf--native">
      <iframe
        className="rl-contract-viewer__pdf"
        src={withPdfViewerParams(pdfUrl)}
        title={filename ?? '合同 PDF'}
      />
    </div>
  );
}

export function ContractPdfViewer({
  pdfUrl,
  filename,
  risks,
  activeRiskId,
  onRiskSelect,
}: ContractPdfViewerProps) {
  const [numPages, setNumPages] = useState(0);
  const [pageMetrics, setPageMetrics] = useState<PageMetrics | null>(null);
  const [viewPage, setViewPage] = useState(1);
  const [zoom, setZoom] = useState(1);
  const [loadFailed, setLoadFailed] = useState(false);

  const pageWidth = Math.round(BASE_WIDTH * zoom);

  const activeRisk = useMemo(
    () => risks.find((risk) => risk.id === activeRiskId) ?? null,
    [risks, activeRiskId],
  );

  const targetPage = activeRisk?.highlightRects?.[0]?.page ?? activeRisk?.pageNumber ?? null;

  useEffect(() => {
    setLoadFailed(false);
    setNumPages(0);
    setViewPage(1);
  }, [pdfUrl]);

  useEffect(() => {
    if (targetPage != null && numPages > 0) {
      setViewPage(Math.min(Math.max(targetPage, 1), numPages));
    }
  }, [activeRiskId, targetPage, numPages]);

  const pageNumber = Math.min(Math.max(viewPage, 1), numPages || 1);

  const pageMarks = useMemo(() => {
    const marks: PageMark[] = [];
    for (const risk of risks) {
      if (risk.accepted) {
        continue;
      }
      const rects = risk.highlightRects?.filter((rect) => rect.page === pageNumber) ?? [];
      rects.forEach((rect, index) => {
        marks.push({ riskId: risk.id, severity: risk.severity, rect, index });
      });
    }
    return marks;
  }, [risks, pageNumber]);

  useEffect(() => {
    setPageMetrics(null);
  }, [pageNumber, zoom]);

  useEffect(() => {
    const underline = document.getElementById(`rl-pdf-underline-${activeRiskId}`);
    if (underline) {
      underline.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const container = document.getElementById(`rl-pdf-page-${pageNumber}`);
    container?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [pageNumber, activeRiskId]);

  const handlePageLoad: NonNullable<PageProps['onLoadSuccess']> = (page) => {
    const viewport = page.getViewport({ scale: 1 });
    setPageMetrics({
      pdfHeight: viewport.height,
      scale: pageWidth / viewport.width,
    });
  };

  function changeZoom(delta: number) {
    setZoom((prev) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, +(prev + delta).toFixed(1))));
  }

  if (loadFailed) {
    return <NativePdfViewer pdfUrl={pdfUrl} filename={filename} />;
  }

  return (
    <div className="rl-contract-pdf">
      <div className="rl-contract-pdf__toolbar">
        <div className="rl-contract-pdf__toolbar-group">
          <button
            type="button"
            className="rl-icon-btn"
            title="上一页"
            disabled={pageNumber <= 1}
            onClick={() => setViewPage((p) => Math.max(1, p - 1))}
          >
            <ChevronLeft size={16} />
          </button>
          <span className="rl-contract-pdf__pager-text">
            {numPages > 0 ? `${pageNumber} / ${numPages}` : '—'}
          </span>
          <button
            type="button"
            className="rl-icon-btn"
            title="下一页"
            disabled={numPages === 0 || pageNumber >= numPages}
            onClick={() => setViewPage((p) => Math.min(numPages, p + 1))}
          >
            <ChevronRight size={16} />
          </button>
        </div>
        {filename && <span className="rl-contract-pdf__filename">{filename}</span>}
        <div className="rl-contract-pdf__toolbar-group">
          <button type="button" className="rl-icon-btn" title="缩小" onClick={() => changeZoom(-ZOOM_STEP)}>
            <Minus size={16} />
          </button>
          <span className="rl-contract-pdf__zoom">{Math.round(zoom * 100)}%</span>
          <button type="button" className="rl-icon-btn" title="放大" onClick={() => changeZoom(ZOOM_STEP)}>
            <Plus size={16} />
          </button>
        </div>
      </div>
      <div className="rl-contract-pdf__canvas">
        <Document
          file={pdfUrl}
          onLoadSuccess={({ numPages: pages }) => setNumPages(pages)}
          onLoadError={() => setLoadFailed(true)}
        >
          {numPages > 0 && (
            <div id={`rl-pdf-page-${pageNumber}`} className="rl-contract-pdf__page-wrap">
              <Page
                key={`${pageNumber}-${pageWidth}`}
                pageNumber={pageNumber}
                width={pageWidth}
                renderAnnotationLayer
                renderTextLayer
                onLoadSuccess={handlePageLoad}
              />
              {pageMetrics && pageMarks.map((mark) => {
                const isActive = mark.riskId === activeRiskId;
                return (
                  <button
                    key={`${mark.riskId}-${mark.index}`}
                    id={isActive ? `rl-pdf-underline-${mark.riskId}` : undefined}
                    type="button"
                    className={[
                      'rl-pdf-underline',
                      severityUnderlineClass(mark.severity),
                      isActive && 'rl-pdf-underline--active',
                    ].filter(Boolean).join(' ')}
                    style={pdfRectToUnderlineStyle(mark.rect, pageMetrics)}
                    title="查看风险"
                    onClick={() => onRiskSelect?.(mark.riskId)}
                  />
                );
              })}
            </div>
          )}
        </Document>
      </div>
    </div>
  );
}
