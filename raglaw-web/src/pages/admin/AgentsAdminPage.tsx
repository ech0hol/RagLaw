import { FormEvent, useEffect, useState } from 'react';
import { Badge, Button, Card, MainHeader } from '@raglaw/ui';
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

function flattenCategories(nodes: CategoryNode[], depth = 0): { id: string; label: string }[] {
  const result: { id: string; label: string }[] = [];
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'—'.repeat(depth)} ${node.name} (${node.code})` });
    if (node.children?.length) result.push(...flattenCategories(node.children, depth + 1));
  }
  return result;
}

export function AgentsAdminPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [categories, setCategories] = useState<{ id: string; label: string }[]>([]);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [peers, setPeers] = useState<string[]>([]);
  const [scopes, setScopes] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function loadAgents() {
    const res = await api<Agent[]>('/api/v1/admin/agents');
    if (res.success) setAgents(res.data);
  }

  useEffect(() => {
    void loadAgents();
    void api<CategoryNode[]>('/api/v1/admin/categories').then((res) => {
      if (res.success) setCategories(flattenCategories(res.data));
    });
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
      {message && <p className="rl-form-success">{message}</p>}
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
                <td>{a.type}</td>
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

      {editing && (
        <Card className="rl-admin-modal">
          <h2>编辑 {editing.code}</h2>
          <form className="rl-admin-form" onSubmit={saveEdit}>
            <fieldset>
              <legend>knowledgeScopes（类目 ID）</legend>
              <div className="rl-checkbox-grid">
                {categories.map((c) => (
                  <label key={c.id}>
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
                  <label key={a.code}>
                    <input
                      type="checkbox"
                      checked={peers.includes(a.code)}
                      onChange={() => setPeers(toggleListItem(peers, a.code))}
                    />
                    {a.code} — {a.name}
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
