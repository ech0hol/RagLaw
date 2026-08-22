import { useEffect, useState } from 'react';

type Health = {
  status: string;
  service: string;
  modules: string[];
};

export function HomePage() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/v1/health')
      .then((r) => r.json())
      .then((body) => {
        if (body.success) {
          setHealth(body.data);
        } else {
          setError(body.error?.message ?? 'unknown');
        }
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <div className="home">
      <h1>欢迎使用 RagLaw</h1>
      <p className="subtitle">智能法律咨询助手 · Phase 0 脚手架</p>
      {health && (
        <div className="health-card">
          <p>后端状态：{health.status}</p>
          <p>模块：{health.modules.join(', ')}</p>
        </div>
      )}
      {error && <p className="error">后端未连接：{error}</p>}
      <p className="disclaimer">AI 辅助参考，不构成法律意见。</p>
    </div>
  );
}
