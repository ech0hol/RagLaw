import { describe, expect, it } from 'vitest';
import {
  applyAguiSseEvent,
  createAguiSseParseState,
} from './aguiSseParser';

describe('aguiSseParser', () => {
  it('accumulates text deltas', () => {
    let state = createAguiSseParseState();
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '你好' }));
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '世界' }));
    expect(state.assistant).toBe('你好世界');
  });

  it('clears assistant content on text_reset', () => {
    let state = createAguiSseParseState();
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '草稿回答1. **标题**' }));
    state = applyAguiSseEvent(state, 'text_reset', '{}');
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '正式回答' }));
    expect(state.assistant).toBe('正式回答');
  });

  it('replaces assistant content on done', () => {
    let state = createAguiSseParseState();
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '流式草稿' }));
    state = applyAguiSseEvent(
      state,
      'done',
      JSON.stringify({ messageId: 'm1', content: '清洗后正文' }),
    );
    expect(state.assistant).toBe('清洗后正文');
    expect(state.messageId).toBe('m1');
  });

  it('simulates react draft then tool then final answer without duplication', () => {
    let state = createAguiSseParseState();
    state = applyAguiSseEvent(state, 'text', JSON.stringify({ delta: '可以异地报销。\n\n1. **条件**' }));
    state = applyAguiSseEvent(state, 'status', JSON.stringify({ message: '正在检索知识库…' }));
    state = applyAguiSseEvent(state, 'text_reset', '{}');
    state = applyAguiSseEvent(
      state,
      'text',
      JSON.stringify({ delta: '可以异地报销。\n\n1. **备案要求**\n2. **报销流程**' }),
    );
    state = applyAguiSseEvent(
      state,
      'done',
      JSON.stringify({ content: '可以异地报销。\n\n1. **备案要求**\n2. **报销流程**' }),
    );
    expect(state.assistant).not.toContain('1. **条件**');
    expect(state.assistant).toContain('1. **备案要求**');
    expect((state.assistant.match(/1\.\s+\*\*/g) ?? []).length).toBe(1);
  });
});
