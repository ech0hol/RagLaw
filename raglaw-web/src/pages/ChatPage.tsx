import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { BookOpen, FileText, Gavel, Scale } from 'lucide-react';
import {
  ConversationHistoryDropdown,
  ConversationPanel,
  QuickActionCard,
  type ConversationItem,
} from '@raglaw/ui';
import { api, deleteConversation } from '../lib/api';
import { confirmIrreversibleDelete } from '../lib/confirmDelete';
import { fetchDocumentExcerpt, fetchDocumentTitle, useAguiChat } from '../hooks/useAguiChat';

const AGENT_LABELS: Record<string, string> = {
  GENERAL: '通用法律助手',
  STATUTE: '法规助手',
  CASE: '案例助手',
  CONTRACT: '合同助手',
};

const QUICK_PROMPTS = [
  { text: '劳动合同解除有哪些法定情形？', color: 'yellow' as const, icon: <Gavel size={18} /> },
  { text: '借款合同未约定利息如何认定？', color: 'blue' as const, icon: <Scale size={18} /> },
  { text: '公司拖欠工资如何维权？', color: 'green' as const, icon: <FileText size={18} /> },
  { text: '房屋租赁违约责任的常见约定有哪些？', color: 'pink' as const, icon: <BookOpen size={18} /> },
];

type ConversationDto = { id: string; title: string; updatedAt: string; agentCode?: string };

type ChatPageProps = { fixedAgentCode?: string };

export function ChatPage({ fixedAgentCode }: ChatPageProps) {
  const { agentCode: routeAgentCode } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const conversationId = searchParams.get('c');
  const contextDoc = searchParams.get('contextDoc');

  const expertAgent = fixedAgentCode ?? routeAgentCode;
  const useExplicitAgent = Boolean(expertAgent && expertAgent !== 'GENERAL');
  const pageTitle = expertAgent ? AGENT_LABELS[expertAgent] ?? expertAgent : '智能对话';

  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [convLoading, setConvLoading] = useState(true);
  const [searchFilter, setSearchFilter] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);

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

  const handleConversationIdChange = useCallback((id: string) => {
    setSearchParams(id ? { c: id } : {});
  }, [setSearchParams]);

  const chat = useAguiChat({
    agentCode: expertAgent,
    contextDocumentId: contextDoc,
    conversationId,
    onConversationIdChange: handleConversationIdChange,
    useExplicitAgent,
  });

  const selectConversation = useCallback((id: string) => {
    chat.pinToBottom();
    setHistoryOpen(false);
    setSearchParams({ c: id });
  }, [chat, setSearchParams]);

  const newChat = useCallback(() => {
    chat.resetConversation();
  }, [chat]);

  function copyContent(content: string) {
    void navigator.clipboard.writeText(content);
  }

  async function removeConversation(id: string) {
    if (!confirmIrreversibleDelete('该对话')) return;
    setHistoryOpen(false);
    const res = await deleteConversation(id);
    if (!res.success) return;
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
          onDelete={(id) => void removeConversation(id)}
          loading={convLoading}
          searchValue={searchFilter}
          onSearchChange={setSearchFilter}
        />
        {expertAgent && expertAgent !== 'GENERAL' && (
          <span className="rl-chat-topbar__title">{pageTitle}</span>
        )}
      </div>
      <ConversationPanel
        messages={chat.messages}
        input={chat.input}
        streaming={chat.streaming}
        onInputChange={chat.setInput}
        onSubmit={chat.handleSubmit}
        recommendQuestions={chat.recommendQuestions}
        onRecommendClick={(q) => void chat.sendMessage(q)}
        onCopy={copyContent}
        onRegenerate={(id) => void chat.regenerateAt(id)}
        canRegenerate={chat.canRegenerate}
        messageListRef={chat.messageListRef}
        onMessageListScroll={chat.onMessageListScroll}
        fetchDocumentTitle={fetchDocumentTitle}
        fetchDocumentExcerpt={fetchDocumentExcerpt}
        disclaimer="AI 回答仅供参考，不构成法律意见。"
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
                  onClick={() => void chat.sendMessage(prompt.text)}
                >
                  {prompt.text}
                </QuickActionCard>
              ))}
            </div>
          </>
        }
      />
    </div>
  );
}
