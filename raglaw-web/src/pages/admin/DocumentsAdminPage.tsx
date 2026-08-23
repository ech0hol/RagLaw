import { useCallback, useEffect, useRef, useState } from 'react';
import { Select, Spinner, UploadWorkbench } from '@raglaw/ui';
import { api, uploadDocument } from '../../lib/api';

type CategoryNode = {
  id: string;
  level: number;
  name: string;
  path: string;
  children: CategoryNode[];
};

type DocumentRow = {
  id: string;
  title: string;
  status: string;
  categoryId: string;
  ingestStage?: string | null;
  ingestError?: string | null;
};

const POLLING_STAGES = new Set(['PENDING', 'PARSING', 'PARSED', 'INDEXING']);

function flattenL3(nodes: CategoryNode[]): CategoryNode[] {
  const result: CategoryNode[] = [];
  for (const node of nodes) {
    if (node.level === 3) {
      result.push(node);
    }
    if (node.children?.length) {
      result.push(...flattenL3(node.children));
    }
  }
  return result;
}

export function DocumentsAdminPage() {
  const [categories, setCategories] = useState<CategoryNode[]>([]);
  const [categoryId, setCategoryId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [recent, setRecent] = useState<DocumentRow[]>([]);
  const [pollingId, setPollingId] = useState<string | null>(null);
  const pollTimer = useRef<number | null>(null);

  const loadRecent = useCallback(() => {
    void api<DocumentRow[]>('/api/v1/admin/documents/recent?limit=10').then((res) => {
      if (res.success) {
        setRecent(res.data);
      }
    });
  }, []);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/admin/categories').then((res) => {
      if (res.success) {
        const l3 = flattenL3(res.data);
        setCategories(l3);
        if (l3[0]) {
          setCategoryId(l3[0].id);
        }
      }
    });
    loadRecent();
  }, [loadRecent]);

  const pollDocument = useCallback(async (documentId: string) => {
    const res = await api<DocumentRow>(`/api/v1/admin/documents/${documentId}`);
    if (!res.success) {
      return;
    }
    setRecent((prev) => {
      const others = prev.filter((doc) => doc.id !== documentId);
      return [res.data, ...others].slice(0, 10);
    });
    if (!res.data.ingestStage || !POLLING_STAGES.has(res.data.ingestStage)) {
      setPollingId(null);
      setMessage(`入库完成：${res.data.title}（${res.data.status}${res.data.ingestStage ? ` / ${res.data.ingestStage}` : ''}）`);
    }
  }, []);

  useEffect(() => {
    if (!pollingId) {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
      return;
    }
    void pollDocument(pollingId);
    pollTimer.current = window.setInterval(() => {
      void pollDocument(pollingId);
    }, 2000);
    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
    };
  }, [pollingId, pollDocument]);

  async function startUpload() {
    if (!file || !categoryId) return;
    setUploading(true);
    setMessage(null);
    setError(null);
    try {
      const upload = await uploadDocument(categoryId, file);
      if (!upload.success) {
        throw new Error(upload.error?.message ?? '上传失败');
      }
      const doc = upload.data;
      setRecent((prev) => [doc, ...prev.filter((item) => item.id !== doc.id)].slice(0, 10));
      if (doc.ingestStage && POLLING_STAGES.has(doc.ingestStage)) {
        setPollingId(doc.id);
        setMessage(`已上传：${doc.title}，正在入库（${doc.ingestStage}）…`);
      } else {
        setMessage(`已入库：${doc.title}（${doc.status}）`);
      }
      setFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setUploading(false);
    }
  }

  async function retryIngest(documentId: string) {
    setError(null);
    const res = await api<DocumentRow>(`/api/v1/admin/documents/${documentId}/retry-ingest`, { method: 'POST' });
    if (!res.success) {
      setError(res.error?.message ?? '重试失败');
      return;
    }
    setPollingId(documentId);
    setMessage(`已重新提交入库：${res.data.title}`);
    loadRecent();
  }

  const categoryOptions = categories.map((cat) => ({
    value: cat.id,
    label: cat.name,
  }));

  return (
    <div className="rl-chat-page rl-workbench-page">
      <div className="rl-page-center">
        <UploadWorkbench
          title="文档入库"
          subtitle="上传 Markdown 法规/案例；RabbitMQ 开启时自动异步入库"
          accept=".md,text/markdown"
          formatHint="支持 Markdown（.md）文件"
          submitLabel="上传并入库"
          loading={uploading}
          disabled={!categoryId}
          file={file}
          onFileChange={setFile}
          onSubmit={() => void startUpload()}
          error={error}
          footerSlot={
            categoryOptions.length > 0 ? (
              <Select
                value={categoryId}
                onChange={setCategoryId}
                options={categoryOptions}
              />
            ) : undefined
          }
        />
        {message && <p className="rl-form-hint" style={{ textAlign: 'center', marginTop: '1rem' }}>{message}</p>}

        {recent.length > 0 && (
          <div style={{ marginTop: '2rem' }}>
            <h3 className="rl-section-title">最近入库</h3>
            <div className="rl-contract-history-list">
              {recent.map((doc) => (
                <div key={doc.id} className="rl-contract-history-item">
                  <p className="rl-contract-history-item__title">{doc.title}</p>
                  <p className="rl-contract-history-item__meta">
                    状态 {doc.status}
                    {doc.ingestStage ? ` · 阶段 ${doc.ingestStage}` : ''}
                    {doc.ingestError ? ` · 错误 ${doc.ingestError}` : ''}
                  </p>
                  {doc.ingestStage === 'FAILED' && (
                    <button
                      type="button"
                      className="rl-btn rl-btn--sm"
                      onClick={() => void retryIngest(doc.id)}
                    >
                      重试入库
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
        {uploading && <Spinner />}
      </div>
    </div>
  );
}
