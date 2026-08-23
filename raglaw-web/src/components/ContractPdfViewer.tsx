import { useEffect, useMemo, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

type ContractRisk = {
  id: string;
  excerpt: string;
  pageNumber?: number | null;
};

type ContractPdfViewerProps = {
  pdfUrl: string;
  activeRisk?: ContractRisk | null;
};

export function ContractPdfViewer({ pdfUrl, activeRisk }: ContractPdfViewerProps) {
  const [numPages, setNumPages] = useState(0);
  const targetPage = activeRisk?.pageNumber ?? 1;
  const pageNumber = useMemo(
    () => Math.min(Math.max(targetPage, 1), numPages || targetPage),
    [targetPage, numPages],
  );

  useEffect(() => {
    const container = document.getElementById(`rl-pdf-page-${pageNumber}`);
    container?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [pageNumber, activeRisk?.id]);

  return (
    <div className="rl-contract-pdf">
      <Document file={pdfUrl} onLoadSuccess={({ numPages: pages }) => setNumPages(pages)}>
        {numPages > 0 && (
          <Page
            key={pageNumber}
            pageNumber={pageNumber}
            width={720}
            renderAnnotationLayer
            renderTextLayer
          />
        )}
      </Document>
      {numPages > 1 && (
        <p className="rl-text-muted rl-contract-pdf__pager">
          第 {pageNumber} / {numPages} 页
          {activeRisk?.pageNumber ? ' · 已定位风险条款' : ''}
        </p>
      )}
    </div>
  );
}
