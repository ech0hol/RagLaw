import { FormEvent, useEffect, useState } from 'react';
import { Badge, Button, Card, MainHeader, Spinner } from '@raglaw/ui';
import { Plus } from 'lucide-react';
import { AgentEditDialog, type L2CategoryOption } from '../../components/AgentEditDialog';
import { api } from '../../lib/api';
import { confirmIrreversibleDelete } from '../../lib/confirmDelete';

type Agent = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  model: string;
  type: string;
  skills: string[];
  knowledgeScopes: string[];
  a2aPeers: string[];
  mcpServers: string[];
  systemPrompt: string;
  tools: string[];
};

type CatalogTool = { id: string; label: string; description: string };
type CatalogSkill = { id: string; label: string; description: string };
type CatalogMcpServer = { id: string; label: string; tools: string[]; requiresEnv: string };
type ToolCatalog = {
  builtinTools: CatalogTool[];
  mcpServers: CatalogMcpServer[];
  builtinSkills: CatalogSkill[];
  globalMcpEnabled: boolean;
};

type CategoryNode = {
  id: string;
  code: string;
  name: string;
  docType: string;
  level: number;
  children?: CategoryNode[];
};

const BUILTIN_AGENT_CODES = new Set(['GENERAL', 'STATUTE', 'CASE', 'CONTRACT']);

const AGENT_TYPE_LABELS: Record<string, string> = {
  GENERAL: '通用',
  STATUTE: '法规',
  CASE: '案例',
  CONTRACT: '合同',
};

const DEFAULT_MODEL = 'dashscope:qwen-plus';
const DEFAULT_SYSTEM_PROMPT = '你是法律助手，请基于工具检索结果作答。';

function collectL2Categories(nodes: CategoryNode[]): L2CategoryOption[] {
  const result: L2CategoryOption[] = [];
  for (const l1 of nodes) {
    for (const l2 of l1.children ?? []) {
      if (l2.level === 2) {
        result.push({ code: l2.code, name: l2.name, docType: l2.docType });
      }
    }
  }
  return result;
}

function buildIdToCodeMap(nodes: CategoryNode[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const l1 of nodes) {
    map.set(l1.id, l1.code);
    for (const l2 of l1.children ?? []) {
      map.set(l2.id, l2.code);
      for (const l3 of l2.children ?? []) {
        map.set(l3.id, l3.code);
      }
    }
  }
  return map;
}

function scopesToCodes(scopes: string[], idToCode: Map<string, string>, l2Codes: Set<string>): string[] {
  return scopes
    .map((s) => idToCode.get(s) ?? s)
    .filter((s) => l2Codes.has(s));
}

const SKILL_LABELS: Record<string, string> = {
  'risk-dimension-review': '风险审查',
};

function toolBadges(agent: Agent) {
  const badges: string[] = [];
  if (agent.tools?.includes('rag_search')) badges.push('知识库');
  if (agent.tools?.includes('tavily-search')) badges.push('联网');
  for (const skill of agent.skills ?? []) {
    badges.push(SKILL_LABELS[skill] ?? skill);
  }
  return badges;
}

function createDraftAgent(): Agent {
  return {
    id: '',
    code: '',
    name: '',
    enabled: true,
    model: DEFAULT_MODEL,
    type: 'GENERAL',
    knowledgeScopes: [],
    a2aPeers: [],
    mcpServers: [],
    systemPrompt: DEFAULT_SYSTEM_PROMPT,
    skills: [],
    tools: [],
  };
}

export function AgentsAdminPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [catalog, setCatalog] = useState<ToolCatalog | null>(null);
  const [l2Categories, setL2Categories] = useState<L2CategoryOption[]>([]);
  const [idToCode, setIdToCode] = useState<Map<string, string>>(new Map());
  const [dialogMode, setDialogMode] = useState<'create' | 'edit' | null>(null);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [peers, setPeers] = useState<string[]>([]);
  const [scopes, setScopes] = useState<string[]>([]);
  const [tools, setTools] = useState<string[]>([]);
  const [skills, setSkills] = useState<string[]>([]);
  const [mcpServers, setMcpServers] = useState<string[]>([]);
  const [showDisabled, setShowDisabled] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const visibleAgents = showDisabled ? agents : agents.filter((a) => a.enabled);
  const l2CodeSet = new Set(l2Categories.map((c) => c.code));

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
      const [agentsRes, catRes, catalogRes] = await Promise.all([
        api<Agent[]>('/api/v1/admin/agents'),
        api<CategoryNode[]>('/api/v1/categories/tree'),
        api<ToolCatalog>('/api/v1/admin/agents/catalog'),
      ]);
      if (agentsRes.success) setAgents(agentsRes.data);
      else setError(agentsRes.error?.message ?? '加载 Agent 列表失败');
      if (catRes.success) {
        setL2Categories(collectL2Categories(catRes.data));
        setIdToCode(buildIdToCodeMap(catRes.data));
      }
      if (catalogRes.success) setCatalog(catalogRes.data);
      setLoading(false);
    })();
  }, []);

  function openEdit(agent: Agent) {
    setDialogMode('edit');
    setEditing({ ...agent });
    setPeers(agent.a2aPeers ?? []);
    setScopes(scopesToCodes(agent.knowledgeScopes ?? [], idToCode, l2CodeSet));
    setTools(agent.tools ?? []);
    setSkills(agent.skills ?? []);
    setMcpServers(agent.mcpServers ?? []);
    setMessage(null);
  }

  function openCreate() {
    setDialogMode('create');
    setEditing(createDraftAgent());
    setPeers([]);
    setScopes([]);
    setTools([]);
    setSkills([]);
    setMcpServers([]);
    setMessage(null);
  }

  function closeDialog() {
    setDialogMode(null);
    setEditing(null);
  }

  async function saveEdit(e: FormEvent) {
    e.preventDefault();
    if (!editing || dialogMode !== 'edit') return;
    setSaving(true);
    const res = await api<Agent>(`/api/v1/admin/agents/${editing.code}`, {
      method: 'PUT',
      body: JSON.stringify({
        enabled: editing.enabled,
        type: editing.type,
        model: editing.model,
        knowledgeScopes: scopes,
        a2aPeers: peers,
        systemPrompt: editing.systemPrompt,
        skills,
        tools,
        mcpServers,
      }),
    });
    if (res.success) {
      await api('/api/v1/admin/agents/reload', { method: 'POST' });
      setMessage('已保存并热加载');
      closeDialog();
      void loadAgents();
    } else {
      setMessage(res.error?.message ?? '保存失败');
    }
    setSaving(false);
  }

  async function saveCreate(e: FormEvent) {
    e.preventDefault();
    if (!editing || dialogMode !== 'create') return;
    if (!editing.code.trim()) {
      setMessage('请填写 Agent 编码');
      return;
    }
    if (!editing.name.trim()) {
      setMessage('请填写 Agent 名称');
      return;
    }
    setSaving(true);
    const res = await api<Agent>('/api/v1/admin/agents', {
      method: 'POST',
      body: JSON.stringify({
        code: editing.code.trim().toUpperCase(),
        name: editing.name.trim(),
        model: editing.model,
        systemPrompt: editing.systemPrompt,
        skills,
        tools,
        mcpServers,
        knowledgeScopes: scopes,
        enabled: true,
      }),
    });
    if (res.success) {
      setMessage('已创建并热加载');
      closeDialog();
      void loadAgents();
    } else {
      setMessage(res.error?.message ?? '创建失败');
    }
    setSaving(false);
  }

  async function removeAgent(agent: Agent) {
    if (!confirmIrreversibleDelete(`${agent.name}（${agent.code}）`)) return;
    const res = await api<void>(`/api/v1/admin/agents/${agent.code}`, { method: 'DELETE' });
    if (res.success) {
      setMessage(`已删除 ${agent.code}`);
      void loadAgents();
    } else {
      setMessage(res.error?.message ?? '删除失败');
    }
  }

  return (
    <div>
      <MainHeader title="Agent 配置" />
      {catalog && !catalog.globalMcpEnabled && (
        <p className="rl-form-hint" style={{ marginBottom: '1rem' }}>
          联网搜索需在 <code>.env</code> 设置 <code>RAGLAW_MCP_ENABLED=true</code> 与 <code>TAVILY_API_KEY</code> 并重启后端。
        </p>
      )}
      <div className="rl-admin-toolbar" style={{ marginBottom: '1.25rem' }}>
        <label className="rl-checkbox-item" style={{ display: 'inline-flex' }}>
          <input
            type="checkbox"
            checked={showDisabled}
            onChange={(e) => setShowDisabled(e.target.checked)}
          />
          显示已禁用 Agent
        </label>
        <Button type="button" onClick={openCreate}>
          <Plus size={16} />
          新建 Agent 助手
        </Button>
      </div>
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
                  <th>工具</th>
                  <th>状态</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visibleAgents.map((a) => (
                  <tr key={a.code}>
                    <td><code>{a.code}</code></td>
                    <td>{a.name}</td>
                    <td>{AGENT_TYPE_LABELS[a.type] ?? a.type}</td>
                    <td>{a.model}</td>
                    <td>
                      {toolBadges(a).map((b) => (
                        <Badge key={b} variant="muted">{b}</Badge>
                      ))}
                      {toolBadges(a).length === 0 && <span className="rl-muted">—</span>}
                    </td>
                    <td>
                      <Badge variant={a.enabled ? 'success' : 'muted'}>
                        {a.enabled ? '启用' : '禁用'}
                      </Badge>
                    </td>
                    <td>
                      <div className="rl-table-actions">
                        <Button variant="ghost" onClick={() => openEdit(a)}>编辑</Button>
                        {!BUILTIN_AGENT_CODES.has(a.code) && (
                          <Button variant="ghost" onClick={() => void removeAgent(a)}>删除</Button>
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

      <AgentEditDialog
        open={dialogMode !== null}
        mode={dialogMode ?? 'edit'}
        agent={editing}
        catalogTools={catalog?.builtinTools ?? []}
        catalogSkills={catalog?.builtinSkills ?? []}
        catalogMcpServers={catalog?.mcpServers ?? []}
        globalMcpEnabled={catalog?.globalMcpEnabled ?? false}
        l2Categories={l2Categories}
        scopes={scopes}
        skills={skills}
        tools={tools}
        mcpServers={mcpServers}
        saving={saving}
        onClose={closeDialog}
        onAgentChange={(patch) => setEditing((prev) => (prev ? { ...prev, ...patch } : prev))}
        onScopesChange={setScopes}
        onSkillsChange={setSkills}
        onToolsChange={setTools}
        onMcpServersChange={setMcpServers}
        onSubmit={(e) => void (dialogMode === 'create' ? saveCreate(e) : saveEdit(e))}
      />
    </div>
  );
}
