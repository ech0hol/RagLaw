import { describe, expect, it } from 'vitest';
import type { ContractRisk } from './api';
import { groupRisksByChunk, type ContractChunk } from './contractRiskGroups';

function risk(
  id: string,
  chunkId: string,
  excerpt: string,
  createdAt?: string,
): ContractRisk {
  return {
    id,
    documentId: 'doc-1',
    chunkId,
    severity: 'HIGH',
    dimension: '条款风险',
    summary: `风险 ${id}`,
    excerpt,
    suggestion: '建议',
    accepted: false,
    createdAt,
  };
}

const chunks: ContractChunk[] = [
  { id: 'chunk-0', chunkIndex: 0, content: '第一条 租赁标的。' },
  { id: 'chunk-1', chunkIndex: 1, content: '第二条 租金支付。' },
  { id: 'chunk-2', chunkIndex: 2, content: '第三条 违约责任。' },
];

describe('groupRisksByChunk', () => {
  it('returns empty list for no risks', () => {
    expect(groupRisksByChunk([], chunks)).toEqual([]);
  });

  it('merges multiple risks under the same chunk', () => {
    const groups = groupRisksByChunk(
      [
        risk('r1', 'chunk-1', '租金按月支付'),
        risk('r2', 'chunk-1', '逾期违约金'),
      ],
      chunks,
    );

    expect(groups).toHaveLength(1);
    expect(groups[0].chunkId).toBe('chunk-1');
    expect(groups[0].content).toBe('第二条 租金支付。');
    expect(groups[0].risks.map((item) => item.id)).toEqual(['r1', 'r2']);
  });

  it('sorts groups by chunkIndex', () => {
    const groups = groupRisksByChunk(
      [
        risk('r3', 'chunk-2', '违约金过高'),
        risk('r1', 'chunk-0', '标的描述不清'),
        risk('r2', 'chunk-1', '租金条款'),
      ],
      chunks,
    );

    expect(groups.map((group) => group.chunkId)).toEqual(['chunk-0', 'chunk-1', 'chunk-2']);
  });

  it('falls back to longest excerpt when chunk metadata is missing', () => {
    const groups = groupRisksByChunk(
      [risk('r1', 'missing-chunk', '短'), risk('r2', 'missing-chunk', '较长的条款摘录内容')],
      chunks,
    );

    expect(groups).toHaveLength(1);
    expect(groups[0].content).toBe('较长的条款摘录内容');
  });

  it('sorts risks within a group by createdAt', () => {
    const groups = groupRisksByChunk(
      [
        risk('r2', 'chunk-1', 'b', '2026-01-02T00:00:00Z'),
        risk('r1', 'chunk-1', 'a', '2026-01-01T00:00:00Z'),
      ],
      chunks,
    );

    expect(groups[0].risks.map((item) => item.id)).toEqual(['r1', 'r2']);
  });
});
