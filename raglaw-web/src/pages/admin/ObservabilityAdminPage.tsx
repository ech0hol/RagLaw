import { useEffect, useState } from 'react';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { api } from '../../lib/api';

type TraceSummary = {
  id: string;
  conversationId: string;
  agentCode: string;
  queryText: string;
  latencyMs: number | null;
  createdAt: string;
};

export function ObservabilityAdminPage() {
  const [traces, setTraces] = useState<TraceSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      const res = await api<TraceSummary[]>('/api/v1/admin/traces');
      if (res.success) setTraces(res.data);
      setLoading(false);
    })();
  }, []);

  return (
    <div>
      <PageHeader title="可观测性" subtitle="最近 RAG 追踪记录（L1 MySQL trace）。" />
      {loading ? (
        <Spinner />
      ) : (
        <Card>
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>Agent</th>
                  <th>问题</th>
                  <th>延迟(ms)</th>
                </tr>
              </thead>
              <tbody>
                {traces.map((t) => (
                  <tr key={t.id}>
                    <td>{new Date(t.createdAt).toLocaleString()}</td>
                    <td>{t.agentCode}</td>
                    <td>{t.queryText}</td>
                    <td>{t.latencyMs ?? '—'}</td>
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
