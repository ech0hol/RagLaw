import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ChatMessage, ChatReference } from '@raglaw/ui';
import { preserveStreamedAssistant, mergeStreamingMessages } from './aguiChatMessages';
import { api, getToken } from '../lib/api';
import { useStickyScroll } from './useStickyScroll';

const STREAM_TIMEOUT_MS = 90_000;
const SKIP_LOAD_AFTER_CREATE = 2;

function isToolStreamStatus(status: string | null | undefined): boolean {
  if (!status) return false;
  return status.includes('正在检索知识库') || status.includes('正在联网检索');
}

type ConversationDto = { id: string; title: string; updatedAt: string; agentCode?: string };
type MessageDto = { id: string; role: string; content: string; citationsJson?: string | null };

export type UseAguiChatOptions = {
  agentCode?: string | null;
  contextDocumentId?: string | null;
  conversationId?: string | null;
  onConversationIdChange?: (id: string) => void;
  useExplicitAgent?: boolean;
};

function parseReferences(citationsJson?: string | null): ChatReference[] | undefined {
  if (!citationsJson) return undefined;
  try {
    const parsed = JSON.parse(citationsJson) as ChatReference[];
    if (!Array.isArray(parsed)) return undefined;
    return parsed.map((ref) => ({
      ...ref,
      source: ref.source ?? 'knowledge',
      title: ref.title ?? ref.path,
    }));
  } catch {
    return undefined;
  }
}

export function useAguiChat(options: UseAguiChatOptions) {
  const {
    agentCode,
    contextDocumentId,
    conversationId: externalConversationId,
    onConversationIdChange,
    useExplicitAgent = Boolean(agentCode && agentCode !== 'GENERAL'),
  } = options;

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [recommendQuestions, setRecommendQuestions] = useState<string[]>([]);
  const messageListRef = useRef<HTMLDivElement>(null);
  const skipLoadRef = useRef(0);
  const streamingRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const conversationId = externalConversationId ?? null;

  function setStreamingState(value: boolean) {
    streamingRef.current = value;
    setStreaming(value);
  }

  const { onScroll: onMessageListScroll, pinToBottom } = useStickyScroll(messageListRef, [
    messages,
    streaming,
  ]);

  const canRegenerate = useMemo(
    () => messages.some((m) => m.role === 'user') && messages.some((m) => m.role === 'assistant'),
    [messages],
  );

  const loadMessages = useCallback(async (id: string) => {
    const generation = loadGenerationRef.current;
    const res = await api<MessageDto[]>(`/api/v1/conversations/${id}/messages`);
    if (generation !== loadGenerationRef.current) {
      return;
    }
    if (res.success) {
      const fromApi = res.data.map((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        references: parseReferences(m.citationsJson),
      }));
      setMessages((prev) => {
        const merged = mergeStreamingMessages(prev, fromApi, streamingRef.current);
        return preserveStreamedAssistant(prev, merged);
      });
    }
  }, []);

  useEffect(() => {
    if (!conversationId) {
      if (!streamingRef.current) {
        setMessages([]);
      }
      return;
    }
    if (streamingRef.current) {
      return;
    }
    if (skipLoadRef.current > 0) {
      skipLoadRef.current -= 1;
      return;
    }
    void loadMessages(conversationId);
  }, [conversationId, loadMessages]);

  const resetConversation = useCallback(() => {
    skipLoadRef.current = 0;
    pinToBottom();
    onConversationIdChange?.('');
    setMessages([]);
    setInput('');
    setRecommendQuestions([]);
  }, [onConversationIdChange, pinToBottom]);

  function agentPayload(): Record<string, string> {
    const payload: Record<string, string> = {};
    if (useExplicitAgent && agentCode) {
      payload.agentCode = agentCode;
    }
    if (contextDocumentId) {
      payload.contextDocumentId = contextDocumentId;
    }
    return payload;
  }

  async function ensureConversation(): Promise<string> {
    if (conversationId) return conversationId;
    const res = await api<ConversationDto>('/api/v1/conversations', {
      method: 'POST',
      body: JSON.stringify(agentPayload()),
    });
    if (!res.success) throw new Error(res.error?.message ?? '创建会话失败');
    skipLoadRef.current = SKIP_LOAD_AFTER_CREATE;
    onConversationIdChange?.(res.data.id);
    return res.data.id;
  }

  function applyStreamError(err: unknown, fallback: string) {
    const message = err instanceof Error ? err.message : fallback;
    const friendly = message.toLowerCase().includes('sql') || message.toLowerCase().includes('jdbc')
      ? '服务暂时异常，请稍后重试'
      : message;
    setMessages((prev) => {
      const next = [...prev];
      const lastIdx = next.length - 1;
      if (lastIdx >= 0 && next[lastIdx].role === 'assistant') {
        const existing = next[lastIdx].content?.trim();
        if (existing) {
          return next;
        }
        next[lastIdx] = {
          ...next[lastIdx],
          content: friendly,
          streamStatus: null,
        };
        return next;
      }
      return [...next, { role: 'assistant', content: friendly }];
    });
  }

  async function streamAgui(streamOptions: {
    regenerate?: boolean;
    message?: string;
    regenerateFromMessageId?: string;
  }) {
    const convId = await ensureConversation();
    const token = getToken();
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), STREAM_TIMEOUT_MS);

    try {
      const res = await fetch('/api/v1/agui/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          conversationId: convId,
          message: streamOptions.regenerate ? undefined : streamOptions.message,
          regenerate: streamOptions.regenerate ?? false,
          regenerateFromMessageId: streamOptions.regenerate
            ? streamOptions.regenerateFromMessageId
            : undefined,
          ...agentPayload(),
        }),
        signal: controller.signal,
      });
      if (!res.ok) throw new Error(`对话请求失败 (${res.status})`);

      const reader = res.body?.getReader();
      if (!reader) throw new Error('无法读取流式响应');

      const decoder = new TextDecoder();
      let assistant = '';
      const references: ChatReference[] = [];
      let streamStatus: string | null = null;
      let webSearchAttempted = false;
      setRecommendQuestions([]);
      setMessages((prev) => {
        const last = prev[prev.length - 1];
        if (last?.role === 'assistant' && !last.content?.trim()) {
          return prev;
        }
        return [...prev, { role: 'assistant', content: '', references: [] }];
      });

      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';
        for (const part of parts) {
          const lines = part.split('\n');
          let event = '';
          let data = '';
          for (const line of lines) {
            if (line.startsWith('event:')) event = line.slice(6).trim();
            else if (line.startsWith('data:')) data = line.slice(5).trim();
          }
          if (event === 'text' && data) {
            const parsed = JSON.parse(data) as { delta?: string };
            assistant += parsed.delta ?? '';
            streamStatus = null;
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              next[next.length - 1] = {
                ...last,
                content: assistant,
                references: [...references],
                streamStatus: null,
                webSearchAttempted,
              };
              return next;
            });
          } else if (event === 'status' && data) {
            const parsed = JSON.parse(data) as { message?: string };
            streamStatus = parsed.message ?? null;
            if (streamStatus?.includes('联网')) {
              webSearchAttempted = true;
            }
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              next[next.length - 1] = {
                ...last,
                content: assistant,
                references: [...references],
                streamStatus,
                webSearchAttempted,
              };
              return next;
            });
          } else if (event === 'reference' && data) {
            const parsed = JSON.parse(data) as ChatReference;
            const normalized = {
              ...parsed,
              source: parsed.source ?? 'knowledge',
              title: parsed.title ?? parsed.path,
            };
            if (!references.some((ref) => ref.chunkId === normalized.chunkId)) {
              references.push(normalized);
            }
            if (parsed.source === 'web') {
              webSearchAttempted = true;
            }
            if (isToolStreamStatus(streamStatus)) {
              streamStatus = null;
            }
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              next[next.length - 1] = {
                ...last,
                references: [...references],
                streamStatus,
                webSearchAttempted,
              };
              return next;
            });
          } else if (event === 'text_reset') {
            assistant = '';
            streamStatus = '正在整理回答…';
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (last?.role === 'assistant') {
                next[next.length - 1] = {
                  ...last,
                  content: '',
                  references: [...references],
                  streamStatus,
                  webSearchAttempted,
                };
              }
              return next;
            });
          } else if (event === 'recommend' && data) {
            const parsed = JSON.parse(data) as { questions?: string[] };
            setRecommendQuestions(parsed.questions ?? []);
          } else if (event === 'done' && data) {
            const parsed = JSON.parse(data) as { messageId?: string; content?: string };
            if (parsed.content?.trim()) {
              assistant = parsed.content;
            }
            if (parsed.messageId || parsed.content) {
              setMessages((prev) => {
                const next = [...prev];
                const last = next[next.length - 1];
                if (last?.role === 'assistant') {
                  next[next.length - 1] = {
                    ...last,
                    ...(parsed.messageId ? { id: parsed.messageId } : {}),
                    ...(parsed.content?.trim() ? { content: parsed.content } : {}),
                    streamStatus: null,
                  };
                }
                return next;
              });
            }
          } else if (event === 'error' && data) {
            const parsed = JSON.parse(data) as { message?: string };
            throw new Error(parsed.message ?? '流式响应错误');
          }
        }
      }

      if (!assistant.trim()) {
        throw new Error('模型未返回内容，请重试');
      }

      streamingRef.current = false;
      void loadMessages(convId);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        throw new Error('请求超时，请重试');
      }
      throw err;
    } finally {
      window.clearTimeout(timeoutId);
      setMessages((prev) => {
        const last = prev[prev.length - 1];
        if (last?.role === 'assistant' && last.streamStatus) {
          const next = [...prev];
          next[next.length - 1] = { ...last, streamStatus: null };
          return next;
        }
        return prev;
      });
    }
  }

  async function sendMessage(text: string) {
    if (!text.trim() || streaming) return;
    loadGenerationRef.current += 1;
    pinToBottom();
    setStreamingState(true);
    setMessages((prev) => [
      ...prev,
      { id: `user-${Date.now()}`, role: 'user', content: text },
      { role: 'assistant', content: '', references: [] },
    ]);
    setInput('');
    try {
      await streamAgui({ message: text });
    } catch (err) {
      applyStreamError(err, '发送失败');
    } finally {
      setStreamingState(false);
    }
  }

  async function regenerateAt(assistantMessageId?: string) {
    if (streaming || !canRegenerate) return;
    loadGenerationRef.current += 1;
    pinToBottom();
    setStreamingState(true);
    setRecommendQuestions([]);
    setMessages((prev) => {
      if (assistantMessageId) {
        const idx = prev.findIndex((m) => m.id === assistantMessageId && m.role === 'assistant');
        if (idx >= 0) {
          const kept = prev.slice(0, idx);
          const target = prev[idx];
          return [...kept, { ...target, content: '', references: [], streamStatus: null }];
        }
      }
      const next = [...prev];
      const lastIdx = next.length - 1;
      if (lastIdx >= 0 && next[lastIdx].role === 'assistant') {
        next[lastIdx] = { ...next[lastIdx], content: '', references: [], streamStatus: null };
        return next;
      }
      return [...next, { role: 'assistant', content: '', references: [] }];
    });
    try {
      await streamAgui({
        regenerate: true,
        regenerateFromMessageId: assistantMessageId,
      });
    } catch (err) {
      applyStreamError(err, '重新生成失败');
    } finally {
      setStreamingState(false);
    }
  }

  async function regenerateLast() {
    await regenerateAt();
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    void sendMessage(input);
  }

  return {
    messages,
    input,
    streaming,
    recommendQuestions,
    messageListRef,
    onMessageListScroll,
    canRegenerate,
    setInput,
    sendMessage,
    regenerateLast,
    regenerateAt,
    resetConversation,
    handleSubmit,
    pinToBottom,
  };
}

export async function fetchDocumentTitle(documentId: string): Promise<string | null> {
  const token = getToken();
  const res = await fetch(`/api/v1/knowledge/documents/${documentId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) return null;
  const body = await res.json() as { success?: boolean; data?: { document?: { title?: string } } };
  return body.success ? body.data?.document?.title ?? null : null;
}

export async function fetchDocumentExcerpt(documentId: string, anchors: string): Promise<string | null> {
  const token = getToken();
  const params = new URLSearchParams({ anchors });
  const res = await fetch(`/api/v1/knowledge/documents/${documentId}/excerpt?${params.toString()}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) return null;
  const body = await res.json() as { success?: boolean; data?: { excerpt?: string } };
  if (!body.success) return null;
  const excerpt = body.data?.excerpt?.trim();
  return excerpt || null;
}
