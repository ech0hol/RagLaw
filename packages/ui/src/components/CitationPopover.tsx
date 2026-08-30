import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { articleLabelsMismatch } from './citationMarkdownUtils';
import { needsRemoteExcerpt, referenceSnippet } from '../lib/referenceSnippet';
import type { ChatReference } from './ReferenceList';

type CitationPopoverProps = {
  reference: ChatReference;
  anchorRect: DOMRect;
  articleLabel?: string;
  onClose: () => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
  fetchDocumentTitle?: (documentId: string) => Promise<string | null>;
  fetchDocumentExcerpt?: (documentId: string, anchors: string) => Promise<string | null>;
};

export function CitationPopover({
  reference,
  anchorRect,
  articleLabel,
  onClose,
  onMouseEnter,
  onMouseLeave,
  fetchDocumentTitle,
  fetchDocumentExcerpt,
}: CitationPopoverProps) {
  const popoverRef = useRef<HTMLDivElement>(null);
  const [title, setTitle] = useState(reference.title ?? reference.path);
  const [body, setBody] = useState(() => referenceSnippet(reference, articleLabel));
  const [loadingRemote, setLoadingRemote] = useState(false);
  const [remoteLoaded, setRemoteLoaded] = useState(false);
  const mismatch = articleLabel
    ? articleLabelsMismatch(articleLabel, body)
    : false;

  useEffect(() => {
    if (!fetchDocumentTitle || !reference.documentId) return;
    let cancelled = false;
    void fetchDocumentTitle(reference.documentId).then((fetched) => {
      if (!cancelled && fetched) setTitle(fetched);
    });
    return () => {
      cancelled = true;
    };
  }, [fetchDocumentTitle, reference.documentId, reference.title, reference.path]);

  useEffect(() => {
    const local = referenceSnippet(reference, articleLabel);
    setBody(local);
    setRemoteLoaded(false);

    const shouldFetchRemote = needsRemoteExcerpt(reference, articleLabel)
      || (local && articleLabel && articleLabelsMismatch(articleLabel, local));

    if (!shouldFetchRemote) {
      setLoadingRemote(false);
      return;
    }
    if (!fetchDocumentExcerpt || !reference.documentId || !articleLabel) {
      setLoadingRemote(false);
      return;
    }

    let cancelled = false;
    setLoadingRemote(true);
    void fetchDocumentExcerpt(reference.documentId, articleLabel).then((fetched) => {
      if (cancelled) return;
      setLoadingRemote(false);
      setRemoteLoaded(true);
      if (fetched) {
        setBody(fetched);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [articleLabel, fetchDocumentExcerpt, reference]);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const top = Math.min(anchorRect.bottom + 8, window.innerHeight - 320);
  const left = Math.min(Math.max(12, anchorRect.left), window.innerWidth - 440);

  return createPortal(
    <div
      ref={popoverRef}
      className="rl-citation-popover rl-select-menu--enter"
      style={{ top, left }}
      role="tooltip"
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <div className="rl-citation-popover__header">
        <p className="rl-citation-popover__title">{title}</p>
        <span className="rl-citation-popover__badge">已收录</span>
      </div>
      <p className="rl-citation-popover__meta">{reference.path}</p>
      {articleLabel && (
        <p className="rl-citation-popover__article">{articleLabel}</p>
      )}
      {mismatch && remoteLoaded && (
        <p className="rl-citation-popover__warning">
          摘录条号与正文引用不一致，请以正文条号为准。
        </p>
      )}
      <div className="rl-citation-popover__body">
        <p className="rl-citation-popover__excerpt">
          {loadingRemote ? '正在加载摘录…' : body || '暂无摘录'}
        </p>
      </div>
    </div>,
    document.body,
  );
}
