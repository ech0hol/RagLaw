import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { KnowledgeGraphView } from '../components/KnowledgeGraphView';
import { api, downloadKnowledgeDocument } from '../lib/api';

type KnowledgeDocument = {
  document: { id: string; title: string; docType: string; status: string };
  chunks: { id: string; chunkIndex: number; content: string; l3Path: string }[];
  relatedDocuments?: { documentId: string; title: string; docType: string; refType: string }[];
};

export function KnowledgeDetailPage() {
  const [params] = useSearchParams();
  const docId = params.get('doc');
  const [data, setData] = useState<KnowledgeDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!docId) {
      setLoading(false);
      setError('缺少文档 ID');
      return;
    }
    void api<KnowledgeDocument>(`/api/v1/knowledge/documents/${docId}`).then((res) => {
      if (res.success) setData(res.data);
      else setError(res.error?.message ?? '加载失败');
      setLoading(false);
    });
  }, [docId]);

  if (loading) return <Spinner />;
  if (error || !data) return <PageHeader title="文档详情" subtitle={error ?? '未找到文档'} />;

  const related = data.relatedDocuments ?? [];

  return (
    <div>
      <PageHeader title={data.document.title} subtitle={`${data.document.docType} · ${data.document.status}`} />
      <p>
        <button
          type="button"
          className="rl-btn"
          onClick={() => void downloadKnowledgeDocument(data.document.id, data.document.title)}
        >
          下载原件
        </button>
      </p>
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
                  <a href={`/knowledge/documents?doc=${item.documentId}`}>
                    [{item.docType}] {item.title}
                  </a>
                  <span className="rl-text-muted"> · {item.refType}</span>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
      <div className="rl-knowledge-results">
        {data.chunks.map((chunk) => (
          <Card key={chunk.id} className="rl-knowledge-hit">
            <p className="rl-knowledge-hit__path">片段 #{chunk.chunkIndex} · {chunk.l3Path}</p>
            <p style={{ whiteSpace: 'pre-wrap' }}>{chunk.content}</p>
          </Card>
        ))}
      </div>
    </div>
  );
}
