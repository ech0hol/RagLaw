import { FormEvent, useEffect, useState } from 'react';
import { Button, Card, MainHeader, Select } from '@raglaw/ui';
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

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!file || !categoryId) {
      return;
    }
    setUploading(true);
    setMessage(null);
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
      setMessage(err instanceof Error ? err.message : '操作失败');
    } finally {
      setUploading(false);
    }
  }

  return (
    <div>
      <MainHeader title="文档入库" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        上传 Markdown 法规/案例并触发分块索引
      </p>
      <Card>
        <form className="rl-admin-form" onSubmit={(e) => void onSubmit(e)}>
          <Select
            label="L3 类目"
            value={categoryId}
            onChange={setCategoryId}
            options={categories.map((cat) => ({
              value: cat.id,
              label: `${cat.name} (${cat.path})`,
            }))}
          />
          <label className="rl-field">
            <span className="rl-field__label">Markdown 文件</span>
            <input
              className="rl-input"
              type="file"
              accept=".md,text/markdown"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              required
            />
          </label>
          <Button type="submit" disabled={uploading || !file}>
            {uploading ? '处理中…' : '上传并入库'}
          </Button>
        </form>
        {message && <p className="rl-form-hint">{message}</p>}
      </Card>
      {recent.length > 0 && (
        <Card>
          <h3 className="rl-section-title">最近入库</h3>
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th>标题</th>
                  <th>状态</th>
                  <th>类目 ID</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((doc) => (
                  <tr key={doc.id}>
                    <td>{doc.title}</td>
                    <td>{doc.status}</td>
                    <td><code>{doc.categoryId}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
