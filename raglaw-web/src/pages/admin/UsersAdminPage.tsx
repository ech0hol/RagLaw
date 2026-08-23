import { FormEvent, useEffect, useState } from 'react';
import { Badge, Button, Card, MainHeader, Spinner } from '@raglaw/ui';
import { api } from '../../lib/api';

type AdminUser = {
  id: string;
  email: string;
  displayName: string;
  role: string;
  enabled: boolean;
  createdAt: string;
};

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  LAWYER: '律师',
};

export function UsersAdminPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<'LAWYER' | 'ADMIN'>('LAWYER');

  async function loadUsers() {
    const res = await api<AdminUser[]>('/api/v1/admin/users');
    if (res.success) {
      setUsers(res.data);
      setError(null);
    } else {
      setError(res.error?.message ?? '加载用户失败');
    }
    setLoading(false);
  }

  useEffect(() => {
    void loadUsers();
  }, []);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    setError(null);
    const res = await api<AdminUser>('/api/v1/admin/users', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName, role }),
    });
    if (!res.success) {
      setError(res.error?.message ?? '创建失败');
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

  return (
    <div>
      <MainHeader title="用户管理" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        仅管理员可创建律师/管理员账号，无公开注册。
      </p>

      <Card className="rl-admin-form" style={{ marginBottom: '1.5rem' }}>
        <h3 className="rl-section-title">创建账号</h3>
        <form onSubmit={(event) => void handleCreate(event)}>
          <div className="rl-form-row">
            <label className="rl-form-label" htmlFor="user-email">邮箱</label>
            <input
              id="user-email"
              className="rl-input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
          <div className="rl-form-row">
            <label className="rl-form-label" htmlFor="user-display-name">显示名称</label>
            <input
              id="user-display-name"
              className="rl-input"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              required
            />
          </div>
          <div className="rl-form-row">
            <label className="rl-form-label" htmlFor="user-password">初始密码</label>
            <input
              id="user-password"
              className="rl-input"
              type="password"
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>
          <div className="rl-form-row">
            <label className="rl-form-label" htmlFor="user-role">角色</label>
            <select
              id="user-role"
              className="rl-input"
              value={role}
              onChange={(event) => setRole(event.target.value as 'LAWYER' | 'ADMIN')}
            >
              <option value="LAWYER">律师</option>
              <option value="ADMIN">管理员</option>
            </select>
          </div>
          {error && <p className="rl-form-error">{error}</p>}
          {message && <p className="rl-form-hint">{message}</p>}
          <Button type="submit" disabled={saving}>
            {saving ? '创建中…' : '创建用户'}
          </Button>
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
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
