export type ChatReference = {
  index: number;
  chunkId: string;
  documentId?: string;
  path: string;
  excerpt: string;
  score: number;
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
      {references.map((ref) => (
        <article key={ref.chunkId} className="rl-reference-card" data-testid="reference-card">
          <header className="rl-reference-card__header">
            <span className="rl-reference-card__index">[{ref.index}]</span>
            {ref.documentId ? (
              <a className="rl-reference-card__path" href={`/knowledge/statutes?doc=${ref.documentId}`}>
                {ref.path}
              </a>
            ) : (
              <span className="rl-reference-card__path">{ref.path}</span>
            )}
          </header>
          <p className="rl-reference-card__excerpt">{ref.excerpt}</p>
        </article>
      ))}
    </div>
  );
}
