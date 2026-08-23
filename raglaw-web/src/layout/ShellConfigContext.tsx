import { createContext, useContext, useState, type ReactNode } from 'react';

type ShellConfig = {
  sidebarExtra?: ReactNode;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  showSearch?: boolean;
};

const ShellConfigContext = createContext<{
  config: ShellConfig;
  setConfig: (config: ShellConfig) => void;
} | null>(null);

export function ShellConfigProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<ShellConfig>({});
  return (
    <ShellConfigContext.Provider value={{ config, setConfig }}>
      {children}
    </ShellConfigContext.Provider>
  );
}

export function useShellConfig() {
  const ctx = useContext(ShellConfigContext);
  if (!ctx) {
    throw new Error('useShellConfig must be used within ShellConfigProvider');
  }
  return ctx;
}
