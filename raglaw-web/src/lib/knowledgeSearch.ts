export type KnowledgeDocType = 'STATUTE' | 'CASE';

export type KnowledgeSearchState = {
  q: string;
  docType: KnowledgeDocType;
  l2Path: string;
  page: number;
};

export function parseKnowledgeSearchParams(params: URLSearchParams): KnowledgeSearchState {
  const q = params.get('q') ?? '';
  const docType: KnowledgeDocType = params.get('docType') === 'CASE' ? 'CASE' : 'STATUTE';
  const l2Path = params.get('l2Path') ?? '';
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);
  return { q, docType, l2Path, page };
}

export function buildKnowledgeSearchParams(state: KnowledgeSearchState): URLSearchParams {
  const params = new URLSearchParams();
  params.set('docType', state.docType);
  if (state.q.trim()) {
    params.set('q', state.q.trim());
  }
  if (state.l2Path) {
    params.set('l2Path', state.l2Path);
  }
  if (state.page > 0) {
    params.set('page', String(state.page));
  }
  return params;
}

export function buildKnowledgeListPath(state: Partial<KnowledgeSearchState>): string {
  const params = buildKnowledgeSearchParams({
    q: state.q ?? '',
    docType: state.docType ?? 'STATUTE',
    l2Path: state.l2Path ?? '',
    page: state.page ?? 0,
  });
  return `/knowledge/statutes?${params.toString()}`;
}

export function buildKnowledgeDetailReturnParams(state: KnowledgeSearchState): URLSearchParams {
  const params = new URLSearchParams();
  params.set('from', 'search');
  params.set('docType', state.docType);
  params.set('q', state.q);
  if (state.l2Path) {
    params.set('l2Path', state.l2Path);
  }
  if (state.page > 0) {
    params.set('page', String(state.page));
  }
  return params;
}

export function appendKnowledgeDetailParams(
  base: URLSearchParams,
  documentId: string,
  linkFrom?: 'admin' | 'search',
  searchReturnParams?: URLSearchParams,
): URLSearchParams {
  const params = new URLSearchParams(base);
  params.set('doc', documentId);
  if (linkFrom === 'admin') {
    params.set('from', 'admin');
    return params;
  }
  if (linkFrom === 'search' && searchReturnParams) {
    params.set('from', 'search');
    for (const [key, value] of searchReturnParams.entries()) {
      if (key !== 'from' && key !== 'doc') {
        params.set(key, value);
      }
    }
  }
  return params;
}
