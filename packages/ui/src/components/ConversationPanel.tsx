import type { FormEvent, ReactNode } from 'react';
import { Paperclip, Mic, BookOpen, Send } from 'lucide-react';
import { ReferenceList, type ChatReference } from './ReferenceList';

export type ChatMessage = {
  id?: string;
  role: string;
  content: string;
  references?: ChatReference[];
};

const MAX_CHARS = 3000;

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
  recommendQuestions?: string[];
  onRecommendClick?: (question: string) => void;
};

export function ConversationPanel({
  messages,
  input,
  streaming,
  onInputChange,
  onSubmit,
  welcome,
  disclaimer = 'AI 辅助参考，不构成法律意见。',
  messageListRef,
  statusMessage,
  recommendQuestions = [],
  onRecommendClick,
}: ConversationPanelProps) {
  const charCount = input.length;

  return (
    <div className="rl-chat">
      {messages.length === 0 && welcome ? (
        <div className="rl-chat-welcome">{welcome}</div>
      ) : (
        <div className="rl-message-list" ref={messageListRef}>
          {statusMessage && streaming && (
            <p className="rl-chat-status" data-testid="chat-status">{statusMessage}</p>
          )}
          {messages.map((msg, i) => (
            <div
              key={msg.id ?? `${msg.role}-${i}`}
              className={[
                'rl-message',
                msg.role === 'user' ? 'rl-message--user' : 'rl-message--assistant',
              ].join(' ')}
            >
              <div
                className={[
                  'rl-bubble',
                  msg.role === 'user' ? 'rl-bubble--user' : 'rl-bubble--assistant',
                ].join(' ')}
              >
                {msg.content || (streaming && msg.role === 'assistant' ? '…' : '')}
              </div>
              {msg.role === 'assistant' && msg.references && msg.references.length > 0 && (
                <ReferenceList references={msg.references} />
              )}
            </div>
          ))}
        </div>
      )}

      <div className="rl-composer-wrap">
        <form className="rl-composer-gradient" onSubmit={onSubmit}>
          <div className="rl-composer-inner">
            <textarea
              className="rl-composer__textarea"
              value={input}
              onChange={(e) => onInputChange(e.target.value)}
              placeholder="描述您的法律问题…"
              rows={2}
              disabled={streaming}
              maxLength={MAX_CHARS}
            />
            <div className="rl-composer__footer">
              <div className="rl-composer__links">
                <button type="button" className="rl-composer__link" disabled>
                  <Paperclip size={14} style={{ marginRight: 4, verticalAlign: -2 }} />
                  附件
                </button>
                <button type="button" className="rl-composer__link" disabled>
                  <Mic size={14} style={{ marginRight: 4, verticalAlign: -2 }} />
                  语音
                </button>
                <button type="button" className="rl-composer__link" disabled>
                  <BookOpen size={14} style={{ marginRight: 4, verticalAlign: -2 }} />
                  提示词
                </button>
              </div>
              <div className="rl-composer__meta">
                <span className="rl-composer__count">{charCount} / {MAX_CHARS}</span>
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
        {recommendQuestions.length > 0 && !streaming && onRecommendClick && (
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
        {disclaimer && <p className="rl-disclaimer">{disclaimer}</p>}
      </div>
    </div>
  );
}
