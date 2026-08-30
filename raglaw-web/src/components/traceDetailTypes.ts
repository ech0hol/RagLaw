export type TraceSummary = {
  id: string;
  conversationId: string;
  agentCode: string;
  queryText: string;
  latencyMs: number | null;
  langfuseTraceId?: string | null;
  langfuseUrl?: string | null;
  createdAt: string;
};

export type TraceStage = {
  id: string;
  stage: string;
  detailJson: string | null;
  durationMs: number | null;
};

export type TraceChunk = {
  id: string;
  chunkId: string;
  score: number;
  path: string;
  excerpt: string;
};

export type TraceLlmUsage = {
  model: string;
  promptTokens: number | null;
  completionTokens: number | null;
};

export type TraceA2aCall = {
  fromAgent: string;
  toAgent: string;
  inputSummary: string | null;
  outputSummary: string | null;
  latencyMs: number | null;
};

export type TraceShadowLog = {
  id: string;
  shadowType: string;
  userValue: string | null;
  systemValue: string | null;
  hit: boolean | null;
  rank: number | null;
  confidenceJson: string | null;
  createdAt: string;
};

export type TraceDetail = {
  trace: TraceSummary;
  stages: TraceStage[];
  chunks: TraceChunk[];
  llmUsage: TraceLlmUsage[];
  a2aCalls: TraceA2aCall[];
  shadowLogs: TraceShadowLog[];
};

export const STAGE_LABELS: Record<string, string> = {
  contract_review: '合同审查',
  contract_rag_context: '合同 RAG',
  contract_llm_batch: '合同 LLM 批次',
  contract_review_complete: '审查完成',
  knowledge_funnel: '知识漏斗',
  dual_channel_retrieval: '双通道检索',
  rag_search: 'RAG 检索',
  shadow_route: '影子路由',
  mcp_tool_call: 'MCP 联网',
  a2a_delegate: 'A2A 委派',
  llm: 'LLM 生成',
};

const STRUCTURED_STAGE_KEYS = new Set([
  'hitCount',
  'scopes',
  'documentId',
  'title',
  'tool',
  'toolCallId',
  'riskCount',
  'originalQuery',
  'contextDocumentId',
  'react',
  'degradation',
  'degradationLevel',
  'lowConfidence',
  'scopeRouteReason',
  'topicConfidence',
  'documentConfidence',
]);

export function formatJson(detailJson: string | null) {
  if (!detailJson) return '';
  try {
    return JSON.stringify(JSON.parse(detailJson), null, 2);
  } catch {
    return detailJson;
  }
}

export function parseStageDetail(detailJson: string | null): {
  structured: Array<{ key: string; value: string }>;
  raw: Record<string, unknown> | null;
} {
  if (!detailJson) return { structured: [], raw: null };
  try {
    const parsed = JSON.parse(detailJson) as Record<string, unknown>;
    const structured: Array<{ key: string; value: string }> = [];
    const rest: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (STRUCTURED_STAGE_KEYS.has(key)) {
        structured.push({ key, value: formatDetailValue(value) });
      } else {
        rest[key] = value;
      }
    }
    return {
      structured,
      raw: Object.keys(rest).length > 0 ? rest : null,
    };
  } catch {
    return { structured: [], raw: null };
  }
}

function formatDetailValue(value: unknown): string {
  if (value == null) return '—';
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map(String).join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

export function normalizeTraceDetail(data: TraceDetail): TraceDetail {
  return {
    ...data,
    stages: data.stages ?? [],
    chunks: data.chunks ?? [],
    llmUsage: data.llmUsage ?? [],
    a2aCalls: data.a2aCalls ?? [],
    shadowLogs: data.shadowLogs ?? [],
  };
}

export type TimelineStage = TraceStage & {
  offsetMs: number;
};

export function buildTimeline(stages: TraceStage[]): TimelineStage[] {
  let offset = 0;
  return stages.map((stage) => {
    const item = { ...stage, offsetMs: offset };
    offset += stage.durationMs ?? 0;
    return item;
  });
}
