import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { appendKnowledgeDetailParams } from '../lib/knowledgeSearch';
import { ViewOriginalLink } from './ViewOriginalLink';

type KnowledgeResultCardProps = {
  documentId: string;
  title: string;
  meta: string[];
  excerpt?: string;
  linkFrom?: 'admin' | 'search';
  searchReturnParams?: URLSearchParams;
  leading?: ReactNode;
  actions?: ReactNode;
  trailingActions?: ReactNode;
  showViewOriginalLink?: boolean;
};

function buildDocumentLink(
  documentId: string,
  linkFrom?: 'admin' | 'search',
  searchReturnParams?: URLSearchParams,
): string {
  const params = appendKnowledgeDetailParams(new URLSearchParams(), documentId, linkFrom, searchReturnParams);
  return `/knowledge/documents?${params.toString()}`;
}

export function KnowledgeResultCard({
  documentId,
  title,
  meta,
  excerpt,
  linkFrom,
  searchReturnParams,
  leading,
  actions,
  trailingActions,
  showViewOriginalLink = true,
}: KnowledgeResultCardProps) {
  const hasActions = showViewOriginalLink || actions || trailingActions;

  return (
    <article className="rl-knowledge-result-card">
      <div className="rl-knowledge-result-card__title-row">
        {leading}
        <h3 className="rl-knowledge-result-card__title">
          <Link to={buildDocumentLink(documentId, linkFrom, searchReturnParams)}>{title}</Link>
        </h3>
      </div>
      {excerpt && (
        <p className="rl-knowledge-result-card__excerpt">{excerpt}</p>
      )}
      {meta.length > 0 && (
        <p className="rl-knowledge-result-card__meta">
          {meta.map((item, index) => (
            <span key={`${index}-${item}`}>{item}</span>
          ))}
        </p>
      )}
      {hasActions && (
        <div className="rl-knowledge-result-card__actions">
          {actions}
          {showViewOriginalLink && (
            <ViewOriginalLink
              documentId={documentId}
              from={linkFrom === 'admin' ? 'admin' : linkFrom === 'search' ? 'search' : undefined}
              searchReturnParams={searchReturnParams}
            />
          )}
          {trailingActions && (
            <div className="rl-knowledge-result-card__actions-trailing">
              {trailingActions}
            </div>
          )}
        </div>
      )}
    </article>
  );
}
