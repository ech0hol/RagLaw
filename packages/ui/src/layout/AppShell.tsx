import { useState } from 'react';
import type { ReactNode } from 'react';
import {
  Activity,
  Bot,
  BookOpen,
  CheckCircle,
  FileText,
  FolderTree,
  MessageSquare,
  PanelLeft,
  PanelLeftClose,
} from 'lucide-react';
import { NavItem, NavSection } from '../components/NavItem';
import { SearchInput } from '../components/SearchInput';

type AppShellUser = {
  displayName: string;
  role: string;
  email?: string;
};

type AppShellProps = {
  children: ReactNode;
  user: AppShellUser;
  sidebarExtra?: ReactNode;
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  showSearch?: boolean;
};

function avatarLetter(name: string) {
  return name.trim().charAt(0).toUpperCase() || '?';
}

export function AppShell({
  children,
  user,
  sidebarExtra,
  collapsed: collapsedProp,
  onCollapsedChange,
  searchValue,
  onSearchChange,
  showSearch = false,
}: AppShellProps) {
  const [collapsedInternal, setCollapsedInternal] = useState(false);
  const collapsed = collapsedProp ?? collapsedInternal;
  const setCollapsed = onCollapsedChange ?? setCollapsedInternal;
  const isAdmin = user.role === 'ADMIN';

  return (
    <div className="rl-shell">
      <aside className={['rl-sidebar', collapsed && 'rl-sidebar--collapsed'].filter(Boolean).join(' ')}>
        <div className="rl-sidebar-header">
          <div className="rl-logo">RagLaw</div>
          <button
            type="button"
            className="rl-sidebar-toggle"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? '展开侧栏' : '折叠侧栏'}
          >
            {collapsed ? <PanelLeft size={18} /> : <PanelLeftClose size={18} />}
          </button>
        </div>

        {showSearch && onSearchChange && (
          <div className="rl-sidebar-search">
            <SearchInput
              placeholder="搜索会话…"
              value={searchValue ?? ''}
              onChange={(e) => onSearchChange(e.target.value)}
              showShortcutHint={!collapsed}
            />
          </div>
        )}

        <nav className="rl-nav">
          <NavItem to="/" end icon={<MessageSquare size={18} />}>智能对话</NavItem>
          <NavItem to="/contracts" icon={<FileText size={18} />}>合同审查</NavItem>
          <NavItem to="/knowledge/statutes" icon={<BookOpen size={18} />}>法规/案例查询</NavItem>
          {isAdmin && (
            <>
              <NavSection>管理</NavSection>
              <NavItem to="/admin/agents" icon={<Bot size={18} />}>Agent 配置</NavItem>
              <NavItem to="/admin/categories" icon={<FolderTree size={18} />}>类目管理</NavItem>
              <NavItem to="/admin/documents" icon={<FileText size={18} />}>文档管理</NavItem>
              <NavItem to="/admin/approvals" icon={<CheckCircle size={18} />}>审批管理</NavItem>
              <NavItem to="/admin/observability" icon={<Activity size={18} />}>可观测性</NavItem>
            </>
          )}
        </nav>

        {sidebarExtra && <div className="rl-sidebar-extra">{sidebarExtra}</div>}

        <div className="rl-sidebar-footer">
          <div className="rl-avatar" aria-hidden="true">{avatarLetter(user.displayName)}</div>
          <div className="rl-sidebar-footer__info">
            <span className="rl-sidebar-footer__name">{user.displayName}</span>
            {user.email && <span className="rl-sidebar-footer__email">{user.email}</span>}
          </div>
        </div>
      </aside>
      <main className="rl-main">
        <div className="rl-main-content">{children}</div>
      </main>
    </div>
  );
}
