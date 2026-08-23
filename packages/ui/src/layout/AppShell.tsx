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
  Moon,
  PanelLeft,
  PanelLeftClose,
  Sun,
} from 'lucide-react';
import { NavItem, NavSection } from '../components/NavItem';

type AppShellUser = {
  displayName: string;
  role: string;
  email?: string;
};

type AppShellProps = {
  children: ReactNode;
  user: AppShellUser;
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  theme?: 'light' | 'dark';
  onThemeToggle?: () => void;
};

function avatarLetter(name: string) {
  return name.trim().charAt(0).toUpperCase() || '?';
}

export function AppShell({
  children,
  user,
  collapsed: collapsedProp,
  onCollapsedChange,
  theme = 'light',
  onThemeToggle,
}: AppShellProps) {
  const [collapsedInternal, setCollapsedInternal] = useState(false);
  const collapsed = collapsedProp ?? collapsedInternal;
  const setCollapsed = onCollapsedChange ?? setCollapsedInternal;
  const isAdmin = user.role === 'ADMIN';

  return (
    <div className="rl-shell">
      <aside className={['rl-sidebar', collapsed && 'rl-sidebar--collapsed'].filter(Boolean).join(' ')}>
        <div className="rl-sidebar-header">
          {!collapsed && <div className="rl-logo">RagLaw</div>}
          <button
            type="button"
            className="rl-sidebar-toggle"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? '展开侧栏' : '折叠侧栏'}
          >
            {collapsed ? <PanelLeft size={18} /> : <PanelLeftClose size={18} />}
          </button>
        </div>

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

        <div className="rl-sidebar-footer">
          {onThemeToggle && (
            <div className="rl-sidebar-footer__tools">
              <button
                type="button"
                className="rl-btn rl-theme-toggle"
                onClick={onThemeToggle}
                aria-label={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
                title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}
              >
                {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
                {!collapsed && <span>{theme === 'dark' ? '浅色' : '深色'}</span>}
              </button>
            </div>
          )}
          <div className="rl-sidebar-footer__profile">
            <div className="rl-avatar" aria-hidden="true">{avatarLetter(user.displayName)}</div>
            {!collapsed && (
              <div className="rl-sidebar-footer__info">
                <span className="rl-sidebar-footer__name">{user.displayName}</span>
                {user.email && <span className="rl-sidebar-footer__email">{user.email}</span>}
              </div>
            )}
          </div>
        </div>
      </aside>
      <main className="rl-main">
        <div className="rl-main-content">{children}</div>
      </main>
    </div>
  );
}
