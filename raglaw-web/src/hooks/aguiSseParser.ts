import type { ChatReference } from '@raglaw/ui';

export type AguiSseParseState = {
  assistant: string;
  references: ChatReference[];
  streamStatus: string | null;
  webSearchAttempted: boolean;
  messageId?: string;
};

export function createAguiSseParseState(): AguiSseParseState {
  return {
    assistant: '',
    references: [],
    streamStatus: null,
    webSearchAttempted: false,
  };
}

function isToolStreamStatus(status: string | null | undefined): boolean {
  if (!status) return false;
  return status.includes('正在检索知识库') || status.includes('正在联网检索');
}

export function applyAguiSseEvent(
  state: AguiSseParseState,
  event: string,
  data: string,
): AguiSseParseState {
  const next = { ...state, references: [...state.references] };

  if (event === 'text' && data) {
    const parsed = JSON.parse(data) as { delta?: string };
    next.assistant += parsed.delta ?? '';
    next.streamStatus = null;
    return next;
  }

  if (event === 'text_reset') {
    next.assistant = '';
    return next;
  }

  if (event === 'status' && data) {
    const parsed = JSON.parse(data) as { message?: string };
    next.streamStatus = parsed.message ?? null;
    if (next.streamStatus?.includes('联网')) {
      next.webSearchAttempted = true;
    }
    return next;
  }

  if (event === 'reference' && data) {
    const parsed = JSON.parse(data) as ChatReference;
    const normalized = {
      ...parsed,
      source: parsed.source ?? 'knowledge',
      title: parsed.title ?? parsed.path,
    };
    if (!next.references.some((ref) => ref.chunkId === normalized.chunkId)) {
      next.references.push(normalized);
    }
    if (parsed.source === 'web') {
      next.webSearchAttempted = true;
    }
    if (isToolStreamStatus(next.streamStatus)) {
      next.streamStatus = null;
    }
    return next;
  }

  if (event === 'done' && data) {
    const parsed = JSON.parse(data) as { messageId?: string; content?: string };
    if (parsed.content?.trim()) {
      next.assistant = parsed.content;
    }
    if (parsed.messageId) {
      next.messageId = parsed.messageId;
    }
    next.streamStatus = null;
    return next;
  }

  return next;
}
