import { useEffect, useMemo, useState } from 'react';
import { SlidePanel } from './SlidePanel';
import type { ChatReference } from './ReferenceList';
import {
  articleLabelsMismatch,
  extractCitedArticlesByIndex,
} from './citationMarkdownUtils';
import { needsRemoteExcerpt, referenceSnippet } from '../lib/referenceSnippet';
import { filterUserVisibleReferences, isCatalogReference } from '../lib/referenceUtils';

type ReferenceMaterialsPanelProps = {
  open: boolean;
  onClose: () => void;
  references: ChatReference[];
  content?: string;
  webSearchAttempted?: boolean;
  catalogMode?: boolean;
  fetchDocumentExcerpt?: (documentId: string, anchors: string) => Promise<string | null>;
};

function groupReferences(references: ChatReference[]) {
  const knowledge = references.filter((ref) => ref.source !== 'web');
  const web = references.filter((ref) => ref.source === 'web');
  return { knowledge, web };
}

function isExternalUrl(path: string) {
  return path.startsWith('http://') || path.startsWith('https://');
}

function ReferenceMaterialItem({
  reference,
  citedArticleLabel,
  fetchDocumentExcerpt,
}: {
  reference: ChatReference;
  citedArticleLabel?: string;
  fetchDocumentExcerpt?: (documentId: string, anchors: string) => Promise<string | null>;
}) {
  const title = reference.title ?? reference.path;
  const displayArticleLabel = citedArticleLabel ?? reference.articleLabel;
  const [snippet, setSnippet] = useState(() => referenceSnippet(reference, citedArticleLabel));
  const [loadingRemote, setLoadingRemote] = useState(false);
  const [remoteLoaded, setRemoteLoaded] = useState(false);
  const mismatch = citedArticleLabel
    ? articleLabelsMismatch(citedArticleLabel, reference.articleLabel)
    : false;
  const excerptMismatch = citedArticleLabel
    ? articleLabelsMismatch(citedArticleLabel, snippet)
    : false;
  const isWeb = reference.source === 'web' && isExternalUrl(reference.path);
  const catalog = isCatalogReference(reference.chunkId);

  useEffect(() => {
    const local = referenceSnippet(reference, citedArticleLabel);
    setSnippet(local);
    setRemoteLoaded(false);

    const shouldFetchRemote = needsRemoteExcerpt(reference, citedArticleLabel)
      || (local && citedArticleLabel && articleLabelsMismatch(citedArticleLabel, local));

    if (!shouldFetchRemote) {
      setLoadingRemote(false);
      return;
    }
    if (!fetchDocumentExcerpt || !reference.documentId || !citedArticleLabel) {
      setLoadingRemote(false);
      return;
    }

    let cancelled = false;
    setLoadingRemote(true);
    void fetchDocumentExcerpt(reference.documentId, citedArticleLabel).then((fetched) => {
      if (cancelled) return;
      setLoadingRemote(false);
      setRemoteLoaded(true);
      if (fetched) {
        setSnippet(fetched);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [citedArticleLabel, fetchDocumentExcerpt, reference]);

  return (
    <article className="rl-reference-material-item" data-testid="reference-material-item">
      <h3 className="rl-reference-material-item__title">
        {reference.index}、{title}
      </h3>
      <p className="rl-reference-material-item__snippet-label">
        {catalog ? '文档条目' : '引用片段'}
        {displayArticleLabel ? ` · ${displayArticleLabel}` : ''}
        {mismatch && reference.articleLabel && citedArticleLabel ? (
          <span className="rl-reference-material-item__slice-label">
            {' '}
            （切片：{reference.articleLabel}）
          </span>
        ) : null}
      </p>
      {(mismatch || excerptMismatch) && remoteLoaded && (
        <p className="rl-reference-material-item__warning">
          正文引用与检索切片条号不一致，摘录已按正文条号展示。
        </p>
      )}
      {loadingRemote ? (
        <p className="rl-reference-material-item__snippet rl-text-muted">正在加载摘录…</p>
      ) : snippet ? (
        <p className="rl-reference-material-item__snippet">{snippet}</p>
      ) : (
        <p className="rl-reference-material-item__snippet rl-text-muted">暂无摘录</p>
      )}
      <p className="rl-reference-material-item__meta">
        {reference.documentId ? (
          <a href={`/knowledge/documents?doc=${reference.documentId}`}>{reference.path}</a>
        ) : isWeb ? (
          <a href={reference.path} target="_blank" rel="noopener noreferrer">{reference.path}</a>
        ) : (
          reference.path
        )}
      </p>
    </article>
  );
}

export function ReferenceMaterialsPanel({
  open,
  onClose,
  references,
  content = '',
  webSearchAttempted = false,
  catalogMode = false,
  fetchDocumentExcerpt,
}: ReferenceMaterialsPanelProps) {
  const visible = filterUserVisibleReferences(references);
  const { knowledge, web } = groupReferences(visible);
  const citedArticlesByIndex = useMemo(
    () => extractCitedArticlesByIndex(content),
    [content],
  );
  const showWebSection = web.length > 0 || webSearchAttempted;
  const resolvedCatalogMode = catalogMode || knowledge.some((ref) => isCatalogReference(ref.chunkId));

  return (
    <SlidePanel
      open={open}
      onClose={onClose}
      title="文章"
      side="right"
      anchor="sidebar"
      width="380px"
    >
      {knowledge.length > 0 && (
        <section className="rl-reference-materials-section">
          <h2 className="rl-reference-materials-section__title">检索依据</h2>
          <p className="rl-reference-materials-section__hint">
            {resolvedCatalogMode
              ? '以下为知识库可查询文档目录，非条文切片。'
              : '以下为知识库检索片段；正文带 [n] 的引用与之对应。'}
          </p>
          <div className="rl-reference-materials-list">
            {knowledge.map((ref) => (
              <ReferenceMaterialItem
                key={ref.chunkId}
                reference={ref}
                citedArticleLabel={citedArticlesByIndex.get(ref.index)}
                fetchDocumentExcerpt={fetchDocumentExcerpt}
              />
            ))}
          </div>
        </section>
      )}
      {showWebSection && (
      <section className="rl-reference-materials-section">
        <h2 className="rl-reference-materials-section__title">联网资料</h2>
        {web.length === 0 ? (
          <p className="rl-text-muted">本次已尝试联网检索，但未找到可用结果</p>
        ) : (
          <div className="rl-reference-materials-list">
            {web.map((ref) => (
              <ReferenceMaterialItem key={ref.chunkId} reference={ref} />
            ))}
          </div>
        )}
      </section>
      )}
    </SlidePanel>
  );
}
