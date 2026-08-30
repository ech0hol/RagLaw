import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Badge, Card, PageHeader, Select, Spinner } from '@raglaw/ui';
import { TraceDetailDialog } from '../../components/TraceDetailDialog';
import { normalizeTraceDetail, type TraceDetail, type TraceSummary } from '../../components/traceDetailTypes';
import { api, callWithAuthRetry, deleteAdminTrace, deleteAdminTracesBatch } from '../../lib/api';
import { confirmIrreversibleBatchDelete, confirmIrreversibleDelete } from '../../lib/confirmDelete';

type TraceListPage = {
  items: TraceSummary[];
  page: number;
  pageSize: number;
  total: number;
};

const AGENT_OPTIONS = [
  { value: '__all__', label: '全部 Agent' },
  { value: 'GENERAL', label: 'GENERAL' },
  { value: 'STATUTE', label: 'STATUTE' },
  { value: 'CASE', label: 'CASE' },
  { value: 'CONTRACT', label: 'CONTRACT' },
];

const MAX_BATCH_DELETE = 50;

function normalizeTraceList(data: unknown): { items: TraceSummary[]; page: number; total: number } {
  if (Array.isArray(data)) {
    return { items: data, page: 1, total: data.length };
  }
  const pageData = data as TraceListPage | null | undefined;
  return {
    items: Array.isArray(pageData?.items) ? pageData.items : [],
    page: pageData?.page ?? 1,
    total: pageData?.total ?? 0,
  };
}

export function ObservabilityAdminPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [traces, setTraces] = useState<TraceSummary[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [draftAgent, setDraftAgent] = useState('__all__');
  const [draftQuery, setDraftQuery] = useState('');
  const [appliedAgent, setAppliedAgent] = useState('__all__');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [selected, setSelected] = useState<TraceDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [deleting, setDeleting] = useState(false);
  const deepLinkHandledRef = useRef<string | null>(null);

  const pageSize = 20;

  const loadTraces = useCallback(async (nextPage: number, nextAgent: string, nextQuery: string) => {
    setLoading(true);
    setLoadError(null);
    const params = new URLSearchParams({
      page: String(nextPage),
      pageSize: String(pageSize),
    });
    if (nextAgent && nextAgent !== '__all__') params.set('agentCode', nextAgent);
    if (nextQuery.trim()) params.set('q', nextQuery.trim());
    const res = await api<TraceListPage | TraceSummary[]>(`/api/v1/admin/traces?${params.toString()}`);
    if (res.success) {
      const normalized = normalizeTraceList(res.data);
      setTraces(normalized.items);
      setPage(normalized.page);
      setTotal(normalized.total);
    } else {
      setTraces([]);
      setLoadError(res.error?.message ?? '加载 trace 列表失败');
    }
    setLoading(false);
  }, []);

  const closeDetail = useCallback(() => {
    const closingId = searchParams.get('trace');
    if (closingId) {
      deepLinkHandledRef.current = closingId;
    }
    setDetailOpen(false);
    setSelected(null);
    setDetailError(null);
    const next = new URLSearchParams(searchParams);
    next.delete('trace');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const openTrace = useCallback(async (id: string) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError(null);
    const res = await api<TraceDetail>(`/api/v1/admin/traces/${id}`);
    if (res.success) {
      setSelected(normalizeTraceDetail(res.data));
      const next = new URLSearchParams(searchParams);
      next.set('trace', id);
      setSearchParams(next, { replace: true });
    } else {
      setDetailError(res.error?.message ?? '加载 trace 详情失败');
    }
    setDetailLoading(false);
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    void loadTraces(page, appliedAgent, appliedQuery);
  }, [loadTraces, page, appliedAgent, appliedQuery]);

  useEffect(() => {
    const traceId = searchParams.get('trace');
    if (!traceId) {
      deepLinkHandledRef.current = null;
      return;
    }
    if (deepLinkHandledRef.current === traceId) return;
    if (selected?.trace.id === traceId) {
      deepLinkHandledRef.current = traceId;
      setDetailOpen(true);
      return;
    }
    deepLinkHandledRef.current = traceId;
    void openTrace(traceId);
  }, [openTrace, searchParams, selected?.trace.id]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const allPageSelected = traces.length > 0 && traces.every((t) => selectedIds.has(t.id));
  const hasActiveFilters = appliedAgent !== '__all__' || appliedQuery.trim().length > 0;

  function toggleSelection(id: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  function selectAllOnPage() {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      for (const trace of traces) {
        next.add(trace.id);
      }
      return next;
    });
  }

  function clearSelection() {
    setSelectedIds(new Set());
  }

  function clearDetailIfDeleted(deletedIds: Set<string>) {
    if (selected && deletedIds.has(selected.trace.id)) {
      closeDetail();
    }
  }

  function removeDeletedFromSelection(deletedIds: Set<string>) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      for (const id of deletedIds) {
        next.delete(id);
      }
      return next;
    });
  }

  async function reloadAfterDelete(deletedCount: number) {
    const remainingOnPage = traces.length - deletedCount;
    if (remainingOnPage <= 0 && page > 1) {
      setPage((p) => p - 1);
      return;
    }
    await loadTraces(page, appliedAgent, appliedQuery);
  }

  function reportDeleteError(message: string) {
    if (detailOpen) {
      setDetailError(message);
    } else {
      setLoadError(message);
    }
  }

  async function deleteTrace(id: string, label: string) {
    if (!confirmIrreversibleDelete(label)) return;
    setDeleting(true);
    setLoadError(null);
    setDetailError(null);
    setMessage(null);
    const res = await callWithAuthRetry(() => deleteAdminTrace(id));
    if (!res.success) {
      reportDeleteError(res.error?.message ?? '删除失败');
      setDeleting(false);
      return;
    }
    const deletedIds = new Set([id]);
    clearDetailIfDeleted(deletedIds);
    removeDeletedFromSelection(deletedIds);
    setMessage('已删除 trace');
    setDeleting(false);
    await reloadAfterDelete(1);
  }

  async function deleteSelectedTraces() {
    const ids = [...selectedIds].slice(0, MAX_BATCH_DELETE);
    if (ids.length === 0) return;
    if (!confirmIrreversibleBatchDelete(ids.length, '条 trace')) return;
    setDeleting(true);
    setLoadError(null);
    setDetailError(null);
    setMessage(null);
    const res = await callWithAuthRetry(() => deleteAdminTracesBatch(ids));
    if (!res.success) {
      reportDeleteError(res.error?.message ?? '批量删除失败');
      setDeleting(false);
      return;
    }
    const deletedIds = new Set(ids);
    clearDetailIfDeleted(deletedIds);
    removeDeletedFromSelection(deletedIds);
    setMessage(`已删除 ${res.data.deleted} 条 trace`);
    setDeleting(false);
    await reloadAfterDelete(res.data.deleted);
  }

  function applyFilters() {
    setPage(1);
    setAppliedAgent(draftAgent);
    setAppliedQuery(draftQuery);
  }

  function resetFilters() {
    setDraftAgent('__all__');
    setDraftQuery('');
    setAppliedAgent('__all__');
    setAppliedQuery('');
    setPage(1);
  }

  return (
    <div>
      <PageHeader title="可观测性" subtitle="RAG 追踪、漏斗阶段、影子路由与合同审查 trace。" />

      {loadError && (
        <p className="rl-form-error" style={{ marginBottom: '1rem' }}>{loadError}</p>
      )}
      {message && (
        <p className="rl-form-hint" style={{ marginBottom: '1rem' }}>{message}</p>
      )}

      <div className="rl-obs-summary-grid">
        <Card className="rl-obs-summary-card">
          <h3>Trace 总数</h3>
          <p>{total}</p>
          {hasActiveFilters && (
            <p className="rl-text-muted rl-obs-summary-card__hint">当前筛选结果</p>
          )}
        </Card>
      </div>

      <Card className="rl-obs-toolbar" style={{ marginBottom: '1rem' }}>
        <div className="rl-obs-toolbar__filters">
          <Select
            label="Agent"
            value={draftAgent}
            onChange={setDraftAgent}
            options={AGENT_OPTIONS}
          />
          <div className="rl-admin-form-field">
            <label htmlFor="obs-query">关键词</label>
            <input
              id="obs-query"
              className="rl-input"
              value={draftQuery}
              placeholder="搜索问题文本…"
              onChange={(e) => setDraftQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  applyFilters();
                }
              }}
            />
          </div>
          <button
            type="button"
            className="rl-btn rl-btn--primary rl-btn--sm"
            onClick={applyFilters}
          >
            筛选
          </button>
          <button
            type="button"
            className="rl-btn rl-btn--ghost rl-btn--sm"
            onClick={resetFilters}
          >
            重置
          </button>
        </div>
      </Card>

      {loading ? (
        <Spinner />
      ) : (
        <Card>
          <div className="rl-obs-list-toolbar">
            {traces.length > 0 && (
              <label className="rl-checkbox-item">
                <input
                  type="checkbox"
                  checked={allPageSelected}
                  onChange={(e) => {
                    if (e.target.checked) {
                      selectAllOnPage();
                    } else {
                      clearSelection();
                    }
                  }}
                />
                全选本页
              </label>
            )}
            <button
              type="button"
              className="rl-btn rl-btn--danger rl-btn--sm"
              disabled={selectedIds.size === 0}
              onClick={() => void deleteSelectedTraces()}
            >
              删除选中 ({selectedIds.size}/{MAX_BATCH_DELETE})
            </button>
          </div>
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th className="rl-obs-table__select" aria-label="选择" />
                  <th>时间</th>
                  <th>Agent</th>
                  <th>问题</th>
                  <th>延迟</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {traces.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="rl-text-muted">暂无 trace 记录</td>
                  </tr>
                ) : traces.map((t) => (
                  <tr
                    key={t.id}
                    className={selected?.trace.id === t.id ? 'rl-row--active' : ''}
                    onClick={() => void openTrace(t.id)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td className="rl-obs-table__select" onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        aria-label={`选择 ${t.queryText}`}
                        checked={selectedIds.has(t.id)}
                        onChange={(e) => toggleSelection(t.id, e.target.checked)}
                      />
                    </td>
                    <td>{new Date(t.createdAt).toLocaleString()}</td>
                    <td><Badge variant="muted">{t.agentCode}</Badge></td>
                    <td>{t.queryText}</td>
                    <td>{t.latencyMs ?? '—'} ms</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        className="rl-btn rl-btn--ghost rl-btn--sm rl-btn--danger"
                        onClick={() => void deleteTrace(t.id, t.queryText)}
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="rl-obs-pagination">
            <button
              type="button"
              className="rl-btn rl-btn--ghost rl-btn--sm"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              上一页
            </button>
            <span className="rl-text-muted">
              第 {page} / {totalPages} 页
            </span>
            <button
              type="button"
              className="rl-btn rl-btn--ghost rl-btn--sm"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              下一页
            </button>
          </div>
        </Card>
      )}

      <TraceDetailDialog
        open={detailOpen}
        detail={selected}
        loading={detailLoading}
        error={detailError}
        deleting={deleting}
        onClose={closeDetail}
        onDelete={() => {
          if (selected) {
            void deleteTrace(selected.trace.id, selected.trace.queryText);
          }
        }}
      />
    </div>
  );
}
