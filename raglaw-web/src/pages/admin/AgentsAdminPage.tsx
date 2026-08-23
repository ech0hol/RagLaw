import { FormEvent, useEffect, useState } from 'react';
import { Badge, Button, Card, MainHeader, Spinner } from '@raglaw/ui';
import { api } from '../../lib/api';

type Agent = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  model: string;
  type: string;
  knowledgeScopes: string[];
  a2aPeers: string[];
  systemPrompt: string;
  tools: string[];
};

type CategoryNode = { id: string; code: string; name: string; children: CategoryNode[] };

type FlatCategory = { id: string; label: string; depth: number };

const AGENT_TYPE_LABELS: Record<string, string> = {
  GENERAL: '通用',
  STATUTE: '法规',
  CASE: '案例',
  CONTRACT: '合同',
};

function flattenCategories(nodes: CategoryNode[], depth = 0): FlatCategory[] {
  const result: FlatCategory[] = [];
  for (const node of nodes) {
    result.push({ id: node.id, label: `${node.name}（${node.code}）`, depth });
    if (node.children?.length) result.push(...flattenCategories(node.children, depth + 1));
  }
  return result;
}

export function AgentsAdminPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [categories, setCategories] = useState<FlatCategory[]>([]);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [peers, setPeers] = useState<string[]>([]);
  const [scopes, setScopes] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function loadAgents() {
    const res = await api<Agent[]>('/api/v1/admin/agents');
    if (res.success) {
      setAgents(res.data);
      setError(null);
    } else {
      setError(res.error?.message ?? '加载 Agent 列表失败');
    }
  }

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      const agentsRes = await api<Agent[]>('/api/v1/admin/agents');
      if (agentsRes.success) {
        setAgents(agentsRes.data);
      } else {
        setError(agentsRes.error?.message ?? '加载 Agent 列表失败');
      }
      const catRes = await api<CategoryNode[]>('/api/v1/admin/categories');
      if (catRes.success) {
        setCategories(flattenCategories(catRes.data));
      } else {
        setError((prev) => prev ?? catRes.error?.message ?? '加载类目失败');
      }
      setLoading(false);
    })();
  }, []);

  function openEdit(agent: Agent) {
    setEditing(agent);
    setPeers(agent.a2aPeers ?? []);
    setScopes(agent.knowledgeScopes ?? []);
    setMessage(null);
  }

  async function saveEdit(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    const res = await api<Agent>(`/api/v1/admin/agents/${editing.code}`, {
      method: 'PUT',
      body: JSON.stringify({
        enabled: editing.enabled,
        model: editing.model,
        knowledgeScopes: scopes,
        a2aPeers: peers,
        systemPrompt: editing.systemPrompt,
        tools: editing.tools,
      }),
    });
    if (res.success) {
      await api('/api/v1/admin/agents/reload', { method: 'POST' });
      setMessage('已保存并热加载');
      setEditing(null);
      void loadAgents();
    } else {
      setMessage(res.error?.message ?? '保存失败');
    }
    setSaving(false);
  }

  function toggleListItem(list: string[], value: string): string[] {
    return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
  }

  return (
    <div>
      <MainHeader title="Agent 配置" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        管理 Agent 模型、knowledgeScopes 与 A2A 白名单；保存后自动 reload。
      </p>
      {error && <p className="rl-form-error">{error}</p>}
      {message && <p className="rl-form-success">{message}</p>}
      {loading ? (
        <Spinner />
      ) : (
        <Card>
          <div className="rl-data-table-wrap">
            <table className="rl-data-table">
              <thead>
                <tr>
                  <th>编码</th>
                  <th>名称</th>
                  <th>类型</th>
                  <th>模型</th>
                  <th>状态</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {agents.map((a) => (
                  <tr key={a.code}>
                    <td><code>{a.code}</code></td>
                    <td>{a.name}</td>
                    <td>{AGENT_TYPE_LABELS[a.type] ?? a.type}</td>
                    <td>{a.model}</td>
                    <td>
                      <Badge variant={a.enabled ? 'success' : 'muted'}>
                        {a.enabled ? '启用' : '禁用'}
                      </Badge>
                    </td>
                    <td>
                      <Button variant="ghost" onClick={() => openEdit(a)}>编辑</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {editing && (
        <Card className="rl-admin-modal">
          <h2>编辑 {editing.code}</h2>
          <form className="rl-admin-form" onSubmit={saveEdit}>
            <fieldset>
              <legend>knowledgeScopes（类目）</legend>
              <div className="rl-checkbox-grid">
                {categories.map((c) => (
                  <label
                    key={c.id}
                    className="rl-checkbox-item"
                    style={{ paddingLeft: `${c.depth * 0.75}rem` }}
                  >
                    <input
                      type="checkbox"
                      checked={scopes.includes(c.id)}
                      onChange={() => setScopes(toggleListItem(scopes, c.id))}
                    />
                    {c.label}
                  </label>
                ))}
              </div>
            </fieldset>
            <fieldset>
              <legend>a2aPeers（专家 Agent 白名单）</legend>
              <div className="rl-checkbox-grid">
                {agents.filter((a) => a.code !== editing.code).map((a) => (
                  <label key={a.code} className="rl-checkbox-item">
                    <input
                      type="checkbox"
                      checked={peers.includes(a.code)}
                      onChange={() => setPeers(toggleListItem(peers, a.code))}
                    />
                    {a.code}（{a.name}）
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="rl-admin-form__actions">
              <Button type="submit" disabled={saving}>{saving ? '保存中…' : '保存并 reload'}</Button>
              <Button type="button" variant="ghost" onClick={() => setEditing(null)}>取消</Button>
            </div>
          </form>
        </Card>
      )}
    </div>
  );
}
