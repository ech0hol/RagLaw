import { FileText } from 'lucide-react';
import { Link } from 'react-router-dom';
import { appendKnowledgeDetailParams } from '../lib/knowledgeSearch';

type ViewOriginalLinkProps = {
  documentId: string;
  className?: string;
  label?: string;
  from?: 'admin' | 'search';
  searchReturnParams?: URLSearchParams;
};

export function ViewOriginalLink({
  documentId,
  className = 'rl-btn rl-btn--ghost rl-btn--sm',
  label = '查看原文',
  from,
  searchReturnParams,
}: ViewOriginalLinkProps) {
  const params = appendKnowledgeDetailParams(
    new URLSearchParams(),
    documentId,
    from,
    searchReturnParams,
  );
  return (
    <Link className={className} to={`/knowledge/documents?${params.toString()}`}>
      <FileText size={14} aria-hidden />
      {label}
    </Link>
  );
}
