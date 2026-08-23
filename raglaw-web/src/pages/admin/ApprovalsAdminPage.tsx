import { useEffect, useState } from 'react';
import { Badge, Button, Card, MainHeader } from '@raglaw/ui';
import { api } from '../../lib/api';

type DocumentRow = {
  id: string;
  title: string;
  status: string;
  docType: string;
  categoryId: string;
};

export function ApprovalsAdminPage() {
  const [pending, setPending] = useState<DocumentRow[]>([]);
  const [message, setMessage] = useState<string | null>(null);

  async function loadPending() {
    const res = await api<DocumentRow[]>('/api/v1/admin/approvals/pending');
    if (res.success) {
      setPending(res.data);
    }
  }

  useEffect(() => {
    void loadPending();
  }, []);

  async function approve(documentId: string) {
    setMessage(null);
    const res = await api<DocumentRow>(`/api/v1/admin/approvals/${documentId}/approve`, {
      method: 'POST',
    });
    if (!res.success) {
      setMessage(res.error?.message ?? '审批失败');
      return;
    }
    setMessage(`已批准：${res.data.title}`);
    await loadPending();
  }

  return (
    <div>
      <MainHeader title="案例审批" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        批准后的案例文档方可参与检索
      </p>
      {message && <p className="rl-form-hint">{message}</p>}
      <Card>
        {pending.length === 0 ? (
          <p className="rl-muted">暂无待审批文档</p>
        ) : (
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th>标题</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {pending.map((doc) => (
                  <tr key={doc.id}>
                    <td>{doc.title}</td>
                    <td>{doc.docType}</td>
                    <td>
                      <Badge>{doc.status}</Badge>
                    </td>
                    <td>
                      <Button type="button" onClick={() => void approve(doc.id)}>
                        批准
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
