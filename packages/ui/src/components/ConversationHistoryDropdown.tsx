import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Clock } from 'lucide-react';
import { ConversationList, type ConversationItem } from './ConversationList';
import { SearchInput } from './SearchInput';

type ConversationHistoryDropdownProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  conversations: ConversationItem[];
  selectedId?: string | null;
  onSelect: (id: string) => void;
  onNewChat?: () => void;
  onDelete?: (id: string) => void;
  loading?: boolean;
  searchValue: string;
  onSearchChange: (value: string) => void;
};

type PanelPosition = { top: number; left: number };

export function ConversationHistoryDropdown({
  open,
  onOpenChange,
  conversations,
  selectedId,
  onSelect,
  onNewChat,
  onDelete,
  loading,
  searchValue,
  onSearchChange,
}: ConversationHistoryDropdownProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [panelPosition, setPanelPosition] = useState<PanelPosition | null>(null);

  const updatePanelPosition = useCallback(() => {
    const button = buttonRef.current;
    if (!button) return;
    const rect = button.getBoundingClientRect();
    setPanelPosition({ top: rect.bottom + 4, left: rect.left });
  }, []);

  useLayoutEffect(() => {
    if (!open) {
      setPanelPosition(null);
      return;
    }
    updatePanelPosition();
    window.addEventListener('scroll', updatePanelPosition, true);
    window.addEventListener('resize', updatePanelPosition);
    return () => {
      window.removeEventListener('scroll', updatePanelPosition, true);
      window.removeEventListener('resize', updatePanelPosition);
    };
  }, [open, updatePanelPosition]);

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onOpenChange(false);
    }
    function onPointerDown(e: MouseEvent) {
      const target = e.target as Node;
      if (rootRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      onOpenChange(false);
    }
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousedown', onPointerDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('mousedown', onPointerDown);
    };
  }, [open, onOpenChange]);

  function handleSelect(id: string) {
    onSelect(id);
    onOpenChange(false);
  }

  function handleNewChat() {
    onNewChat?.();
    onOpenChange(false);
  }

  const panel =
    open && panelPosition
      ? createPortal(
          <div
            ref={panelRef}
            className="rl-history-panel rl-history-panel--portal rl-select-menu--enter"
            style={{ top: panelPosition.top, left: panelPosition.left }}
            role="dialog"
            aria-label="历史对话"
          >
            <SearchInput
              placeholder="搜索会话…"
              value={searchValue}
              onChange={(e) => onSearchChange(e.target.value)}
            />
            <ConversationList
              conversations={conversations}
              selectedId={selectedId}
              onSelect={handleSelect}
              onNewChat={onNewChat ? handleNewChat : undefined}
              onDelete={onDelete}
              loading={loading}
            />
          </div>,
          document.body,
        )
      : null;

  return (
    <div className="rl-history-dropdown" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className={['rl-history-btn', open && 'rl-history-btn--open'].filter(Boolean).join(' ')}
        aria-expanded={open}
        aria-haspopup="dialog"
        data-testid="history-toggle"
        onClick={() => onOpenChange(!open)}
      >
        <Clock size={18} className="rl-history-btn__icon" aria-hidden="true" />
        <span>历史对话</span>
      </button>
      {panel}
    </div>
  );
}
