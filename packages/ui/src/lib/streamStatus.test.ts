import { describe, expect, it } from 'vitest';
import { isToolStreamStatus } from './streamStatus';

describe('isToolStreamStatus', () => {
  it('returns true for knowledge and web search hints', () => {
    expect(isToolStreamStatus('正在检索知识库…')).toBe(true);
    expect(isToolStreamStatus('正在联网检索…')).toBe(true);
  });

  it('returns false for generic processing status', () => {
    expect(isToolStreamStatus('正在处理您的问题…')).toBe(false);
    expect(isToolStreamStatus('正在咨询法规助手…')).toBe(false);
  });
});
