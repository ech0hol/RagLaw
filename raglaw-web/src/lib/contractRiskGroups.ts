import type { ContractRisk } from './api';

export type ContractChunk = {
  id: string;
  chunkIndex: number;
  content: string;
  chunkLevel?: string | null;
};

export type ClauseRiskGroup = {
  chunkId: string;
  chunkIndex: number;
  content: string;
  risks: ContractRisk[];
};

function longestExcerpt(risks: ContractRisk[]) {
  return risks.reduce((longest, risk) => {
    const excerpt = risk.excerpt?.trim() ?? '';
    return excerpt.length > longest.length ? excerpt : longest;
  }, '');
}

function riskSortKey(risk: ContractRisk) {
  if (risk.createdAt) {
    const time = Date.parse(risk.createdAt);
    if (!Number.isNaN(time)) {
      return time;
    }
  }
  return 0;
}

export function groupRisksByChunk(
  risks: ContractRisk[],
  chunks: ContractChunk[],
): ClauseRiskGroup[] {
  if (risks.length === 0) {
    return [];
  }

  const chunkById = new Map(chunks.map((chunk) => [chunk.id, chunk]));
  const grouped = new Map<string, ContractRisk[]>();

  for (const risk of risks) {
    const chunkId = risk.chunkId || `orphan-${risk.id}`;
    const existing = grouped.get(chunkId);
    if (existing) {
      existing.push(risk);
    } else {
      grouped.set(chunkId, [risk]);
    }
  }

  const groups: ClauseRiskGroup[] = [];
  for (const [chunkId, chunkRisks] of grouped) {
    const chunk = chunkById.get(chunkId);
    const content = chunk?.content?.trim() || longestExcerpt(chunkRisks);
    groups.push({
      chunkId,
      chunkIndex: chunk?.chunkIndex ?? Number.MAX_SAFE_INTEGER,
      content,
      risks: [...chunkRisks].sort((a, b) => riskSortKey(a) - riskSortKey(b)),
    });
  }

  return groups.sort((a, b) => {
    if (a.chunkIndex !== b.chunkIndex) {
      return a.chunkIndex - b.chunkIndex;
    }
    return riskSortKey(a.risks[0]) - riskSortKey(b.risks[0]);
  });
}
