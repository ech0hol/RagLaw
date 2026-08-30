import { describe, expect, it } from 'vitest';
import type { ContractReview } from './api';
import { needsHeavyReview, needsIngest, needsLlmReview } from './contractReviewLoad';

function review(overrides: Partial<ContractReview> = {}): ContractReview {
  return {
    documentId: 'doc-1',
    suggestedAgentCode: 'CONTRACT',
    extractMethod: 'text',
    ocrUsed: false,
    risks: [],
    ...overrides,
  };
}

describe('contractReviewLoad', () => {
  it('skips ingest when risks already exist', () => {
    const data = review({ ingestStage: 'PENDING', risks: [{ id: 'r1' } as ContractReview['risks'][0]] });
    expect(needsIngest(data)).toBe(false);
  });

  it('skips llm review when risks already exist despite NOT_RUN status', () => {
    const data = review({
      reviewStatus: 'NOT_RUN',
      risks: [{ id: 'r1' } as ContractReview['risks'][0]],
    });
    expect(needsLlmReview(data)).toBe(false);
    expect(needsHeavyReview(data)).toBe(false);
  });

  it('requires llm review when parsed and status is NOT_RUN', () => {
    const data = review({ ingestStage: 'PARSED', reviewStatus: 'NOT_RUN', risks: [] });
    expect(needsLlmReview(data)).toBe(true);
    expect(needsHeavyReview(data)).toBe(true);
    expect(needsIngest(data)).toBe(false);
  });

  it('requires parse when stage is pending and no risks', () => {
    const data = review({ ingestStage: 'PENDING', reviewStatus: 'NOT_RUN', risks: [] });
    expect(needsIngest(data)).toBe(true);
  });
});
