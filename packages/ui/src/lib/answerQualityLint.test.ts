import { describe, expect, it } from 'vitest';
import {
  hasDuplicateAnswerBlocks,
  lintAnswerQuality,
  passesAnswerQualityGates,
} from './answerQualityLint';

describe('answerQualityLint', () => {
  it('detects duplicate numbered answer blocks', () => {
    const content = '可以报销。\n\n1. **条件**\n\n可以报销。\n\n1. **备案**';
    expect(hasDuplicateAnswerBlocks(content)).toBe(true);
    expect(passesAnswerQualityGates(content)).toBe(false);
  });

  it('passes single answer block', () => {
    const content = '可以报销。\n\n1. **备案要求**\n- 要点\n\n2. **报销流程**';
    expect(passesAnswerQualityGates(content)).toBe(true);
  });

  it('flags contradictory disclaimer when references exist', () => {
    const content = '依据 **《刑法》第三十三条**[1]。\n\n知识库未检索到其他法规。';
    const report = lintAnswerQuality(content, 2);
    expect(report.contradictoryDisclaimer).toBe(true);
    expect(passesAnswerQualityGates(content, 2)).toBe(false);
  });
});
