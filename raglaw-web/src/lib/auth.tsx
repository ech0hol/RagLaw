import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { clearToken, fetchMe, login as apiLogin, logout as apiLogout, refreshAccessToken, type User } from './api';

type AuthState = {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<string | null>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function bootstrap() {
      await refreshAccessToken();
      const res = await fetchMe();
      if (res.success) {
        setUser(res.data);
      } else {
        clearToken();
        setUser(null);
      }
      setLoading(false);
    }
    void bootstrap();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiLogin(email, password);
    if (!res.success) {
      return res.error?.message ?? '登录失败';
    }
    setUser(res.data.user);
    return null;
  }, []);

  const logout = useCallback(async () => {
    await apiLogout();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
