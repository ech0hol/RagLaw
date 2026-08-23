import { useEffect, useState } from 'react';
import { Badge, MainHeader } from '@raglaw/ui';
import { api, type ApiResponse } from '../../lib/api';

type Agent = {
  code: string;
  name: string;
  enabled: boolean;
  model: string;
};

export function AgentsAdminPage() {
  const [agents, setAgents] = useState<Agent[]>([]);

  useEffect(() => {
    void api<Agent[]>('/api/v1/admin/agents').then((res: ApiResponse<Agent[]>) => {
      if (res.success) {
        setAgents(res.data);
      }
    });
  }, []);

  return (
    <div>
      <MainHeader title="Agent 配置" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        管理法律咨询 Agent 的模型与启用状态
      </p>
      <div className="rl-data-table-wrap">
        <table className="rl-data-table">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>模型</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {agents.map((a) => (
              <tr key={a.code}>
                <td>{a.code}</td>
                <td>{a.name}</td>
                <td>{a.model}</td>
                <td>
                  <Badge variant={a.enabled ? 'success' : 'muted'}>
                    {a.enabled ? '启用' : '禁用'}
                  </Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
