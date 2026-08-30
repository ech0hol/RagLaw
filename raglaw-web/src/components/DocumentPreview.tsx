import { useEffect, useState } from 'react';
import { Spinner } from '@raglaw/ui';

type DocumentPreviewProps = {
  url: string | null;
  filename: string;
  mimeType?: string | null;
  extractedText?: string | null;
  loading?: boolean;
  error?: string | null;
  onDownload?: () => void;
};

type PreviewMode = 'pdf' | 'text' | 'office' | 'unsupported';

function getFileExtension(filename: string) {
  const dot = filename.lastIndexOf('.');
  return dot >= 0 ? filename.slice(dot + 1).toLowerCase() : '';
}

function resolvePreviewMode(filename: string, mimeType?: string | null): PreviewMode {
  const mime = (mimeType ?? '').toLowerCase();
  const ext = getFileExtension(filename);

  if (mime.includes('pdf') || ext === 'pdf') {
    return 'pdf';
  }
  if (mime.includes('wordprocessingml') || mime.includes('msword') || ext === 'doc' || ext === 'docx') {
    return 'office';
  }
  if (mime.startsWith('text/') || ext === 'md' || ext === 'txt' || ext === 'markdown') {
    return 'text';
  }
  return 'unsupported';
}

function looksLikeBinaryText(text: string): boolean {
  if (text.length === 0) {
    return false;
  }
  if (text.startsWith('PK')) {
    return true;
  }
  const sample = text.slice(0, 2000);
  let bad = 0;
  for (let i = 0; i < sample.length; i++) {
    const code = sample.charCodeAt(i);
    if (code === 0 || (code < 9 && code !== 0x0a && code !== 0x0d && code !== 0x09)) {
      bad++;
    }
  }
  return bad > 5 || bad / sample.length > 0.05;
}

function DownloadFallback({ message, onDownload }: { message: string; onDownload?: () => void }) {
  return (
    <div className="rl-document-preview__fallback">
      <p className="rl-text-muted">{message}</p>
      {onDownload && (
        <button type="button" className="rl-btn" onClick={onDownload}>
          下载原件
        </button>
      )}
    </div>
  );
}

export function DocumentPreview({
  url,
  filename,
  mimeType = null,
  extractedText = null,
  loading = false,
  error = null,
  onDownload,
}: DocumentPreviewProps) {
  const [textContent, setTextContent] = useState<string | null>(null);
  const [textBinary, setTextBinary] = useState(false);
  const [textLoading, setTextLoading] = useState(false);
  const previewMode = resolvePreviewMode(filename, mimeType);
  const hasExtractedText = Boolean(extractedText?.trim());

  useEffect(() => {
    if (!url || previewMode !== 'text' || hasExtractedText) {
      setTextContent(null);
      setTextBinary(false);
      return;
    }
    let cancelled = false;
    setTextLoading(true);
    void fetch(url)
      .then((res) => res.text())
      .then((text) => {
        if (!cancelled) {
          setTextBinary(looksLikeBinaryText(text));
          setTextContent(text);
          setTextLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTextContent(null);
          setTextBinary(false);
          setTextLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [url, previewMode, hasExtractedText]);

  if (loading || (previewMode === 'text' && !hasExtractedText && textLoading)) {
    return <Spinner />;
  }

  if ((previewMode === 'office' || previewMode === 'unsupported' || previewMode === 'pdf') && hasExtractedText) {
    return (
      <div>
        <p className="rl-document-preview__hint">以下为系统提取文本，版式可能与原件不同。</p>
        <pre className="rl-document-preview__text rl-contract-viewer">{extractedText}</pre>
      </div>
    );
  }

  if (error || !url) {
    return <p className="rl-text-muted">{error ?? '无法加载原件'}</p>;
  }

  if (previewMode === 'pdf') {
    return (
      <iframe className="rl-contract-viewer__pdf" src={url} title={filename} />
    );
  }

  if (previewMode === 'text') {
    if (textBinary) {
      return (
        <DownloadFallback
          message="无法在线预览此格式，请下载查看。"
          onDownload={onDownload}
        />
      );
    }
    return (
      <pre className="rl-document-preview__text">{textContent ?? ''}</pre>
    );
  }

  if (previewMode === 'office') {
    return (
      <DownloadFallback
        message="无法提取文档文本，请下载原件查看。"
        onDownload={onDownload}
      />
    );
  }

  return (
    <DownloadFallback
      message="暂不支持在线预览此格式，请下载查看。"
      onDownload={onDownload}
    />
  );
}
