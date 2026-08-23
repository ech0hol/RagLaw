import { useState, type ReactNode } from 'react';
import { AppShell } from '@raglaw/ui';
import { useAuth } from '../lib/auth';
import { ShellConfigProvider, useShellConfig } from './ShellConfigContext';

function ShellWithConfig({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { config } = useShellConfig();
  const [collapsed, setCollapsed] = useState(false);

  return (
    <AppShell
      user={{
        displayName: user!.displayName,
        role: user!.role,
        email: user!.email,
      }}
      collapsed={collapsed}
      onCollapsedChange={setCollapsed}
      sidebarExtra={config.sidebarExtra}
      searchValue={config.searchValue}
      onSearchChange={config.onSearchChange}
      showSearch={config.showSearch}
    >
      {children}
    </AppShell>
  );
}

export function AuthenticatedLayout({ children }: { children: ReactNode }) {
  return (
    <ShellConfigProvider>
      <ShellWithConfig>{children}</ShellWithConfig>
    </ShellConfigProvider>
  );
}
