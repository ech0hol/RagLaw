import { useEffect, useState } from 'react';
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
      <h1>Agent 配置</h1>
      <table className="data-table">
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
              <td>{a.enabled ? '启用' : '禁用'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
