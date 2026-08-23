import { Trash2 } from 'lucide-react';

export type ConversationItem = {
  id: string;
  title: string;
  updatedAt?: string;
};

type ConversationListProps = {
  conversations: ConversationItem[];
  selectedId?: string | null;
  onSelect: (id: string) => void;
  onNewChat?: () => void;
  onDelete?: (id: string) => void;
  loading?: boolean;
  emptyMessage?: string;
};

function formatTime(value?: string) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const now = new Date();
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

export function ConversationList({
  conversations,
  selectedId,
  onSelect,
  onNewChat,
  onDelete,
  loading,
  emptyMessage = '暂无会话',
}: ConversationListProps) {
  return (
    <div className="rl-conv-list">
      <div className="rl-conv-list__header">
        <span className="rl-conv-list__title">最近对话</span>
        {onNewChat && (
          <button type="button" className="rl-conv-list__new" onClick={onNewChat}>
            新建
          </button>
        )}
      </div>
      <div className="rl-conv-list__items">
        {loading ? (
          <p className="rl-conv-list__empty">加载中…</p>
        ) : conversations.length === 0 ? (
          <p className="rl-conv-list__empty">{emptyMessage}</p>
        ) : (
          conversations.map((conv) => (
            <div
              key={conv.id}
              className={[
                'rl-conv-item',
                selectedId === conv.id && 'rl-conv-item--active',
              ]
                .filter(Boolean)
                .join(' ')}
            >
              <button
                type="button"
                className="rl-conv-item__main"
                onClick={() => onSelect(conv.id)}
              >
                <span className="rl-conv-item__title">{conv.title || '新对话'}</span>
                {conv.updatedAt && <span className="rl-conv-item__time">{formatTime(conv.updatedAt)}</span>}
              </button>
              {onDelete && (
                <button
                  type="button"
                  className="rl-conv-item__delete"
                  aria-label="删除对话"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(conv.id);
                  }}
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
