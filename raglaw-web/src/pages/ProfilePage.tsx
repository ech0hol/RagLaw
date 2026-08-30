import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, PageHeader } from '@raglaw/ui';
import { useAuth } from '../lib/auth';

function roleLabel(role: string) {
  if (role === 'ADMIN') return '管理员';
  if (role === 'LAWYER') return '律师';
  return role;
}

export function ProfilePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  if (!user) {
    return null;
  }

  async function handleLogout() {
    setLoggingOut(true);
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div>
      <PageHeader title="个人主页" subtitle="账号信息与登录状态" />
      <div className="rl-page-center">
        <Card className="rl-profile-card" padding="lg">
          <div className="rl-profile-card__rows">
            <div className="rl-profile-card__row">
              <span className="rl-profile-card__label">姓名</span>
              <span className="rl-profile-card__value">{user.displayName}</span>
            </div>
            <div className="rl-profile-card__row">
              <span className="rl-profile-card__label">邮箱</span>
              <span className="rl-profile-card__value">{user.email}</span>
            </div>
            <div className="rl-profile-card__row">
              <span className="rl-profile-card__label">角色</span>
              <span className="rl-profile-card__value">{roleLabel(user.role)}</span>
            </div>
          </div>
          <p className="rl-text-muted rl-profile-card__hint">
            若上传或接口提示未登录，可退出后重新登录以刷新访问凭证。
          </p>
          <div className="rl-profile-card__actions">
            <Button
              type="button"
              className="rl-btn--danger"
              disabled={loggingOut}
              onClick={() => void handleLogout()}
            >
              {loggingOut ? '退出中…' : '退出登录'}
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}
