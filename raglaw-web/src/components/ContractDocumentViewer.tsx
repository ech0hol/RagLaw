import { ArrowLeft } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import type { ContractRisk } from '../lib/api';
import { ContractDocumentCanvas } from './ContractDocumentCanvas';
import { ContractPdfViewer } from './ContractPdfViewer';

type DocViewTab = 'text' | 'original';

type ContractDocPanelTopbarProps = {
  onBack?: () => void;
  children?: ReactNode;
};

export function ContractDocPanelTopbar({ onBack, children }: ContractDocPanelTopbarProps) {
  if (!onBack && !children) {
    return null;
  }
  return (
    <div className="rl-contract-doc-panel__topbar">
      {onBack && (
        <button
          type="button"
          className="rl-contract-doc-panel__back"
          onClick={onBack}
          title="返回"
        >
          <ArrowLeft size={16} />
          返回
        </button>
      )}
      {children}
    </div>
  );
}

type ContractDocumentViewerProps = {
  content: string;
  filename?: string;
  isPdf: boolean;
  isImage?: boolean;
  pdfUrl?: string | null;
  fileUrl?: string | null;
  risks: ContractRisk[];
  activeRiskId?: string | null;
  acceptedRisks?: ContractRisk[];
  ocrUsed?: boolean;
  onRiskSelect?: (riskId: string) => void;
  onBack?: () => void;
  defaultDocTab?: DocViewTab;
};

export function ContractDocumentViewer({
  content,
  filename,
  isPdf,
  isImage = false,
  pdfUrl,
  fileUrl,
  risks,
  activeRiskId,
  acceptedRisks = [],
  ocrUsed = false,
  onRiskSelect,
  onBack,
  defaultDocTab = 'text',
}: ContractDocumentViewerProps) {
  const [docTab, setDocTab] = useState<DocViewTab>(defaultDocTab);

  useEffect(() => {
    setDocTab(defaultDocTab);
  }, [defaultDocTab]);

  const originalUrl = isPdf ? pdfUrl : fileUrl;
  const showTabs = Boolean(originalUrl && (isPdf || isImage));

  function renderTextView() {
    return (
      <ContractDocumentCanvas
        content={content}
        filename={filename}
        risks={risks}
        activeRiskId={activeRiskId}
        acceptedRisks={acceptedRisks}
        ocrUsed={ocrUsed}
        onRiskSelect={onRiskSelect}
      />
    );
  }

  function renderOriginalView() {
    if (isPdf && pdfUrl) {
      return (
        <ContractPdfViewer
          pdfUrl={pdfUrl}
          filename={filename}
          risks={risks}
          activeRiskId={activeRiskId}
          onRiskSelect={onRiskSelect}
        />
      );
    }
    if (isImage && fileUrl) {
      return (
        <div className="rl-contract-doc-original">
          {filename && <p className="rl-contract-doc-original__filename">{filename}</p>}
          <div className="rl-contract-doc-original__frame">
            <img src={fileUrl} alt={filename ?? '合同原图'} className="rl-contract-doc-original__image" />
          </div>
        </div>
      );
    }
    return renderTextView();
  }

  const tabList = showTabs ? (
    <div className="rl-contract-doc-view__tabs" role="tablist" aria-label="文档视图">
      <button
        type="button"
        role="tab"
        aria-selected={docTab === 'text'}
        className={['rl-contract-doc-view__tab', docTab === 'text' && 'rl-contract-doc-view__tab--active'].filter(Boolean).join(' ')}
        onClick={() => setDocTab('text')}
      >
        审阅文本
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={docTab === 'original'}
        className={['rl-contract-doc-view__tab', docTab === 'original' && 'rl-contract-doc-view__tab--active'].filter(Boolean).join(' ')}
        onClick={() => setDocTab('original')}
      >
        {isImage ? '原图' : '原文件'}
      </button>
    </div>
  ) : null;

  return (
    <div className="rl-contract-doc-view">
      <ContractDocPanelTopbar onBack={onBack}>
        {tabList}
      </ContractDocPanelTopbar>
      <div className="rl-contract-doc-view__body">
        {showTabs && docTab === 'original' ? renderOriginalView() : renderTextView()}
      </div>
    </div>
  );
}
