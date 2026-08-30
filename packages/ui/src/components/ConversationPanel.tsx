import { useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { ChevronRight, Copy, Paperclip, RotateCcw, Send } from 'lucide-react';
import { CitationMarkdown } from './CitationMarkdown';
import { extractCitedReferenceIndices } from './citationMarkdownUtils';
import { ReferenceMaterialsPanel } from './ReferenceMaterialsPanel';
import { StreamingEllipsis } from './StreamingEllipsis';
import { isToolStreamStatus } from '../lib/streamStatus';
import { filterUserVisibleReferences, hasCatalogDocuments, isCatalogReference } from '../lib/referenceUtils';
import type { ChatReference } from './ReferenceList';

export type ChatMessage = {
  id?: string;
  role: string;
  content: string;
  references?: ChatReference[];
  streamStatus?: string | null;
  webSearchAttempted?: boolean;
};

type ConversationPanelProps = {
  messages: ChatMessage[];
  input: string;
  streaming: boolean;
  onInputChange: (value: string) => void;
  onSubmit: (e: FormEvent) => void;
  welcome?: ReactNode;
  disclaimer?: string;
  messageListRef?: React.RefObject<HTMLDivElement | null>;
  recommendQuestions?: string[];
  onRecommendClick?: (question: string) => void;
  onCopy?: (content: string) => void;
  onRegenerate?: (assistantMessageId?: string) => void;
  canRegenerate?: boolean;
  onMessageListScroll?: () => void;
  fetchDocumentTitle?: (documentId: string) => Promise<string | null>;
  fetchDocumentExcerpt?: (documentId: string, anchors: string) => Promise<string | null>;
  compact?: boolean;
};

function referenceSummary(references: ChatReference[]) {
  const visible = filterUserVisibleReferences(references);
  const knowledge = visible.filter((ref) => ref.source !== 'web');
  const web = references.filter((ref) => ref.source === 'web').length;
  const catalogDocs = knowledge.filter((ref) => isCatalogReference(ref.chunkId)).length;
  if (catalogDocs > 0) {
    if (web > 0) {
      return `已阅读知识库目录 · ${catalogDocs} 部文档 · 联网 ${web}`;
    }
    return `已阅读知识库目录 · ${catalogDocs} 部文档`;
  }
  const knowledgeCount = knowledge.length;
  if (web > 0) {
    return `已阅读相关资料 · 知识库 ${knowledgeCount} · 联网 ${web}`;
  }
  return `已阅读相关资料 · ${knowledgeCount} 条引用`;
}

function referencesForMaterials(content: string, references: ChatReference[]) {
  const visible = filterUserVisibleReferences(references);
  const cited = extractCitedReferenceIndices(content);
  if (cited.length === 0) {
    return visible;
  }
  const citedSet = new Set(cited);
  const filtered = visible.filter((ref) => citedSet.has(ref.index));
  return filtered.length > 0 ? filtered : visible;
}

export function ConversationPanel({
  messages,
  input,
  streaming,
  onInputChange,
  onSubmit,
  welcome,
  disclaimer,
  messageListRef,
  recommendQuestions = [],
  onRecommendClick,
  onCopy,
  onRegenerate,
  canRegenerate = false,
  onMessageListScroll,
  fetchDocumentTitle,
  fetchDocumentExcerpt,
  compact = false,
}: ConversationPanelProps) {
  const [materialsOpen, setMaterialsOpen] = useState(false);
  const [activeMaterials, setActiveMaterials] = useState<ChatReference[]>([]);
  const [activeMaterialsContent, setActiveMaterialsContent] = useState('');
  const [activeWebSearchAttempted, setActiveWebSearchAttempted] = useState(false);

  function openMaterials(
    references: ChatReference[],
    content = '',
    webSearchAttempted = false,
  ) {
    setActiveMaterials(references);
    setActiveMaterialsContent(content);
    setActiveWebSearchAttempted(webSearchAttempted);
    setMaterialsOpen(true);
  }

  function onTextareaKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!streaming && input.trim()) {
        e.currentTarget.form?.requestSubmit();
      }
    }
  }

  const isWelcome = messages.length === 0 && Boolean(welcome);
  const lastMessage = messages[messages.length - 1];
  const showFallbackEllipsis = streaming && lastMessage?.role !== 'assistant';

  return (
    <div className={['rl-chat', compact && 'rl-chat--compact'].filter(Boolean).join(' ')}>
      <div
        className={['rl-message-list', isWelcome && 'rl-message-list--welcome'].filter(Boolean).join(' ')}
        ref={messageListRef}
        onScroll={onMessageListScroll}
      >
        <div className="rl-chat-column">
          {isWelcome ? (
            <div className="rl-chat-welcome">{welcome}</div>
          ) : (
            <>
              {messages.map((msg, i) => {
                const isLast = i === messages.length - 1;
                const isStreamingAssistant = streaming && isLast && msg.role === 'assistant';
                const showEllipsis = isStreamingAssistant && !msg.content.trim();
                const statusHint =
                  isStreamingAssistant
                  && msg.content.trim()
                  && isToolStreamStatus(msg.streamStatus)
                    ? msg.streamStatus
                    : null;
                const refs = msg.references ?? [];
                const materialRefs = referencesForMaterials(msg.content, refs);
                const showMaterials = msg.role === 'assistant' && materialRefs.length > 0;
                const showMaterialsChip = showMaterials && isStreamingAssistant;
                const showFooter = msg.role === 'assistant'
                  && !isStreamingAssistant
                  && (msg.content.trim() || showMaterials);
                const canRegenerateMessage = Boolean(onRegenerate && canRegenerate && (msg.id || isLast));

                return (
                  <div
                    key={msg.id ?? `${msg.role}-${i}`}
                    className={[
                      'rl-message',
                      msg.role === 'user' ? 'rl-message--user' : 'rl-message--assistant',
                    ].join(' ')}
                  >
                    {msg.role === 'user' ? (
                      <div className="rl-bubble rl-bubble--user">{msg.content}</div>
                    ) : (
                      <div className="rl-assistant-body">
                        {statusHint && (
                          <p className="rl-chat-stream-status" data-testid="chat-stream-status">
                            {statusHint}
                          </p>
                        )}
                        <StreamingEllipsis visible={showEllipsis} />
                        {msg.content && (
                          <CitationMarkdown
                            content={msg.content}
                            references={filterUserVisibleReferences(refs)}
                            fetchDocumentTitle={fetchDocumentTitle}
                            fetchDocumentExcerpt={fetchDocumentExcerpt}
                          />
                        )}
                      </div>
                    )}
                    {showMaterialsChip && (
                      <div className="rl-message-footer rl-chat-secondary--enter">
                        <div className="rl-message-actions">
                          <button
                            type="button"
                            className="rl-chat-chip rl-chat-chip--enter"
                            onClick={() => openMaterials(materialRefs, msg.content, msg.webSearchAttempted)}
                          >
                            {referenceSummary(materialRefs)}
                            <ChevronRight size={12} aria-hidden="true" />
                          </button>
                        </div>
                      </div>
                    )}
                    {showFooter && (
                      <div className="rl-message-footer rl-chat-secondary--enter">
                        <div className="rl-message-actions">
                          {onCopy && (
                            <button
                              type="button"
                              className="rl-icon-btn"
                              title="复制"
                              aria-label="复制"
                              onClick={() => onCopy(msg.content)}
                            >
                              <Copy size={16} />
                            </button>
                          )}
                          {canRegenerateMessage && (
                            <button
                              type="button"
                              className="rl-icon-btn"
                              title="重新生成"
                              aria-label="重新生成"
                              disabled={streaming}
                              onClick={() => onRegenerate?.(msg.id)}
                            >
                              <RotateCcw size={16} />
                            </button>
                          )}
                          {showMaterials && (
                            <button
                              type="button"
                              className="rl-chat-chip rl-chat-chip--enter"
                              onClick={() => openMaterials(materialRefs, msg.content, msg.webSearchAttempted)}
                            >
                              {referenceSummary(materialRefs)}
                              <ChevronRight size={12} aria-hidden="true" />
                            </button>
                          )}
                        </div>
                        <span className="rl-message-footer__ai">本回答由 AI 生成</span>
                      </div>
                    )}
                  </div>
                );
              })}
              {showFallbackEllipsis && (
                <div className="rl-message rl-message--assistant">
                  <div className="rl-assistant-body">
                    <StreamingEllipsis visible />
                  </div>
                </div>
              )}
              {recommendQuestions.length > 0 && !streaming && onRecommendClick && (
                <div className="rl-chat-secondary" data-testid="recommend-block">
                  <p className="rl-chat-secondary__label">推荐追问</p>
                  <div className="rl-chat-chip-row" data-testid="recommend-chips">
                    {recommendQuestions.map((question) => (
                      <button
                        key={question}
                        type="button"
                        className="rl-chat-chip rl-chat-chip--enter"
                        onClick={() => onRecommendClick(question)}
                      >
                        {question}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="rl-composer-wrap">
        <div className="rl-chat-column">
          <form className="rl-composer-gradient" onSubmit={onSubmit}>
            <div className="rl-composer-inner">
              <textarea
                className="rl-composer__textarea"
                value={input}
                onChange={(e) => onInputChange(e.target.value)}
                onKeyDown={onTextareaKeyDown}
                placeholder="描述您的法律问题…"
                rows={2}
                disabled={streaming}
              />
              <div className="rl-composer__footer">
                <div className="rl-composer__links">
                  <button type="button" className="rl-composer__link" disabled>
                    <Paperclip size={14} style={{ marginRight: 4, verticalAlign: -2 }} />
                    附件
                  </button>
                </div>
                <div className="rl-composer__meta">
                  <button
                    type="submit"
                    className="rl-composer__send"
                    disabled={streaming || !input.trim()}
                    aria-label="发送"
                  >
                    <Send />
                  </button>
                </div>
              </div>
            </div>
          </form>
          {disclaimer && <p className="rl-disclaimer">{disclaimer}</p>}
        </div>
      </div>

      <ReferenceMaterialsPanel
        open={materialsOpen}
        onClose={() => setMaterialsOpen(false)}
        references={activeMaterials}
        content={activeMaterialsContent}
        webSearchAttempted={activeWebSearchAttempted}
        catalogMode={hasCatalogDocuments(activeMaterials)}
        fetchDocumentExcerpt={fetchDocumentExcerpt}
      />
    </div>
  );
}
