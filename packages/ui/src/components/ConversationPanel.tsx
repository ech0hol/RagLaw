import { useEffect, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Paperclip, Send } from 'lucide-react';
import { MarkdownContent } from './MarkdownContent';
import { ReferenceList, type ChatReference } from './ReferenceList';

export type ChatMessage = {
  id?: string;
  role: string;
  content: string;
  references?: ChatReference[];
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
  statusMessage?: string;
  thinkingSteps?: string[];
  recommendQuestions?: string[];
  onRecommendClick?: (question: string) => void;
  onCopy?: (content: string) => void;
  onRegenerate?: () => void;
  canRegenerate?: boolean;
  onMessageListScroll?: () => void;
};

function ThinkingBlock({ steps, current }: { steps: string[]; current?: string }) {
  const completed = current && steps.length > 0 && steps[steps.length - 1] === current
    ? steps.slice(0, -1)
    : steps;

  return (
    <div className="rl-thinking" data-testid="chat-thinking">
      {completed.map((step) => (
        <p key={step} className="rl-thinking__step">{step}</p>
      ))}
      {current && (
        <p className="rl-thinking__current">
          <span className="rl-thinking__pulse">{current}</span>
        </p>
      )}
    </div>
  );
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
  statusMessage,
  thinkingSteps = [],
  recommendQuestions = [],
  onRecommendClick,
  onCopy,
  onRegenerate,
  canRegenerate = false,
  onMessageListScroll,
}: ConversationPanelProps) {
  const [recommendOpen, setRecommendOpen] = useState(false);

  useEffect(() => {
    setRecommendOpen(false);
  }, [recommendQuestions]);

  function onTextareaKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!streaming && input.trim()) {
        e.currentTarget.form?.requestSubmit();
      }
    }
  }

  return (
    <div className="rl-chat">
      {messages.length === 0 && welcome ? (
        <div className="rl-chat-welcome">{welcome}</div>
      ) : (
        <div className="rl-message-list" ref={messageListRef} onScroll={onMessageListScroll}>
          {messages.map((msg, i) => {
            const isLast = i === messages.length - 1;
            const isStreamingAssistant = streaming && isLast && msg.role === 'assistant';
            const showThinking = isStreamingAssistant && (statusMessage || thinkingSteps.length > 0);

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
                    {showThinking && (
                      <ThinkingBlock
                        steps={thinkingSteps}
                        current={statusMessage || undefined}
                      />
                    )}
                    {msg.content && <MarkdownContent content={msg.content} />}
                  </div>
                )}
                {msg.role === 'assistant' && msg.references && msg.references.length > 0 && (
                  <ReferenceList references={msg.references} />
                )}
                {msg.role === 'assistant' && msg.content && !streaming && (
                  <div className="rl-message-actions">
                    {onCopy && (
                      <button type="button" className="rl-message-actions__btn" onClick={() => onCopy(msg.content)}>
                        复制
                      </button>
                    )}
                    {onRegenerate && canRegenerate && i === messages.length - 1 && (
                      <button type="button" className="rl-message-actions__btn" onClick={onRegenerate}>
                        重新生成
                      </button>
                    )}
                  </div>
                )}
              </div>
            );
          })}
          {recommendQuestions.length > 0 && !streaming && onRecommendClick && (
            <div className="rl-recommend-block">
              <button
                type="button"
                className="rl-recommend-toggle"
                onClick={() => setRecommendOpen((v) => !v)}
              >
                {recommendOpen ? '收起推荐' : '推荐问题'}
              </button>
              {recommendOpen && (
                <div className="rl-recommend-chips" data-testid="recommend-chips">
                  {recommendQuestions.map((question) => (
                    <button
                      key={question}
                      type="button"
                      className="rl-recommend-chip"
                      onClick={() => onRecommendClick(question)}
                    >
                      {question}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      <div className="rl-composer-wrap">
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
  );
}
