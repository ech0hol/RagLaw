import type { ChatMessage } from '@raglaw/ui';

function lastAssistantMessage(messages: ChatMessage[]) {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') {
      return messages[i];
    }
  }
  return undefined;
}

function lastAssistantIndex(messages: ChatMessage[]) {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') {
      return i;
    }
  }
  return -1;
}

export function hasCollapsedListFormatting(content: string): boolean {
  return /；\s*-\s/.test(content) || /\*\*[^*\n]+?\*\* -/.test(content);
}

export function shouldKeepStreamedContent(streamed: string, api: string): boolean {
  const streamedTrimmed = streamed.trim();
  const apiTrimmed = api.trim();
  if (!streamedTrimmed) return false;
  if (!apiTrimmed) return true;
  return hasCollapsedListFormatting(apiTrimmed) && !hasCollapsedListFormatting(streamedTrimmed);
}

export function preserveStreamedAssistant(
  prev: ChatMessage[],
  fromApi: ChatMessage[],
): ChatMessage[] {
  const streamedAssistant = lastAssistantMessage(prev);
  const apiAssistantIndex = lastAssistantIndex(fromApi);
  if (!streamedAssistant || apiAssistantIndex < 0) {
    return fromApi;
  }

  const apiAssistant = fromApi[apiAssistantIndex];
  const streamedContent = streamedAssistant.content ?? '';
  const apiContent = apiAssistant.content ?? '';
  const streamedRefs = streamedAssistant.references ?? [];
  const apiRefs = apiAssistant.references ?? [];

  const keepStreamedContent = shouldKeepStreamedContent(streamedContent, apiContent);
  const keepStreamedRefs = streamedRefs.length > 0 && apiRefs.length === 0;

  if (!keepStreamedContent && !keepStreamedRefs) {
    return fromApi;
  }

  const next = [...fromApi];
  next[apiAssistantIndex] = {
    ...apiAssistant,
    ...(keepStreamedContent ? { content: streamedContent } : {}),
    ...(keepStreamedRefs
      ? {
          references: streamedRefs,
          webSearchAttempted:
            streamedAssistant.webSearchAttempted ?? apiAssistant.webSearchAttempted,
        }
      : {}),
  };
  return next;
}

/** @deprecated Use preserveStreamedAssistant */
export function preserveStreamedReferences(
  prev: ChatMessage[],
  fromApi: ChatMessage[],
): ChatMessage[] {
  return preserveStreamedAssistant(prev, fromApi);
}

export function hasOptimisticStreamingTail(messages: ChatMessage[]) {
  if (messages.length < 2) {
    return false;
  }
  const last = messages[messages.length - 1];
  const prev = messages[messages.length - 2];
  return prev.role === 'user' && last.role === 'assistant' && !last.content?.trim();
}

function apiHasMatchingUser(fromApi: ChatMessage[], userContent: string) {
  return fromApi.some((m) => m.role === 'user' && m.content === userContent);
}

export function mergeStreamingMessages(
  prev: ChatMessage[],
  fromApi: ChatMessage[],
  streaming: boolean,
): ChatMessage[] {
  if (!streaming || !hasOptimisticStreamingTail(prev)) {
    return fromApi;
  }

  const optimisticAssistant = prev[prev.length - 1];
  const optimisticUser = prev[prev.length - 2];

  if (!apiHasMatchingUser(fromApi, optimisticUser.content)) {
    return prev;
  }

  const apiLast = fromApi[fromApi.length - 1];
  const apiPrev = fromApi.length >= 2 ? fromApi[fromApi.length - 2] : undefined;

  if (
    apiLast?.role === 'assistant'
    && apiLast.content?.trim()
    && apiPrev?.role === 'user'
    && apiPrev.content === optimisticUser.content
  ) {
    return fromApi;
  }

  if (apiLast?.role === 'user' && apiLast.content === optimisticUser.content) {
    return [...fromApi, optimisticAssistant];
  }

  return [...fromApi, optimisticUser, optimisticAssistant];
}
