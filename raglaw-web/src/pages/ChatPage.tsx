import { FormEvent, useEffect, useRef, useState } from 'react';
import { api, getToken } from '../lib/api';

type Message = { id?: string; role: string; content: string };

const QUICK_PROMPTS = [
  '劳动合同解除有哪些法定情形？',
  '借款合同未约定利息如何认定？',
  '公司拖欠工资如何维权？',
  '房屋租赁违约责任的常见约定有哪些？',
];

export function ChatPage() {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streaming]);

  async function ensureConversation() {
    if (conversationId) {
      return conversationId;
    }
    const res = await api<{ id: string }>('/api/v1/conversations', { method: 'POST', body: '{}' });
    if (!res.success) {
      throw new Error(res.error?.message ?? '创建会话失败');
    }
    setConversationId(res.data.id);
    return res.data.id;
  }

  async function sendMessage(text: string) {
    if (!text.trim() || streaming) {
      return;
    }
    setStreaming(true);
    setMessages((prev) => [...prev, { role: 'user', content: text }]);
    setInput('');

    try {
      const convId = await ensureConversation();
      const token = getToken();
      const res = await fetch('/api/v1/agui/run', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ conversationId: convId, message: text }),
      });

      if (!res.ok) {
        throw new Error(`对话请求失败 (${res.status})`);
      }

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      let assistant = '';
      setMessages((prev) => [...prev, { role: 'assistant', content: '' }]);

      if (!reader) {
        throw new Error('无法读取流式响应');
      }

      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';
        for (const part of parts) {
          const lines = part.split('\n');
          let event = '';
          let data = '';
          for (const line of lines) {
            if (line.startsWith('event:')) {
              event = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              data = line.slice(5).trim();
            }
          }
          if (event === 'text' && data) {
            const parsed = JSON.parse(data) as { delta?: string };
            assistant += parsed.delta ?? '';
            setMessages((prev) => {
              const next = [...prev];
              next[next.length - 1] = { role: 'assistant', content: assistant };
              return next;
            });
          } else if (event === 'error' && data) {
            const parsed = JSON.parse(data) as { message?: string };
            throw new Error(parsed.message ?? '流式响应错误');
          }
        }
      }
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', content: err instanceof Error ? err.message : '发送失败' },
      ]);
    } finally {
      setStreaming(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void sendMessage(input);
  }

  return (
    <div className="chat-page">
      {messages.length === 0 ? (
        <div className="welcome">
          <h1>智能法律咨询</h1>
          <p>输入问题，或选择快捷卡片开始对话</p>
          <div className="quick-cards">
            {QUICK_PROMPTS.map((prompt) => (
              <button key={prompt} type="button" onClick={() => void sendMessage(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="message-list">
          {messages.map((msg, i) => (
            <div key={i} className={`bubble ${msg.role}`}>
              {msg.content || (streaming && msg.role === 'assistant' ? '…' : '')}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      )}

      <form className="composer" onSubmit={onSubmit}>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="描述您的法律问题…"
          rows={2}
          disabled={streaming}
        />
        <button type="submit" disabled={streaming || !input.trim()}>
          发送
        </button>
      </form>
      <p className="disclaimer">AI 辅助参考，不构成法律意见。</p>
    </div>
  );
}
