import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Card, PageHeader, SearchInput, Select, Spinner } from '@raglaw/ui';
import { api } from '../lib/api';

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

export function KnowledgePage() {
  const [query, setQuery] = useState('');
  const [docType, setDocType] = useState('STATUTE');
  const [l2Path, setL2Path] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<SearchPage | null>(null);
  const [categories, setCategories] = useState<CategoryNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/categories/tree').then((res) => {
      if (res.success) setCategories(res.data);
    });
  }, []);

  const l2Options = useMemo(() => {
    const options = [{ value: '', label: '全部领域' }];
    for (const l1 of categories) {
      for (const l2 of l1.children ?? []) {
        if (l2.docType === docType) {
          options.push({ value: l2.path, label: l2.name });
        }
      }
    }
    return options;
  }, [categories, docType]);

  async function onSearch(e: FormEvent, nextPage = 0) {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setSearched(true);
    setPage(nextPage);
    const params = new URLSearchParams({
      q: query,
      docType,
      page: String(nextPage),
      pageSize: String(PAGE_SIZE),
    });
    if (l2Path) params.set('l2Path', l2Path);
    const res = await api<SearchPage>(`/api/v1/knowledge/search?${params.toString()}`);
    setResult(res.success ? res.data : { items: [], page: nextPage, pageSize: PAGE_SIZE, total: 0 });
    setLoading(false);
  }

  const totalPages = result ? Math.ceil(result.total / PAGE_SIZE) : 0;

  return (
    <div className="rl-page-center">
      <PageHeader title="法规/案例查询" subtitle="全文检索已入库的法规条文与案例片段，支持 L2 领域筛选与分页。" />
      <Card>
        <form className="rl-admin-form" onSubmit={(e) => void onSearch(e, 0)}>
          <div className="rl-admin-form__row">
            <Select
              label="类型"
              value={docType}
              onChange={(value) => { setDocType(value); setL2Path(''); }}
              options={[
                { value: 'STATUTE', label: '法规' },
                { value: 'CASE', label: '案例' },
              ]}
            />
            <Select
              label="领域 (L2)"
              value={l2Path}
              onChange={setL2Path}
              options={l2Options}
            />
            <SearchInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="输入关键词，如：拖欠工资、违约金" />
            <button type="submit" className="rl-btn rl-btn--primary" disabled={loading}>
              检索
            </button>
          </div>
        </form>
      </Card>
      <div className="rl-knowledge-results">
        {loading && <Spinner />}
        {!loading && searched && result && result.items.length === 0 && <p>未找到相关结果，请尝试其他关键词。</p>}
        {result?.items.map((hit) => (
          <Card key={hit.chunkId} className="rl-knowledge-hit">
            <h3>
              <a href={`/knowledge/documents?doc=${hit.documentId}`}>{hit.title}</a>
            </h3>
            <p className="rl-knowledge-hit__path">{hit.path}</p>
            <p>{hit.excerpt}</p>
            <span className="rl-knowledge-hit__score">相关度 {hit.score.toFixed(2)}</span>
          </Card>
        ))}
      </div>
      {result && totalPages > 1 && (
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
  );
}
