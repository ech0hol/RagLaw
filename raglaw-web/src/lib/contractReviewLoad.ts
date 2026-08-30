import type { ContractReview } from './api';

function hasPersistedReview(review: ContractReview): boolean {
  return review.risks.length > 0;
}

export function needsParse(review: ContractReview): boolean {
  if (hasPersistedReview(review)) return false;
  const stage = review.ingestStage ?? 'PENDING';
  return stage !== 'PARSED' && stage !== 'INDEXED';
}

/** @deprecated Use needsParse for two-phase contract review. */
export function needsIngest(review: ContractReview): boolean {
  return needsParse(review);
}

export function needsLlmReview(review: ContractReview): boolean {
  if (hasPersistedReview(review)) return false;
  const stage = review.ingestStage ?? 'PENDING';
  const parsed = stage === 'PARSED' || stage === 'INDEXED';
  if (!parsed) return false;
  const status = review.reviewStatus ?? 'NOT_RUN';
  return status === 'NOT_RUN' || status === 'RUNNING';
}

export function needsHeavyReview(review: ContractReview): boolean {
  return needsParse(review) || needsLlmReview(review);
}
