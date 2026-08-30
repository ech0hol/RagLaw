export function formatLocalDate(value?: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleDateString('zh-CN');
}

const DOC_TYPE_LABELS: Record<string, string> = {
  STATUTE: '法规',
  CASE: '案例',
};

export function formatRelevanceScore(score: number): string {
  if (!Number.isFinite(score) || score < 0.0001) {
    return '相关';
  }
  if (score < 0.01) {
    return score.toFixed(4);
  }
  return score.toFixed(2);
}

export function formatDocTypeLabel(docType?: string | null): string {
  if (!docType) {
    return '文档';
  }
  return DOC_TYPE_LABELS[docType] ?? docType;
}
