import { FormEvent, useState } from 'react';
import { Card, PageHeader, SearchInput, Spinner } from '@raglaw/ui';
import { api } from '../lib/api';

type KnowledgeHit = {
  chunkId: string;
  documentId: string;
  title: string;
  path: string;
  excerpt: string;
  score: number;
};

export function KnowledgePage() {
  const [query, setQuery] = useState('');
  const [docType, setDocType] = useState('STATUTE');
  const [hits, setHits] = useState<KnowledgeHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  async function onSearch(e: FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setSearched(true);
    const res = await api<KnowledgeHit[]>(
      `/api/v1/knowledge/search?q=${encodeURIComponent(query)}&docType=${docType}&limit=10`,
    );
    setHits(res.success ? res.data : []);
    setLoading(false);
  }

  return (
    <div>
      <PageHeader title="法规/案例查询" subtitle="全文检索已入库的法规条文与案例片段。" />
      <Card>
        <form className="rl-admin-form" onSubmit={onSearch}>
          <div className="rl-admin-form__row">
            <label>
              类型
              <select value={docType} onChange={(e) => setDocType(e.target.value)}>
                <option value="STATUTE">法规</option>
                <option value="CASE">案例</option>
              </select>
            </label>
            <SearchInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="输入关键词，如：拖欠工资、违约金" />
            <button type="submit" className="rl-btn rl-btn--primary" disabled={loading}>
              检索
            </button>
          </div>
        </form>
      </Card>
      <div className="rl-knowledge-results">
        {loading && <Spinner />}
        {!loading && searched && hits.length === 0 && <p>未找到相关结果，请尝试其他关键词。</p>}
        {hits.map((hit) => (
          <Card key={hit.chunkId} className="rl-knowledge-hit">
            <h3>{hit.title}</h3>
            <p className="rl-knowledge-hit__path">{hit.path}</p>
            <p>{hit.excerpt}</p>
            <span className="rl-knowledge-hit__score">相关度 {hit.score.toFixed(2)}</span>
          </Card>
        ))}
      </div>
    </div>
  );
}
