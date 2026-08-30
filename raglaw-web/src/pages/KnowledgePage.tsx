import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { Select, Spinner } from '@raglaw/ui';
import { KnowledgeResultCard } from '../components/KnowledgeResultCard';
import { api, fetchKnowledgeStats, type KnowledgeStats } from '../lib/api';
import { formatLocalDate, formatRelevanceScore } from '../lib/formatDate';
import {
  buildKnowledgeDetailReturnParams,
  buildKnowledgeSearchParams,
  parseKnowledgeSearchParams,
  type KnowledgeDocType,
} from '../lib/knowledgeSearch';

type KnowledgeHit = {
  chunkId: string;
  documentId: string;
  title: string;
  path: string;
  excerpt: string;
  score: number;
  createdAt?: string | null;
  effectiveDate?: string | null;
};

type SearchPage = {
  items: KnowledgeHit[];
  page: number;
  pageSize: number;
  total: number;
};

type CategoryNode = {
  id: string;
  name: string;
  path: string;
  docType: string;
  level: number;
  children?: CategoryNode[];
};

const PAGE_SIZE = 5;

const DOC_TABS = [
  { value: 'STATUTE', label: '法规查询' },
  { value: 'CASE', label: '案例查询' },
] as const;

export function KnowledgePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialSearch = useMemo(() => parseKnowledgeSearchParams(searchParams), []);
  const [query, setQuery] = useState(initialSearch.q);
  const [docType, setDocType] = useState<KnowledgeDocType>(initialSearch.docType);
  const [l2Path, setL2Path] = useState(initialSearch.l2Path);
  const [page, setPage] = useState(initialSearch.page);
  const [result, setResult] = useState<SearchPage | null>(null);
  const [categories, setCategories] = useState<CategoryNode[]>([]);
  const [stats, setStats] = useState<KnowledgeStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(Boolean(initialSearch.q.trim()));
  const restoredFromUrl = useRef(false);

  const syncSearchToUrl = useCallback((next: {
    q: string;
    docType: KnowledgeDocType;
    l2Path: string;
    page: number;
  }) => {
    setSearchParams(buildKnowledgeSearchParams(next), { replace: true });
  }, [setSearchParams]);

  const loadStats = useCallback(() => {
    void fetchKnowledgeStats().then((res) => {
      if (res.success) setStats(res.data);
    });
  }, []);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/categories/tree').then((res) => {
      if (res.success) setCategories(res.data);
    });
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    loadStats();
  }, [docType, loadStats]);

  useEffect(() => {
    function onVisibilityChange() {
      if (document.visibilityState === 'visible') {
        loadStats();
      }
    }
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [loadStats]);

  const runSearch = useCallback(async (
    nextPage = 0,
    overrides?: Partial<{ query: string; l2Path: string; docType: KnowledgeDocType }>,
  ) => {
    const activeQuery = (overrides?.query ?? query).trim();
    if (!activeQuery) return;
    const activeL2 = overrides?.l2Path ?? l2Path;
    const activeDocType = overrides?.docType ?? docType;
    setLoading(true);
    setSearched(true);
    setPage(nextPage);
    syncSearchToUrl({
      q: activeQuery,
      docType: activeDocType,
      l2Path: activeL2,
      page: nextPage,
    });
    const params = new URLSearchParams({
      q: activeQuery,
      docType: activeDocType,
      page: String(nextPage),
      pageSize: String(PAGE_SIZE),
    });
    if (activeL2) params.set('l2Path', activeL2);
    const res = await api<SearchPage>(`/api/v1/knowledge/search?${params.toString()}`);
    setResult(res.success ? res.data : { items: [], page: nextPage, pageSize: PAGE_SIZE, total: 0 });
    setLoading(false);
  }, [query, l2Path, docType, syncSearchToUrl]);

  useEffect(() => {
    if (restoredFromUrl.current || !initialSearch.q.trim()) {
      return;
    }
    restoredFromUrl.current = true;
    void runSearch(initialSearch.page, {
      query: initialSearch.q,
      l2Path: initialSearch.l2Path,
      docType: initialSearch.docType,
    });
  }, [initialSearch, runSearch]);

  const l2Options = useMemo(() => {
    const options: { value: string; label: string }[] = [];
    for (const l1 of categories) {
      for (const l2 of l1.children ?? []) {
        if (l2.docType === docType) {
          options.push({ value: l2.path, label: l2.name });
        }
      }
    }
    return options;
  }, [categories, docType]);

  const l2ScopeOptions = useMemo(
    () => [{ value: '', label: '全文' }, ...l2Options.map((o) => ({ value: o.value, label: o.label }))],
    [l2Options],
  );

  const searchReturnParams = useMemo(
    () => buildKnowledgeDetailReturnParams({ q: query, docType, l2Path, page }),
    [query, docType, l2Path, page],
  );

  const placeholder = docType === 'CASE'
    ? '请输入案例关键词'
    : '请输入法规关键词';

  async function onSearch(e: FormEvent, nextPage = 0) {
    e.preventDefault();
    await runSearch(nextPage);
  }

  function onL2FilterChange(path: string) {
    setL2Path(path);
    if (searched && query.trim()) {
      void runSearch(0, { l2Path: path });
    }
  }

  const displayItems = useMemo(() => {
    if (!result?.items) {
      return [];
    }
    const seen = new Set<string>();
    return result.items.filter((hit) => {
      if (seen.has(hit.documentId)) {
        return false;
      }
      seen.add(hit.documentId);
      return true;
    });
  }, [result]);

  const pathCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const hit of displayItems) {
      const l2 = hit.path.split('/').slice(0, 3).join('/') || hit.path;
      counts.set(l2, (counts.get(l2) ?? 0) + 1);
    }
    return counts;
  }, [displayItems]);

  const displayTotal = displayItems.length > 0 ? result?.total ?? 0 : 0;
  const totalPages = result ? Math.ceil(displayTotal / PAGE_SIZE) : 0;
  const activeCount = docType === 'CASE' ? stats?.caseCount : stats?.statuteCount;
  const statsLabel = docType === 'CASE' ? '案例' : '法规';

  return (
    <div className="rl-page-center">
      <div className="rl-knowledge-hero">
        <h1 className="rl-knowledge-hero__title">法规/案例查询</h1>
        <div className="rl-knowledge-tabs">
          {DOC_TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              className={[
                'rl-knowledge-tab',
                docType === tab.value && 'rl-knowledge-tab--active',
              ].filter(Boolean).join(' ')}
              onClick={() => {
                setDocType(tab.value);
                setL2Path('');
                setResult(null);
                setSearched(false);
                setPage(0);
                setSearchParams(buildKnowledgeSearchParams({
                  q: '',
                  docType: tab.value,
                  l2Path: '',
                  page: 0,
                }), { replace: true });
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <form className="rl-knowledge-search-bar" onSubmit={(e) => void onSearch(e, 0)}>
          <div className="rl-knowledge-search-bar__scope">
            <Select
              value={l2Path}
              onChange={(path) => onL2FilterChange(path)}
              options={l2ScopeOptions}
              menuWidthFromOptions
              labelAlign="center"
            />
          </div>
          <input
            className="rl-knowledge-search-bar__input"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={placeholder}
          />
          <button type="submit" className="rl-knowledge-search-bar__submit" disabled={loading || !query.trim()}>
            <Search size={16} />
            检索
          </button>
        </form>
        <p className="rl-knowledge-stats">
          本数据库已收录{statsLabel}
          <span className="rl-knowledge-stats__num">
            {activeCount != null ? activeCount.toLocaleString('zh-CN') : '—'}
          </span>
          篇
        </p>
      </div>

      {loading && <Spinner />}

      {!loading && searched && result && result.items.length === 0 && (
        <p>未找到相关结果，请尝试其他关键词。</p>
      )}

      {!loading && result && result.items.length > 0 && (
        <div className="rl-knowledge-layout">
          <aside className="rl-knowledge-filters">
            <h2 className="rl-knowledge-filters__title">领域筛选</h2>
            <p className="rl-text-muted rl-knowledge-filters__hint">括号内为匹配篇数</p>
            <div className="rl-knowledge-filter-list">
              <label className="rl-knowledge-filter-item">
                <input
                  type="checkbox"
                  checked={!l2Path}
                  onChange={() => onL2FilterChange('')}
                />
                <span>全部领域</span>
                <span className="rl-knowledge-filter-item__count">({displayTotal})</span>
              </label>
              {l2Options.map((opt) => (
                <label key={opt.value} className="rl-knowledge-filter-item">
                  <input
                    type="checkbox"
                    checked={l2Path === opt.value}
                    onChange={() => onL2FilterChange(l2Path === opt.value ? '' : opt.value)}
                  />
                  <span>{opt.label}</span>
                  <span className="rl-knowledge-filter-item__count">
                    ({pathCounts.get(opt.value) ?? 0})
                  </span>
                </label>
              ))}
            </div>
          </aside>

          <div className="rl-knowledge-result-list">
            <p className="rl-text-muted rl-knowledge-result-list__summary">
              共 {displayTotal} 篇匹配
            </p>
            {displayItems.map((hit) => (
              <KnowledgeResultCard
                key={hit.documentId}
                documentId={hit.documentId}
                title={hit.title}
                excerpt={hit.excerpt}
                meta={[
                  hit.path,
                  `相关度 ${formatRelevanceScore(hit.score)}`,
                  `入库 ${formatLocalDate(hit.createdAt)}`,
                  `生效 ${hit.effectiveDate ? formatLocalDate(hit.effectiveDate) : '未识别'}`,
                ]}
                linkFrom="search"
                searchReturnParams={searchReturnParams}
              />
            ))}
            {totalPages > 1 && (
              <div className="rl-pagination">
                <button
                  type="button"
                  className="rl-btn"
                  disabled={page <= 0 || loading}
                  onClick={(e) => void onSearch(e, page - 1)}
                >
                  上一页
                </button>
                <span className="rl-text-muted">第 {page + 1} / {totalPages} 页（共 {displayTotal} 篇匹配）</span>
                <button
                  type="button"
                  className="rl-btn"
                  disabled={page + 1 >= totalPages || loading}
                  onClick={(e) => void onSearch(e, page + 1)}
                >
                  下一页
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
