import { FormEvent } from 'react';
import { Button, FormDialog, Select } from '@raglaw/ui';

export type AgentEditAgent = {
  code: string;
  name: string;
  type: string;
  model: string;
  systemPrompt: string;
  enabled: boolean;
};

export type L2CategoryOption = {
  code: string;
  name: string;
  docType: string;
};

type CatalogTool = { id: string; label: string };
type CatalogSkill = { id: string; label: string };
type CatalogMcpServer = { id: string; label: string };

const MODEL_OPTIONS = [
  { value: 'dashscope:qwen-plus', label: '通义千问 Plus（推荐）' },
  { value: 'dashscope:qwen-max', label: '通义千问 Max（更强）' },
  { value: 'dashscope:qwen-turbo', label: '通义千问 Turbo（更快）' },
];

const SEARCH_TYPE_OPTIONS = [
  { value: 'STATUTE', label: '法规' },
  { value: 'CASE', label: '案例' },
];

function resolveModelOptions(current: string) {
  if (MODEL_OPTIONS.some((o) => o.value === current)) return MODEL_OPTIONS;
  return [...MODEL_OPTIONS, { value: current, label: `${current}（当前）` }];
}

const DOC_TYPE_LABELS: Record<string, string> = {
  STATUTE: '法规',
  CASE: '案例',
  CONTRACT: '合同',
};

type AgentEditDialogProps = {
  open: boolean;
  mode: 'create' | 'edit';
  agent: AgentEditAgent | null;
  catalogTools: CatalogTool[];
  catalogSkills: CatalogSkill[];
  catalogMcpServers: CatalogMcpServer[];
  globalMcpEnabled: boolean;
  l2Categories: L2CategoryOption[];
  scopes: string[];
  skills: string[];
  tools: string[];
  mcpServers: string[];
  saving: boolean;
  onClose: () => void;
  onAgentChange: (agent: AgentEditAgent) => void;
  onScopesChange: (scopes: string[]) => void;
  onSkillsChange: (skills: string[]) => void;
  onToolsChange: (tools: string[]) => void;
  onMcpServersChange: (mcpServers: string[]) => void;
  onSubmit: (e: FormEvent) => void;
};

function toggleListItem(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

export function AgentEditDialog({
  open,
  mode,
  agent,
  catalogTools,
  catalogSkills,
  catalogMcpServers,
  globalMcpEnabled,
  l2Categories,
  scopes,
  skills,
  tools,
  mcpServers,
  saving,
  onClose,
  onAgentChange,
  onScopesChange,
  onSkillsChange,
  onToolsChange,
  onMcpServersChange,
  onSubmit,
}: AgentEditDialogProps) {
  if (!open || !agent) return null;

  const currentAgent = agent;
  const isCreate = mode === 'create';
  const showSearchType = !isCreate && (currentAgent.type === 'STATUTE' || currentAgent.type === 'CASE');
  const showRagScopes = tools.includes('rag_search');
  const showTypeScopes = showSearchType && !showRagScopes;
  const domainOptions = l2Categories.filter((c) => c.docType === currentAgent.type);
  const ragScopeOptions = [...l2Categories].sort((a, b) => {
    const typeOrder = (DOC_TYPE_LABELS[a.docType] ?? a.docType).localeCompare(
      DOC_TYPE_LABELS[b.docType] ?? b.docType,
      'zh-CN',
    );
    if (typeOrder !== 0) return typeOrder;
    return a.name.localeCompare(b.name, 'zh-CN');
  });

  function onSearchTypeChange(nextType: 'STATUTE' | 'CASE') {
    onAgentChange({ ...currentAgent, type: nextType });
    onScopesChange(scopes.filter((code) => {
      const cat = l2Categories.find((c) => c.code === code);
      return cat?.docType === nextType;
    }));
  }

  function toggleTool(toolId: string) {
    const next = toggleListItem(tools, toolId);
    onToolsChange(next);
    if (toolId === 'tavily-search' && next.includes('tavily-search')) {
      onMcpServersChange(mcpServers.includes('tavily') ? mcpServers : [...mcpServers, 'tavily']);
    }
  }

  const submitLabel = isCreate
    ? (saving ? '创建中…' : '创建并 reload')
    : (saving ? '保存中…' : '保存并 reload');

  return (
    <FormDialog
      open={open}
      title={isCreate ? '新建 Agent 助手' : `编辑 ${currentAgent.code}`}
      onClose={onClose}
      wide
      footer={
        <div className="rl-dialog__actions">
          <Button type="button" variant="ghost" onClick={onClose}>取消</Button>
          <Button type="submit" disabled={saving} onClick={(e) => {
            e.preventDefault();
            const form = document.getElementById('agent-edit-form') as HTMLFormElement | null;
            form?.requestSubmit();
          }}>
            {submitLabel}
          </Button>
        </div>
      }
    >
      <form id="agent-edit-form" className="rl-admin-form" onSubmit={onSubmit}>
        {isCreate ? (
          <div className="rl-admin-form-grid">
            <div className="rl-admin-form-field">
              <label htmlFor="agent-code">编码</label>
              <input
                id="agent-code"
                className="rl-input"
                value={currentAgent.code}
                placeholder="例如 MY_AGENT"
                onChange={(e) => onAgentChange({ ...currentAgent, code: e.target.value.toUpperCase() })}
              />
              <p className="rl-form-hint">大写字母开头，可含数字与下划线</p>
            </div>
            <div className="rl-admin-form-field">
              <label htmlFor="agent-name">名称</label>
              <input
                id="agent-name"
                className="rl-input"
                value={currentAgent.name}
                placeholder="助手显示名称"
                onChange={(e) => onAgentChange({ ...currentAgent, name: e.target.value })}
              />
            </div>
          </div>
        ) : (
          <p className="rl-text-muted rl-form-dialog__meta">{currentAgent.name}</p>
        )}

        <div className="rl-admin-form-grid">
          <Select
            label="模型"
            value={currentAgent.model}
            onChange={(value) => onAgentChange({ ...currentAgent, model: value })}
            options={resolveModelOptions(currentAgent.model)}
          />
          {showSearchType ? (
            <Select
              label="检索类型"
              value={currentAgent.type}
              onChange={(value) => onSearchTypeChange(value as 'STATUTE' | 'CASE')}
              options={SEARCH_TYPE_OPTIONS}
            />
          ) : (
            <div className="rl-admin-form-field">
              <span className="rl-admin-form-field__label">类型</span>
              <p className="rl-form-hint" style={{ margin: 0 }}>
                {currentAgent.type === 'GENERAL' ? '通用' : currentAgent.type === 'CONTRACT' ? '合同' : currentAgent.type}
              </p>
            </div>
          )}
        </div>

        {showTypeScopes && (
          <div className="rl-admin-form-section rl-admin-form-grid__full">
            <h4 className="rl-admin-form-section__title">
              {currentAgent.type === 'STATUTE' ? '法规' : '案例'}领域（可多选）
            </h4>
            <div className="rl-admin-checkbox-panel">
              {domainOptions.map((c) => (
                <label key={c.code} className="rl-checkbox-item">
                  <input
                    type="checkbox"
                    checked={scopes.includes(c.code)}
                    onChange={() => onScopesChange(toggleListItem(scopes, c.code))}
                  />
                  {c.name}
                </label>
              ))}
            </div>
          </div>
        )}

        {showRagScopes && (
          <div className="rl-admin-form-section rl-admin-form-grid__full">
            <h4 className="rl-admin-form-section__title">知识库领域（可多选）</h4>
            <div className="rl-admin-checkbox-panel">
              {ragScopeOptions.map((c) => (
                <label key={c.code} className="rl-checkbox-item">
                  <input
                    type="checkbox"
                    checked={scopes.includes(c.code)}
                    onChange={() => onScopesChange(toggleListItem(scopes, c.code))}
                  />
                  <span className="rl-agent-scope-label">
                    <span className="rl-agent-scope-label__type">{DOC_TYPE_LABELS[c.docType] ?? c.docType}</span>
                    {c.name}
                  </span>
                </label>
              ))}
            </div>
          </div>
        )}

        <div className="rl-admin-form-field rl-admin-form-grid__full">
          <label htmlFor="agent-system-prompt">System Prompt</label>
          <textarea
            id="agent-system-prompt"
            className="rl-input"
            rows={5}
            value={currentAgent.systemPrompt ?? ''}
            onChange={(e) => onAgentChange({ ...currentAgent, systemPrompt: e.target.value })}
          />
        </div>

        <div className="rl-admin-form-section rl-admin-form-grid__full">
          <h4 className="rl-admin-form-section__title">Skill（可选）</h4>
          <div className="rl-admin-checkbox-panel">
            {catalogSkills.length === 0 ? (
              <p className="rl-text-muted" style={{ margin: 0 }}>暂无可选 Skill</p>
            ) : (
              catalogSkills.map((s) => (
                <label key={s.id} className="rl-checkbox-item">
                  <input
                    type="checkbox"
                    checked={skills.includes(s.id)}
                    onChange={() => onSkillsChange(toggleListItem(skills, s.id))}
                  />
                  {s.label}
                </label>
              ))
            )}
          </div>
        </div>

        <div className="rl-admin-form-section rl-admin-form-grid__full">
          <h4 className="rl-admin-form-section__title">工具（可选）</h4>
          <div className="rl-admin-checkbox-panel">
            {catalogTools.map((t) => (
              <label key={t.id} className="rl-checkbox-item">
                <input
                  type="checkbox"
                  checked={tools.includes(t.id)}
                  onChange={() => toggleTool(t.id)}
                  disabled={t.id === 'tavily-search' && !globalMcpEnabled}
                />
                {t.label}
                {t.id === 'tavily-search' && !globalMcpEnabled && (
                  <span className="rl-text-muted">（全局 MCP 未开启）</span>
                )}
              </label>
            ))}
          </div>
        </div>

        <div className="rl-admin-form-section rl-admin-form-grid__full">
          <h4 className="rl-admin-form-section__title">MCP 连接（可选）</h4>
          <div className="rl-admin-checkbox-panel">
            {catalogMcpServers.map((s) => (
              <label key={s.id} className="rl-checkbox-item">
                <input
                  type="checkbox"
                  checked={mcpServers.includes(s.id)}
                  onChange={() => onMcpServersChange(toggleListItem(mcpServers, s.id))}
                  disabled={!globalMcpEnabled}
                />
                {s.label}
              </label>
            ))}
          </div>
        </div>
      </form>
    </FormDialog>
  );
}
