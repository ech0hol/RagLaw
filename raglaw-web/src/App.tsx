import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@raglaw/ui';
import { useAuth } from './lib/auth';
import { AgentsAdminPage } from './pages/admin/AgentsAdminPage';
import { ChatPage } from './pages/ChatPage';
import { LoginPage } from './pages/LoginPage';

function Protected({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <p>加载中…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function AdminOnly({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  const { user } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route
        path="/*"
        element={
          <Protected>
            <AppShell user={user!}>
              <Routes>
                <Route path="/" element={<ChatPage />} />
                <Route path="/contracts" element={<p>合同审查（Phase 4）</p>} />
                <Route path="/knowledge/statutes" element={<p>法规/案例查询（Phase 5）</p>} />
                <Route
                  path="/admin/agents"
                  element={
                    <AdminOnly>
                      <AgentsAdminPage />
                    </AdminOnly>
                  }
                />
                <Route path="/admin/*" element={<p>更多管理功能开发中</p>} />
              </Routes>
            </AppShell>
          </Protected>
        }
      />
    </Routes>
  );
}
