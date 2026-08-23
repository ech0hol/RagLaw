import { useEffect, useState } from 'react';
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
};

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
  }, []);

  async function startIngest() {
    if (!file || !categoryId) return;
    setUploading(true);
    setMessage(null);
    setError(null);
    try {
      const upload = await uploadDocument(categoryId, file);
      if (!upload.success) {
        throw new Error(upload.error?.message ?? '上传失败');
      }
      const ingest = await api<DocumentRow>(`/api/v1/admin/documents/${upload.data.id}/ingest`, {
        method: 'POST',
      });
      if (!ingest.success) {
        throw new Error(ingest.error?.message ?? '入库失败');
      }
      setRecent((prev) => [ingest.data, ...prev].slice(0, 10));
      setMessage(`已入库：${ingest.data.title}（${ingest.data.status}）`);
      setFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setUploading(false);
    }
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
          subtitle="上传 Markdown 法规/案例并触发分块索引"
          accept=".md,text/markdown"
          formatHint="支持 Markdown（.md）文件"
          submitLabel="开始入库"
          loading={uploading}
          disabled={!categoryId}
          file={file}
          onFileChange={setFile}
          onSubmit={() => void startIngest()}
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
                    状态 {doc.status} · 类目 {doc.categoryId}
                  </p>
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
