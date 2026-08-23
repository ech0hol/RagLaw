import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Input } from '@raglaw/ui';
import { useAuth } from '../lib/auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@raglaw.local');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    const err = await login(email, password);
    setSubmitting(false);
    if (err) {
      setError(err);
      return;
    }
    navigate('/');
  }

  return (
    <div className="rl-login-page">
      <div>
        <div className="rl-login-brand">
          <div className="rl-login-brand__logo">RagLaw</div>
          <p className="rl-login-brand__tagline">智能法律咨询平台</p>
        </div>
        <Card padding="lg" className="rl-login-card">
          <form onSubmit={onSubmit}>
            <h1>登录</h1>
            <div className="rl-form-stack">
              <Input
                label="邮箱"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                required
              />
              <Input
                label="密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                required
              />
              {error && <p className="rl-error">{error}</p>}
              <Button type="submit" disabled={submitting}>
                {submitting ? '登录中…' : '登录'}
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </div>
  );
}
