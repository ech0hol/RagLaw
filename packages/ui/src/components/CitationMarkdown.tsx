import { useRef, useState, type AnchorHTMLAttributes, type MouseEvent } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import { CitationPopover } from './CitationPopover';
import { prepareCitationDisplayContent } from './citationMarkdownUtils';
import {
  preprocessCitationLinks,
  type CitationMeta,
} from './citationMarkdownPreprocessor';
import type { ChatReference } from './ReferenceList';
import { filterUserVisibleReferences } from '../lib/referenceUtils';

type CitationMarkdownProps = {
  content: string;
  references?: ChatReference[];
  fetchDocumentTitle?: (documentId: string) => Promise<string | null>;
  fetchDocumentExcerpt?: (documentId: string, anchors: string) => Promise<string | null>;
};

type CitationLinkProps = {
  meta: CitationMeta | undefined;
  children: React.ReactNode;
  showPopover: (e: MouseEvent<HTMLElement>, meta?: CitationMeta) => void;
  scheduleClose: () => void;
};

function CitationLink({ meta, children, showPopover, scheduleClose }: CitationLinkProps) {
  return (
    <span
      className={[
        'rl-citation-mark',
        meta?.bold ? 'rl-citation-mark--bold' : '',
      ].filter(Boolean).join(' ')}
      onMouseEnter={(e) => showPopover(e, meta)}
      onMouseLeave={scheduleClose}
    >
      {children}
    </span>
  );
}

export function CitationMarkdown({
  content,
  references = [],
  fetchDocumentTitle,
  fetchDocumentExcerpt,
}: CitationMarkdownProps) {
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [hover, setHover] = useState<{
    reference: ChatReference;
    rect: DOMRect;
    articleLabel?: string;
  } | null>(null);

  function cancelClose() {
    if (closeTimer.current) {
      clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
  }

  function scheduleClose() {
    cancelClose();
    closeTimer.current = setTimeout(() => setHover(null), 150);
  }

  function showPopover(e: MouseEvent<HTMLElement>, meta?: CitationMeta) {
    cancelClose();
    if (!meta?.reference) return;
    setHover({
      reference: meta.reference,
      rect: e.currentTarget.getBoundingClientRect(),
      articleLabel: meta.articleSuffix || undefined,
    });
  }

  const visibleReferences = filterUserVisibleReferences(references);
  const normalized = prepareCitationDisplayContent(content, visibleReferences.length > 0);
  const { markdown, citationMap } = preprocessCitationLinks(normalized, visibleReferences);

  return (
    <div className="rl-markdown rl-citation-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        components={{
          a: ({ href, children, ...props }: AnchorHTMLAttributes<HTMLAnchorElement>) => {
            if (href?.startsWith('#raglaw-cite-')) {
              return (
                <CitationLink
                  meta={citationMap.get(href)}
                  showPopover={showPopover}
                  scheduleClose={scheduleClose}
                >
                  {children}
                </CitationLink>
              );
            }
            return (
              <a href={href} {...props} target="_blank" rel="noreferrer">
                {children}
              </a>
            );
          },
        }}
      >
        {markdown}
      </ReactMarkdown>
      {hover && (
        <CitationPopover
          reference={hover.reference}
          anchorRect={hover.rect}
          articleLabel={hover.articleLabel}
          onClose={() => setHover(null)}
          onMouseEnter={cancelClose}
          onMouseLeave={scheduleClose}
          fetchDocumentTitle={fetchDocumentTitle}
          fetchDocumentExcerpt={fetchDocumentExcerpt}
        />
      )}
    </div>
  );
}
