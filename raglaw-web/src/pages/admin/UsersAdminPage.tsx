import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, FormDialog, MainHeader, Select, Spinner } from '@raglaw/ui';
import { api, deleteAdminUser, ensureSession, updateAdminUser, type AdminUserRow, type ApiResponse } from '../../lib/api';
import { confirmIrreversibleDelete } from '../../lib/confirmDelete';
import { useAuth } from '../../lib/auth';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  LAWYER: '律师',
};

const ROLE_OPTIONS = [
  { value: 'LAWYER', label: '律师' },
  { value: 'ADMIN', label: '管理员' },
];

const ENABLED_OPTIONS = [
  { value: 'true', label: '启用' },
  { value: 'false', label: '禁用' },
];

export function UsersAdminPage() {
  const { user: currentUser, logout } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<'LAWYER' | 'ADMIN'>('LAWYER');
  const [editing, setEditing] = useState<AdminUserRow | null>(null);
  const [editDisplayName, setEditDisplayName] = useState('');
  const [editEnabled, setEditEnabled] = useState(true);
  const [editRole, setEditRole] = useState<'LAWYER' | 'ADMIN'>('LAWYER');

  const handleUnauthorized = useCallback(async () => {
    await logout();
    navigate('/login', { replace: true });
  }, [logout, navigate]);

  const resolveApiError = useCallback(async (
    res: ApiResponse<unknown>,
    fallback: string,
    setErr: (message: string) => void,
  ): Promise<boolean> => {
    if (res.success) {
      return true;
    }
    if (res.error?.code === 'UNAUTHORIZED') {
      setErr('登录已过期，请重新登录');
      await handleUnauthorized();
      return false;
    }
    setErr(res.error?.message ?? fallback);
    return false;
  }, [handleUnauthorized]);

  async function loadUsers() {
    if (!await ensureSession()) {
      setError('登录已过期，请重新登录');
      setLoading(false);
      await handleUnauthorized();
      return;
    }
    const res = await api<AdminUserRow[]>('/api/v1/admin/users');
    if (await resolveApiError(res, '加载用户失败', setError)) {
      setUsers(res.data);
      setError(null);
    }
    setLoading(false);
  }

  useEffect(() => {
    void loadUsers();
  }, []);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    const form = event.currentTarget;
    if (!form.checkValidity()) {
      event.preventDefault();
      form.reportValidity();
      return;
    }
    if (password.length < 8) {
      event.preventDefault();
      setError('初始密码至少 8 位');
      return;
    }
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    setError(null);
    if (!await ensureSession()) {
      setError('登录已过期，请重新登录');
      setSaving(false);
      await handleUnauthorized();
      return;
    }
    const res = await api<AdminUserRow>('/api/v1/admin/users', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName, role }),
    });
    if (!await resolveApiError(res, '创建失败', setError)) {
      setSaving(false);
      return;
    }
    setMessage(`已创建用户：${res.data.email}`);
    setEmail('');
    setPassword('');
    setDisplayName('');
    setRole('LAWYER');
    await loadUsers();
    setSaving(false);
  }

  function openEdit(user: AdminUserRow) {
    setEditing(user);
    setEditDisplayName(user.displayName);
    setEditEnabled(user.enabled);
    setEditRole(user.role as 'LAWYER' | 'ADMIN');
    setEditError(null);
  }

  async function saveEdit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    if (!editEnabled && editing.id === currentUser?.id) {
      setEditError('不能禁用当前登录账号');
      return;
    }
    setSaving(true);
    setEditError(null);
    if (!await ensureSession()) {
      setEditError('登录已过期，请重新登录');
      setSaving(false);
      await handleUnauthorized();
      return;
    }
    const res = await updateAdminUser(editing.id, {
      displayName: editDisplayName.trim(),
      enabled: editEnabled,
      role: editRole,
    });
    setSaving(false);
    if (!await resolveApiError(res, '保存失败', setEditError)) {
      return;
    }
    setMessage(`已更新用户：${editing.email}`);
    setEditing(null);
    await loadUsers();
  }

  async function removeUser(user: AdminUserRow) {
    if (!confirmIrreversibleDelete(user.email)) return;
    setSaving(true);
    setError(null);
    if (!await ensureSession()) {
      setError('登录已过期，请重新登录');
      setSaving(false);
      await handleUnauthorized();
      return;
    }
    const res = await deleteAdminUser(user.id);
    setSaving(false);
    if (!await resolveApiError(res, '删除失败', setError)) {
      return;
    }
    setMessage(`已删除用户：${user.email}`);
    await loadUsers();
  }

  const editingSelf = editing?.id === currentUser?.id;

  return (
    <div>
      <MainHeader title="用户管理" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        仅管理员可创建律师/管理员账号，无公开注册。
      </p>

      <Card className="rl-admin-form" style={{ marginBottom: '1.5rem' }}>
        <h3 className="rl-section-title">创建账号</h3>
        <form onSubmit={(event) => void handleCreate(event)}>
          <div className="rl-admin-form-grid">
            <div className="rl-admin-form-field">
              <label htmlFor="user-email">邮箱</label>
              <input
                id="user-email"
                className="rl-input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </div>
            <div className="rl-admin-form-field">
              <label htmlFor="user-display-name">显示名称</label>
              <input
                id="user-display-name"
                className="rl-input"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                required
              />
            </div>
            <div className="rl-admin-form-field">
              <label htmlFor="user-password">初始密码</label>
              <input
                id="user-password"
                className="rl-input"
                type="password"
                minLength={8}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
              <p className="rl-form-hint">至少 8 位字符</p>
            </div>
            <div className="rl-admin-form-field">
              <Select
                label="角色"
                value={role}
                onChange={(value) => setRole(value as 'LAWYER' | 'ADMIN')}
                options={ROLE_OPTIONS}
              />
            </div>
          </div>
          {(error || message) && !editing && (
            <div className="rl-admin-form-grid__full">
              {error && <p className="rl-form-error">{error}</p>}
              {message && <p className="rl-form-hint">{message}</p>}
            </div>
          )}
          <div className="rl-admin-form-actions">
            <Button type="submit" disabled={saving}>
              {saving ? '创建中…' : '创建用户'}
            </Button>
          </div>
        </form>
      </Card>

      {loading ? (
        <Spinner />
      ) : (
        <Card>
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th>邮箱</th>
                  <th>名称</th>
                  <th>角色</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>{user.email}</td>
                    <td>{user.displayName}</td>
                    <td>{ROLE_LABELS[user.role] ?? user.role}</td>
                    <td>
                      <Badge variant={user.enabled ? 'default' : 'muted'}>
                        {user.enabled ? '启用' : '禁用'}
                      </Badge>
                    </td>
                    <td>{new Date(user.createdAt).toLocaleString()}</td>
                    <td>
                      <div className="rl-table-actions">
                        <Button variant="ghost" onClick={() => openEdit(user)}>编辑</Button>
                        {user.id !== currentUser?.id && (
                          <Button variant="ghost" onClick={() => void removeUser(user)}>删除</Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <FormDialog
        open={Boolean(editing)}
        title={editing ? `编辑用户：${editing.email}` : '编辑用户'}
        onClose={() => setEditing(null)}
        footer={
          <div className="rl-dialog__actions">
            <Button type="button" variant="ghost" onClick={() => setEditing(null)}>取消</Button>
            <Button
              type="submit"
              disabled={saving || !editDisplayName.trim()}
              onClick={(e) => {
                e.preventDefault();
                const form = document.getElementById('user-edit-form') as HTMLFormElement | null;
                form?.requestSubmit();
              }}
            >
              {saving ? '保存中…' : '保存'}
            </Button>
          </div>
        }
      >
        {editing && (
          <form id="user-edit-form" className="rl-admin-form" onSubmit={(e) => void saveEdit(e)}>
            <p className="rl-text-muted rl-form-dialog__meta">邮箱 {editing.email}</p>
            <label>
              显示名称
              <input
                className="rl-input"
                value={editDisplayName}
                onChange={(e) => setEditDisplayName(e.target.value)}
                required
              />
            </label>
            <Select
              label="角色"
              value={editRole}
              onChange={(value) => setEditRole(value as 'LAWYER' | 'ADMIN')}
              options={ROLE_OPTIONS}
              disabled={editingSelf}
            />
            <Select
              label="账号状态"
              value={editEnabled ? 'true' : 'false'}
              onChange={(value) => setEditEnabled(value === 'true')}
              options={ENABLED_OPTIONS}
              disabled={editingSelf}
            />
            {editError && <p className="rl-form-error">{editError}</p>}
          </form>
        )}
      </FormDialog>
    </div>
  );
}
