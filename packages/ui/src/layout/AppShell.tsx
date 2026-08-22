import type { ReactNode } from 'react';

type AppShellUser = {
  displayName: string;
  role: string;
};

type AppShellProps = {
  children: ReactNode;
  user: AppShellUser;
};

export function AppShell({ children, user }: AppShellProps) {
  const isAdmin = user.role === 'ADMIN';

  return (
    <div className="raglaw-shell">
      <aside className="raglaw-sidebar">
        <div className="raglaw-logo">RagLaw</div>
        <nav className="raglaw-nav">
          <a href="/">智能对话</a>
          <a href="/contracts">合同审查</a>
          <a href="/knowledge/statutes">法规/案例查询</a>
          {isAdmin && (
            <>
              <div className="nav-section">管理</div>
              <a href="/admin/agents">Agent 配置</a>
              <a href="/admin/categories">类目管理</a>
              <a href="/admin/observability">可观测性</a>
            </>
          )}
        </nav>
        <div className="sidebar-footer">
          <span>{user.displayName}</span>
        </div>
      </aside>
      <main className="raglaw-main">{children}</main>
    </div>
  );
}
