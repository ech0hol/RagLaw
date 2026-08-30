import { referenceSnippet } from '../lib/referenceSnippet';

export type ChatReference = {
  index: number;
  chunkId: string;
  documentId?: string;
  path: string;
  excerpt: string;
  content?: string;
  score: number;
  title?: string;
  source?: 'knowledge' | 'web';
  articleLabel?: string;
};

type ReferenceListProps = {
  references: ChatReference[];
};

export function ReferenceList({ references }: ReferenceListProps) {
  if (!references.length) {
    return null;
  }

  return (
    <div className="rl-reference-list" data-testid="reference-list">
      <p className="rl-reference-list__title">参考依据</p>
      {references.map((ref) => {
        const snippet = referenceSnippet(ref);
        return (
          <article key={ref.chunkId} className="rl-reference-card" data-testid="reference-card">
            <header className="rl-reference-card__header">
              <span className="rl-reference-card__index">[{ref.index}]</span>
              {ref.documentId ? (
                <a className="rl-reference-card__path" href={`/knowledge/documents?doc=${ref.documentId}`}>
                  {ref.path}
                </a>
              ) : (
                <span className="rl-reference-card__path">{ref.path}</span>
              )}
            </header>
            {snippet ? (
              <p className="rl-reference-card__snippet">{snippet}</p>
            ) : (
              <p className="rl-reference-card__snippet rl-text-muted">暂无摘录</p>
            )}
          </article>
        );
      })}
    </div>
  );
}
