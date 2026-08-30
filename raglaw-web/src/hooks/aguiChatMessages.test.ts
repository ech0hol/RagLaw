import { describe, expect, it } from 'vitest';
import {
  hasCollapsedListFormatting,
  hasOptimisticStreamingTail,
  mergeStreamingMessages,
  preserveStreamedAssistant,
  shouldKeepStreamedContent,
} from './aguiChatMessages';

describe('aguiChatMessages', () => {
  it('detects optimistic user + empty assistant tail', () => {
    const messages = [
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '' },
    ];
    expect(hasOptimisticStreamingTail(messages)).toBe(true);
  });

  it('merges API user-only result with optimistic assistant during streaming', () => {
    const prev = [
      { role: 'user', content: '我想买硝酸' },
      { role: 'assistant', content: '', references: [] },
    ];
    const fromApi = [{ id: '1', role: 'user', content: '我想买硝酸' }];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toHaveLength(2);
    expect(merged[1]).toMatchObject({ role: 'assistant', content: '' });
  });

  it('preserves optimistic tail when API has historical assistant only', () => {
    const prev = [
      { role: 'user', content: '第一条' },
      { role: 'assistant', content: '回答一' },
      { role: 'user', content: '第二条' },
      { role: 'assistant', content: '', references: [] },
    ];
    const fromApi = [
      { id: '1', role: 'user', content: '第一条' },
      { id: '2', role: 'assistant', content: '回答一' },
    ];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toHaveLength(4);
    expect(merged[3]).toMatchObject({ role: 'assistant', content: '' });
  });

  it('appends optimistic assistant when API ends with matching user in multi-turn', () => {
    const prev = [
      { role: 'user', content: '第一条' },
      { role: 'assistant', content: '回答一' },
      { role: 'user', content: '第二条' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [
      { role: 'user', content: '第一条' },
      { role: 'assistant', content: '回答一' },
      { role: 'user', content: '第二条' },
    ];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toHaveLength(4);
    expect(merged[3]).toMatchObject({ role: 'assistant', content: '' });
  });

  it('returns prev when API is stale and missing optimistic user', () => {
    const prev = [
      { role: 'user', content: '第一条' },
      { role: 'assistant', content: '回答一' },
      { role: 'user', content: '第二条' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [
      { role: 'user', content: '第一条' },
      { role: 'assistant', content: '回答一' },
    ];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toBe(prev);
  });

  it('uses fromApi when current turn assistant is complete on server', () => {
    const prev = [
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '回答' },
    ];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toEqual(fromApi);
  });

  it('does not merge when not streaming', () => {
    const prev = [
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [{ role: 'user', content: '问题' }];
    const merged = mergeStreamingMessages(prev, fromApi, false);
    expect(merged).toEqual(fromApi);
  });

  it('does not merge when optimistic user content differs from API tail user', () => {
    const prev = [
      { role: 'user', content: '新问题' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [{ role: 'user', content: '旧问题' }];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toBe(prev);
  });

  it('appends optimistic user and assistant when API lacks current user', () => {
    const prev = [
      { role: 'user', content: '新问题' },
      { role: 'assistant', content: '' },
    ];
    const fromApi = [
      { role: 'user', content: '旧问题' },
      { role: 'assistant', content: '旧回答' },
      { role: 'user', content: '新问题' },
    ];
    const merged = mergeStreamingMessages(prev, fromApi, true);
    expect(merged).toHaveLength(4);
    expect(merged[3]).toMatchObject({ role: 'assistant', content: '' });
  });

  it('preserves streamed references when API citations are empty', () => {
    const refs = [{ index: 1, chunkId: 'c1', path: '/STATUTE', excerpt: 'excerpt', score: 1 }];
    const prev = [
      { role: 'user', content: '有哪些法规' },
      {
        role: 'assistant',
        content: '回答',
        references: refs,
        webSearchAttempted: false,
      },
    ];
    const fromApi = [
      { id: 'u1', role: 'user', content: '有哪些法规' },
      { id: 'a1', role: 'assistant', content: '回答' },
    ];
    const merged = preserveStreamedAssistant(prev, fromApi);
    expect(merged[1].references).toEqual(refs);
  });

  it('preserves streamed assistant content when API content has collapsed list formatting', () => {
    const prev = [
      { role: 'user', content: '刑法刑罚种类' },
      {
        role: 'assistant',
        content: '1. **主刑**\n- 管制\n- 有期徒刑',
        references: [],
      },
    ];
    const fromApi = [
      { id: 'u1', role: 'user', content: '刑法刑罚种类' },
      {
        id: 'a1',
        role: 'assistant',
        content: '1. **主刑** - 管制； - 有期徒刑',
      },
    ];
    const merged = preserveStreamedAssistant(prev, fromApi);
    expect(merged[1].content).toBe('1. **主刑**\n- 管制\n- 有期徒刑');
    expect(merged[1].id).toBe('a1');
  });

  it('prefers API content when streamed content only adds retrieval status prefix', () => {
    const prev = [
      { role: 'user', content: '适用条件' },
      {
        role: 'assistant',
        content: '正在检索相关法规的适用条件…\n\n知识库中未检索到明确条款。',
        references: [],
      },
    ];
    const fromApi = [
      { id: 'u1', role: 'user', content: '适用条件' },
      {
        id: 'a1',
        role: 'assistant',
        content: '知识库中未检索到明确条款。',
      },
    ];
    const merged = preserveStreamedAssistant(prev, fromApi);
    expect(merged[1].content).toBe('知识库中未检索到明确条款。');
  });

  it('detects collapsed list formatting', () => {
    expect(hasCollapsedListFormatting('1. **主刑** - 管制； - 有期徒刑')).toBe(true);
    expect(hasCollapsedListFormatting('1. **主刑**\n- 管制')).toBe(false);
    expect(shouldKeepStreamedContent('1. **主刑**\n- 管制', '1. **主刑** - 管制； - 有期徒刑')).toBe(true);
    expect(shouldKeepStreamedContent('正在检索…\n\n正文', '正文')).toBe(false);
  });

  it('keeps API references when citations exist', () => {
    const apiRefs = [{ index: 1, chunkId: 'api', path: '/CASE', excerpt: 'api', score: 1 }];
    const prev = [
      { role: 'user', content: '问题' },
      {
        role: 'assistant',
        content: '回答',
        references: [{ index: 1, chunkId: 'stream', path: '/STATUTE', excerpt: 'stream', score: 1 }],
      },
    ];
    const fromApi = [
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '回答', references: apiRefs },
    ];
    const merged = preserveStreamedAssistant(prev, fromApi);
    expect(merged[1].references).toEqual(apiRefs);
  });
});
