import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { BookOpen, FileText, Gavel, Scale } from 'lucide-react';
import {
  ConfirmDialog,
  ConversationHistoryDropdown,
  ConversationPanel,
  QuickActionCard,
  type ChatMessage,
  type ChatReference,
  type ConversationItem,
} from '@raglaw/ui';
import { api, deleteConversation, getToken } from '../lib/api';
import { useStickyScroll } from '../hooks/useStickyScroll';

const AGENT_LABELS: Record<string, string> = {
  GENERAL: '通用法律助手',
  STATUTE_CIVIL: '民法商法规范助手',
  CASE_CIVIL: '民事案例助手',
  CONTRACT_GENERAL: '合同审查助手',
};

const QUICK_PROMPTS = [
  { text: '劳动合同解除有哪些法定情形？', color: 'yellow' as const, icon: <Gavel size={18} /> },
  { text: '借款合同未约定利息如何认定？', color: 'blue' as const, icon: <Scale size={18} /> },
  { text: '公司拖欠工资如何维权？', color: 'green' as const, icon: <FileText size={18} /> },
  { text: '房屋租赁违约责任的常见约定有哪些？', color: 'pink' as const, icon: <BookOpen size={18} /> },
];

type ConversationDto = { id: string; title: string; updatedAt: string; agentCode?: string };
type MessageDto = { id: string; role: string; content: string; citationsJson?: string | null };

function parseReferences(citationsJson?: string | null): ChatReference[] | undefined {
  if (!citationsJson) return undefined;
  try {
    const parsed = JSON.parse(citationsJson) as ChatReference[];
    return Array.isArray(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

type ChatPageProps = { fixedAgentCode?: string };

export function ChatPage({ fixedAgentCode }: ChatPageProps) {
  const { agentCode: routeAgentCode } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const conversationId = searchParams.get('c');

  const expertAgent = fixedAgentCode ?? routeAgentCode;
  const useExplicitAgent = Boolean(expertAgent && expertAgent !== 'GENERAL');

  const pageTitle = expertAgent ? AGENT_LABELS[expertAgent] ?? expertAgent : '智能对话';

  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [convLoading, setConvLoading] = useState(true);
  const [searchFilter, setSearchFilter] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [thinkingSteps, setThinkingSteps] = useState<string[]>([]);
  const [recommendQuestions, setRecommendQuestions] = useState<string[]>([]);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const messageListRef = useRef<HTMLDivElement>(null);
  const skipLoadRef = useRef(false);

  const { onScroll: onMessageListScroll, pinToBottom } = useStickyScroll(messageListRef, [
    messages,
    streaming,
    statusMessage,
    thinkingSteps,
  ]);

  const canRegenerate = useMemo(
    () => messages.some((m) => m.role === 'user') && messages.some((m) => m.role === 'assistant'),
    [messages],
  );

  const refreshConversations = useCallback(async () => {
    const res = await api<ConversationDto[]>('/api/v1/conversations');
    if (res.success) {
      setConversations(res.data.map((c) => ({ id: c.id, title: c.title, updatedAt: c.updatedAt })));
    }
    setConvLoading(false);
  }, []);

  useEffect(() => { void refreshConversations(); }, [refreshConversations]);

  const filteredConversations = useMemo(() => {
    if (!searchFilter.trim()) return conversations;
    const q = searchFilter.toLowerCase();
    return conversations.filter((c) => (c.title || '新对话').toLowerCase().includes(q));
  }, [conversations, searchFilter]);

  const loadMessages = useCallback(async (id: string) => {
    const res = await api<MessageDto[]>(`/api/v1/conversations/${id}/messages`);
    if (res.success) {
      setMessages(res.data.map((m) => ({
        id: m.id,
        role: m.role,
        content: m.content,
        references: parseReferences(m.citationsJson),
      })));
    }
  }, []);

  useEffect(() => {
    if (!conversationId) {
      setMessages([]);
      setHistoryOpen(false);
      return;
    }
    if (skipLoadRef.current) {
      skipLoadRef.current = false;
      return;
    }
    setHistoryOpen(false);
    void loadMessages(conversationId);
  }, [conversationId, loadMessages]);

  const selectConversation = useCallback((id: string) => {
    skipLoadRef.current = false;
    pinToBottom();
    setHistoryOpen(false);
    setSearchParams({ c: id });
  }, [setSearchParams, pinToBottom]);

  const newChat = useCallback(() => {
    skipLoadRef.current = false;
    pinToBottom();
    setHistoryOpen(false);
    setSearchParams({});
    setMessages([]);
    setInput('');
    setRecommendQuestions([]);
  }, [setSearchParams, pinToBottom]);

  function agentPayload(): Record<string, string> {
    if (useExplicitAgent && expertAgent) {
      return { agentCode: expertAgent };
    }
    return {};
  }

  async function ensureConversation(): Promise<string> {
    if (conversationId) return conversationId;
    const res = await api<ConversationDto>('/api/v1/conversations', {
      method: 'POST',
      body: JSON.stringify(agentPayload()),
    });
    if (!res.success) throw new Error(res.error?.message ?? '创建会话失败');
    skipLoadRef.current = true;
    setSearchParams({ c: res.data.id });
    void refreshConversations();
    return res.data.id;
  }

  async function streamAgui(options: { regenerate?: boolean; message?: string }) {
    const convId = await ensureConversation();
    const token = getToken();
    const res = await fetch('/api/v1/agui/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        conversationId: convId,
        message: options.regenerate ? undefined : options.message,
        regenerate: options.regenerate ?? false,
        ...agentPayload(),
      }),
    });
    if (!res.ok) throw new Error(`对话请求失败 (${res.status})`);

    const reader = res.body?.getReader();
    if (!reader) throw new Error('无法读取流式响应');

    const decoder = new TextDecoder();
    let assistant = '';
    const references: ChatReference[] = [];
    setStatusMessage('');
    setThinkingSteps([]);
    setRecommendQuestions([]);
    setMessages((prev) => [...prev, { role: 'assistant', content: '', references: [] }]);

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
        if (event === 'status' && data) {
          const parsed = JSON.parse(data) as { message?: string };
          const msg = parsed.message ?? '';
          if (msg) {
            setThinkingSteps((prev) => {
              if (prev.length === 0 || prev[prev.length - 1] !== msg) {
                return [...prev, msg];
              }
              return prev;
            });
            setStatusMessage(msg);
          }
        } else if (event === 'text' && data) {
          const parsed = JSON.parse(data) as { delta?: string };
          if (!assistant) {
            setStatusMessage('');
          }
          assistant += parsed.delta ?? '';
          setMessages((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            next[next.length - 1] = { ...last, content: assistant, references: [...references] };
            return next;
          });
        } else if (event === 'reference' && data) {
          const parsed = JSON.parse(data) as ChatReference;
          references.push(parsed);
          setMessages((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            next[next.length - 1] = { ...last, references: [...references] };
            return next;
          });
        } else if (event === 'recommend' && data) {
          const parsed = JSON.parse(data) as { questions?: string[] };
          setRecommendQuestions(parsed.questions ?? []);
        } else if (event === 'error' && data) {
          const parsed = JSON.parse(data) as { message?: string };
          throw new Error(parsed.message ?? '流式响应错误');
        }
      }
    }
    setStatusMessage('');
    void refreshConversations();
    void loadMessages(convId);
  }

  async function sendMessage(text: string) {
    if (!text.trim() || streaming) return;
    pinToBottom();
    setStreaming(true);
    setMessages((prev) => [...prev, { id: `user-${Date.now()}`, role: 'user', content: text }]);
    setInput('');
    try {
      await streamAgui({ message: text });
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'assistant', content: err instanceof Error ? err.message : '发送失败' }]);
    } finally {
      setStreaming(false);
      setThinkingSteps([]);
      setStatusMessage('');
    }
  }

  async function regenerateLast() {
    if (streaming || !canRegenerate) return;
    pinToBottom();
    setStreaming(true);
    try {
      await streamAgui({ regenerate: true });
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'assistant', content: err instanceof Error ? err.message : '重新生成失败' }]);
    } finally {
      setStreaming(false);
      setThinkingSteps([]);
      setStatusMessage('');
    }
  }

  function copyContent(content: string) {
    void navigator.clipboard.writeText(content);
  }

  function requestDeleteConversation(id: string) {
    setDeleteTargetId(id);
    setHistoryOpen(false);
  }

  async function confirmDeleteConversation() {
    if (!deleteTargetId) return;
    const id = deleteTargetId;
    setDeleteLoading(true);
    const res = await deleteConversation(id);
    setDeleteLoading(false);
    if (!res.success) return;
    setDeleteTargetId(null);
    if (conversationId === id) {
      newChat();
    }
    void refreshConversations();
  }

  return (
    <div className="rl-chat-page">
      <div className="rl-chat-topbar">
        <ConversationHistoryDropdown
          open={historyOpen}
          onOpenChange={setHistoryOpen}
          conversations={filteredConversations}
          selectedId={conversationId}
          onSelect={selectConversation}
          onNewChat={newChat}
          onDelete={requestDeleteConversation}
          loading={convLoading}
          searchValue={searchFilter}
          onSearchChange={setSearchFilter}
        />
        {expertAgent && expertAgent !== 'GENERAL' && (
          <span className="rl-chat-topbar__title">{pageTitle}</span>
        )}
      </div>
      <ConversationPanel
        messages={messages}
        input={input}
        streaming={streaming}
        onInputChange={setInput}
        onSubmit={(e: FormEvent) => { e.preventDefault(); void sendMessage(input); }}
        statusMessage={statusMessage}
        thinkingSteps={thinkingSteps}
        recommendQuestions={recommendQuestions}
        onRecommendClick={(q) => void sendMessage(q)}
        onCopy={copyContent}
        onRegenerate={() => void regenerateLast()}
        canRegenerate={canRegenerate}
        messageListRef={messageListRef}
        onMessageListScroll={onMessageListScroll}
        welcome={
          <>
            <h1>欢迎使用 RagLaw</h1>
            <p>输入问题，或选择快捷卡片开始对话</p>
            <div className="rl-quick-cards">
              {QUICK_PROMPTS.map((prompt, index) => (
                <QuickActionCard
                  key={prompt.text}
                  color={prompt.color}
                  icon={prompt.icon}
                  delayIndex={index}
                  onClick={() => void sendMessage(prompt.text)}
                >
                  {prompt.text}
                </QuickActionCard>
              ))}
            </div>
          </>
        }
      />
      <ConfirmDialog
        open={deleteTargetId !== null}
        title="删除对话"
        description="删除后无法恢复，是否继续？"
        confirmLabel="删除"
        variant="danger"
        loading={deleteLoading}
        onConfirm={() => void confirmDeleteConversation()}
        onCancel={() => !deleteLoading && setDeleteTargetId(null)}
      />
    </div>
  );
}
