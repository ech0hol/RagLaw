import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { Select, Spinner } from '@raglaw/ui';
import { api, fetchKnowledgeStats, type KnowledgeStats } from '../lib/api';

type KnowledgeHit = {
  chunkId: string;
  documentId: string;
  title: string;
  path: string;
  excerpt: string;
  score: number;
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
  const [query, setQuery] = useState('');
  const [docType, setDocType] = useState<string>('STATUTE');
  const [l2Path, setL2Path] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<SearchPage | null>(null);
  const [categories, setCategories] = useState<CategoryNode[]>([]);
  const [stats, setStats] = useState<KnowledgeStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/categories/tree').then((res) => {
      if (res.success) setCategories(res.data);
    });
    void fetchKnowledgeStats().then((res) => {
      if (res.success) setStats(res.data);
    });
  }, []);

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

  const placeholder = docType === 'CASE'
    ? '请输入案例关键词'
    : '请输入法规关键词';

  async function runSearch(nextPage = 0, l2Override?: string) {
    if (!query.trim()) return;
    const activeL2 = l2Override ?? l2Path;
    setLoading(true);
    setSearched(true);
    setPage(nextPage);
    const params = new URLSearchParams({
      q: query,
      docType,
      page: String(nextPage),
      pageSize: String(PAGE_SIZE),
    });
    if (activeL2) params.set('l2Path', activeL2);
    const res = await api<SearchPage>(`/api/v1/knowledge/search?${params.toString()}`);
    setResult(res.success ? res.data : { items: [], page: nextPage, pageSize: PAGE_SIZE, total: 0 });
    setLoading(false);
  }

  async function onSearch(e: FormEvent, nextPage = 0) {
    e.preventDefault();
    await runSearch(nextPage);
  }

  function onL2FilterChange(path: string) {
    setL2Path(path);
    if (searched && query.trim()) {
      void runSearch(0, path);
    }
  }

  const pathCounts = useMemo(() => {
    const counts = new Map<string, number>();
    if (!result?.items) return counts;
    for (const hit of result.items) {
      const l2 = hit.path.split('/').slice(0, 3).join('/') || hit.path;
      counts.set(l2, (counts.get(l2) ?? 0) + 1);
    }
    return counts;
  }, [result]);

  const totalPages = result ? Math.ceil(result.total / PAGE_SIZE) : 0;
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
              onChange={setL2Path}
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
            <div className="rl-knowledge-filter-list">
              <label className="rl-knowledge-filter-item">
                <input
                  type="checkbox"
                  checked={!l2Path}
                  onChange={() => onL2FilterChange('')}
                />
                <span>全部领域</span>
                <span className="rl-knowledge-filter-item__count">({result.total})</span>
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
            {result.items.map((hit) => (
              <article key={hit.chunkId} className="rl-knowledge-result-card">
                <h3 className="rl-knowledge-result-card__title">
                  <a href={`/knowledge/documents?doc=${hit.documentId}`}>{hit.title}</a>
                </h3>
                <p className="rl-knowledge-result-card__meta">
                  <span>{hit.path}</span>
                  <span>相关度 {hit.score.toFixed(2)}</span>
                </p>
                <p className="rl-knowledge-snippet">{hit.excerpt}</p>
              </article>
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
                <span className="rl-text-muted">第 {page + 1} / {totalPages} 页（共 {result.total} 条）</span>
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
