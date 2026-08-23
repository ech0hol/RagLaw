import { Navigate, Route, Routes } from 'react-router-dom';
import { PageTransition, PlaceholderPage, Spinner } from '@raglaw/ui';
import { useAuth } from './lib/auth';
import { AuthenticatedLayout } from './layout/AuthenticatedLayout';
import { AgentsAdminPage } from './pages/admin/AgentsAdminPage';
import { ApprovalsAdminPage } from './pages/admin/ApprovalsAdminPage';
import { CategoriesAdminPage } from './pages/admin/CategoriesAdminPage';
import { DocumentsAdminPage } from './pages/admin/DocumentsAdminPage';
import { ChatPage } from './pages/ChatPage';
import { ContractsPage } from './pages/ContractsPage';
import { ContractReviewPage } from './pages/ContractReviewPage';
import { KnowledgeDetailPage } from './pages/KnowledgeDetailPage';
import { KnowledgePage } from './pages/KnowledgePage';
import { LoginPage } from './pages/LoginPage';
import { ObservabilityAdminPage } from './pages/admin/ObservabilityAdminPage';

function Protected({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="rl-loading-page">
        <Spinner />
      </div>
    );
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
            <AuthenticatedLayout>
              <PageTransition>
                <Routes>
                  <Route path="/" element={<ChatPage />} />
                  <Route path="/chat/:agentCode" element={<ChatPage />} />
                  <Route path="/contracts" element={<ContractsPage />} />
                  <Route path="/contracts/review" element={<ContractReviewPage />} />
                  <Route path="/knowledge/statutes" element={<KnowledgePage />} />
                  <Route path="/knowledge/documents" element={<KnowledgeDetailPage />} />
                  <Route
                    path="/admin/agents"
                    element={
                      <AdminOnly>
                        <AgentsAdminPage />
                      </AdminOnly>
                    }
                  />
                  <Route
                    path="/admin/categories"
                    element={
                      <AdminOnly>
                        <CategoriesAdminPage />
                      </AdminOnly>
                    }
                  />
                  <Route
                    path="/admin/documents"
                    element={
                      <AdminOnly>
                        <DocumentsAdminPage />
                      </AdminOnly>
                    }
                  />
                  <Route
                    path="/admin/approvals"
                    element={
                      <AdminOnly>
                        <ApprovalsAdminPage />
                      </AdminOnly>
                    }
                  />
                  <Route
                    path="/admin/observability"
                    element={
                      <AdminOnly>
                        <ObservabilityAdminPage />
                      </AdminOnly>
                    }
                  />
                  <Route
                    path="/admin/*"
                    element={
                      <AdminOnly>
                        <PlaceholderPage title="更多管理功能" description="更多管理功能开发中。" />
                      </AdminOnly>
                    }
                  />
                </Routes>
              </PageTransition>
            </AuthenticatedLayout>
          </Protected>
        }
      />
    </Routes>
  );
}
