import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { DocumentPreview } from '../components/DocumentPreview';
import { KnowledgeGraphView } from '../components/KnowledgeGraphView';
import { useAuthedBlobUrl } from '../hooks/useAuthedBlobUrl';
import { api, downloadKnowledgeDocument } from '../lib/api';
import { buildFullTextFromChunks } from '../lib/documentText';
import { buildKnowledgeListPath, parseKnowledgeSearchParams } from '../lib/knowledgeSearch';
import { formatDocTypeLabel, formatLocalDate } from '../lib/formatDate';

type KnowledgeDocument = {
  document: {
    id: string;
    title: string;
    originalFilename: string;
    docType: string;
    status: string;
    minioKey?: string | null;
    createdAt?: string | null;
    effectiveDate?: string | null;
  };
  chunks: {
    id: string;
    chunkIndex: number;
    content: string;
    l3Path: string;
    chunkLevel?: string | null;
  }[];
  relatedDocuments?: { documentId: string; title: string; docType: string; refType: string }[];
};

function basenameFromStorageKey(key?: string | null): string | null {
  if (!key) {
    return null;
  }
  const slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
  return slash >= 0 ? key.slice(slash + 1) : key;
}

function resolveOriginalFilename(doc: KnowledgeDocument['document']): string {
  if (doc.originalFilename) {
    return doc.originalFilename;
  }
  const fromKey = basenameFromStorageKey(doc.minioKey);
  if (fromKey) {
    return fromKey;
  }
  return `${doc.title}.md`;
}

function knowledgeListPath(docType?: string): string {
  if (docType === 'CASE' || docType === 'STATUTE') {
    return `/knowledge/statutes?docType=${docType}`;
  }
  return '/knowledge/statutes';
}

function resolveBackPath(from: string | null, searchParams: URLSearchParams, docType?: string): string {
  if (from === 'admin') {
    return '/admin/documents';
  }
  if (from === 'search') {
    const parsed = parseKnowledgeSearchParams(searchParams);
    if (parsed.q.trim()) {
      return buildKnowledgeListPath(parsed);
    }
  }
  return knowledgeListPath(docType);
}

function buildDetailSubtitle(doc: KnowledgeDocument['document'], originalFilename: string): string {
  const typeLabel = formatDocTypeLabel(doc.docType);
  const ingested = formatLocalDate(doc.createdAt);
  const effective = doc.effectiveDate ? formatLocalDate(doc.effectiveDate) : '未识别';
  return `${typeLabel} · ${doc.status} · ${originalFilename} · 入库 ${ingested} · 生效 ${effective}`;
}

export function KnowledgeDetailPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const docId = params.get('doc');
  const from = params.get('from');
  const [data, setData] = useState<KnowledgeDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const previewPath = docId ? `/api/v1/knowledge/documents/${docId}/preview` : null;
  const {
    url: previewUrl,
    mimeType: previewMimeType,
    loading: previewLoading,
    error: previewError,
  } = useAuthedBlobUrl(previewPath);

  useEffect(() => {
    if (params.get('tab')) {
      const next = new URLSearchParams(params);
      next.delete('tab');
      setParams(next, { replace: true });
    }
  }, [params, setParams]);

  useEffect(() => {
    if (!docId) {
      setLoading(false);
      setError('缺少文档 ID');
      return;
    }
    void api<KnowledgeDocument>(`/api/v1/knowledge/documents/${docId}`).then((res) => {
      if (res.success) {
        setData(res.data);
      } else {
        setError(res.error?.message ?? '加载失败');
      }
      setLoading(false);
    });
  }, [docId]);

  if (loading) return <Spinner />;

  if (error || !data) {
    return (
      <div className="rl-page-center">
        <button
          type="button"
          className="rl-knowledge-detail__back"
          onClick={() => navigate(resolveBackPath(from, params))}
        >
          <ArrowLeft size={16} />
          返回
        </button>
        <PageHeader title="文档详情" subtitle={error ?? '未找到文档'} />
      </div>
    );
  }

  const related = data.relatedDocuments ?? [];
  const originalFilename = resolveOriginalFilename(data.document);
  const extractedText = buildFullTextFromChunks(data.chunks);
  const backPath = resolveBackPath(from, params, data.document.docType);

  return (
    <div className="rl-page-center">
      <button
        type="button"
        className="rl-knowledge-detail__back"
        onClick={() => navigate(backPath)}
      >
        <ArrowLeft size={16} />
        返回
      </button>
      <PageHeader
        title={data.document.title}
        subtitle={buildDetailSubtitle(data.document, originalFilename)}
      />
      <Card className="rl-knowledge-detail-panel">
        <div className="rl-knowledge-detail-panel__toolbar">
          <button
            type="button"
            className="rl-btn rl-btn--sm"
            onClick={() => void downloadKnowledgeDocument(data.document.id, originalFilename)}
          >
            下载原件
          </button>
        </div>
        <DocumentPreview
          url={previewUrl}
          filename={originalFilename}
          mimeType={previewMimeType}
          extractedText={extractedText}
          loading={previewLoading}
          error={previewError}
          onDownload={() => void downloadKnowledgeDocument(data.document.id, originalFilename)}
        />
      </Card>

      {related.length > 0 && (
        <>
          <h3>知识图谱</h3>
          <KnowledgeGraphView
            documentId={data.document.id}
            title={data.document.title}
            docType={data.document.docType}
            relatedDocuments={related}
          />
          <Card className="rl-knowledge-related">
            <h3>关联文档</h3>
            <ul>
              {related.map((item) => (
                <li key={item.documentId}>
                  <Link to={`/knowledge/documents?doc=${item.documentId}`}>
                    [{item.docType}] {item.title}
                  </Link>
                  <span className="rl-text-muted"> · {item.refType}</span>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
    </div>
  );
}
