import { useState, type ReactNode } from 'react';
import { AppShell } from '@raglaw/ui';
import { useAuth } from '../lib/auth';
import { useTheme } from '../lib/theme';

export function AuthenticatedLayout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { theme, toggleTheme } = useTheme();
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
      theme={theme}
      onThemeToggle={toggleTheme}
    >
      {children}
    </AppShell>
  );
}
