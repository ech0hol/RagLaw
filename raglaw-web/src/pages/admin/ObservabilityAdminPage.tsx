import { useEffect, useState } from 'react';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { api } from '../../lib/api';

type TraceSummary = {
  id: string;
  conversationId: string;
  agentCode: string;
  queryText: string;
  latencyMs: number | null;
  langfuseTraceId?: string | null;
  langfuseUrl?: string | null;
  createdAt: string;
};

type TraceDetail = {
  trace: TraceSummary;
  stages: { id: string; stage: string; detailJson: string | null; durationMs: number | null }[];
  chunks: { id: string; chunkId: string; score: number; path: string; excerpt: string }[];
};

export function ObservabilityAdminPage() {
  const [traces, setTraces] = useState<TraceSummary[]>([]);
  const [selected, setSelected] = useState<TraceDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void api<TraceSummary[]>('/api/v1/admin/traces').then((res) => {
      if (res.success) setTraces(res.data);
      setLoading(false);
    });
  }, []);

  async function openTrace(id: string) {
    const res = await api<TraceDetail>(`/api/v1/admin/traces/${id}`);
    if (res.success) setSelected(res.data);
  }

  const maxStageMs = selected
    ? Math.max(...selected.stages.map((stage) => stage.durationMs ?? 0), 1)
    : 1;

  return (
    <div>
      <PageHeader title="可观测性" subtitle="RAG 追踪列表与阶段瀑布（L1 MySQL trace）。" />
      {loading ? (
        <Spinner />
      ) : (
        <div className="rl-obs-layout">
          <Card>
            <div className="rl-data-table-wrap">
              <table className="rl-data-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>Agent</th>
                    <th>问题</th>
                    <th>延迟</th>
                  </tr>
                </thead>
                <tbody>
                  {traces.map((t) => (
                    <tr key={t.id} className={selected?.trace.id === t.id ? 'rl-row--active' : ''} onClick={() => void openTrace(t.id)} style={{ cursor: 'pointer' }}>
                      <td>{new Date(t.createdAt).toLocaleString()}</td>
                      <td>{t.agentCode}</td>
                      <td>{t.queryText}</td>
                      <td>{t.latencyMs ?? '—'} ms</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          {selected && (
            <Card className="rl-obs-detail">
              <h3>Trace {selected.trace.id.slice(0, 8)}…</h3>
              {selected.trace.langfuseUrl && (
                <p>
                  <a href={selected.trace.langfuseUrl} target="_blank" rel="noreferrer">
                    在 Langfuse 中查看
                  </a>
                </p>
              )}
              <h4>阶段瀑布</h4>
              <ul className="rl-obs-waterfall">
                {selected.stages.map((stage) => (
                  <li key={stage.id} className="rl-obs-waterfall__item">
                    <strong>{stage.stage}</strong> — {stage.durationMs ?? 0} ms
                    <div
                      className="rl-obs-waterfall__bar"
                      style={{ width: `${((stage.durationMs ?? 0) / maxStageMs) * 100}%` }}
                    />
                    {stage.detailJson && <pre className="rl-trace-detail">{stage.detailJson}</pre>}
                  </li>
                ))}
              </ul>
              <h4>检索片段</h4>
              <ul>
                {selected.chunks.map((c) => (
                  <li key={c.id}>
                    [{c.score?.toFixed(2)}] {c.path} — {c.excerpt}
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
