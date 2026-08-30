const CODE_SEGMENT_PATTERN = /(```[\s\S]*?```|`[^`\n]+`)/g;
const ORPHAN_FOOTNOTE_PATTERN = /(?<![》*])\[\d+\]/g;
const CITED_FOOTNOTE_PATTERN = /\*\*《[^》]+》[^*]*\*\*\[(\d+)\]/g;
const CITED_PLAIN_FOOTNOTE_PATTERN = /《[^》]+》\[(\d+)\]/g;
const RETRIEVAL_STATUS_PREFIX = /^\s*正在(?:检索|查询|搜索|分析|整理).{0,120}?[。…\\.]{1,3}\s*/m;
const CONTRADICTORY_RETRIEVAL_DISCLAIMER =
  /(?<=[。！？\n])[^。！？\n]*(?:未检索到|知识库未检索|无法基于文档作答)[^。！？\n]*[。！？]?\s*$/;
const FIRST_NUMBERED_SECTION_LINE = /^1\.\s+\*\*/gm;

export function dedupeRepeatedAnswerBlocks(content: string): string {
  if (!content?.trim()) {
    return content ?? '';
  }
  const matcher = content.matchAll(FIRST_NUMBERED_SECTION_LINE);
  let second = -1;
  let seen = 0;
  for (const match of matcher) {
    if (match.index == null) {
      continue;
    }
    seen++;
    if (seen === 2) {
      second = match.index;
      break;
    }
  }
  if (second < 0) {
    return content;
  }
  return content.substring(second).trim();
}

export function hasDuplicateAnswerBlocks(content: string): boolean {
  const matcher = content.matchAll(FIRST_NUMBERED_SECTION_LINE);
  let count = 0;
  for (const _match of matcher) {
    count++;
    if (count > 1) {
      return true;
    }
  }
  return false;
}

export function stripOrphanFootnotes(content: string): string {
  const parts = content.split(CODE_SEGMENT_PATTERN);
  return parts
    .map((part, index) => (index % 2 === 1 ? part : part.replace(ORPHAN_FOOTNOTE_PATTERN, '')))
    .join('');
}

const NESTED_LIST_INDENT = '   ';

function indentNestedListUnderNumberedSections(content: string): string {
  const lines = content.split('\n');
  const result: string[] = [];
  let underNumberedSection = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (/^\d+\.\s+\*\*/.test(trimmed)) {
      underNumberedSection = true;
      result.push(line);
      continue;
    }
    if (underNumberedSection && /^-\s/.test(trimmed) && !line.startsWith(NESTED_LIST_INDENT)) {
      result.push(`${NESTED_LIST_INDENT}${trimmed}`);
      continue;
    }
    if (
      underNumberedSection
      && trimmed
      && !/^-\s/.test(trimmed)
      && !/^\d+\.\s/.test(trimmed)
    ) {
      underNumberedSection = false;
    }
    result.push(line);
  }
  return result.join('\n');
}

export function stripRetrievalStatusPrefix(content: string): string {
  if (!content?.trim()) return content ?? '';
  const stripped = content.replace(RETRIEVAL_STATUS_PREFIX, '');
  return stripped.trim() ? stripped : content.trim();
}

export function stripContradictoryRetrievalDisclaimer(
  content: string,
  hasRetrievalEvidence: boolean,
): string {
  if (!content?.trim()) return content ?? '';
  const hasFootnotes = extractCitedReferenceIndices(content).length > 0;
  if (!hasFootnotes && !hasRetrievalEvidence) {
    return content;
  }
  const stripped = content.replace(CONTRADICTORY_RETRIEVAL_DISCLAIMER, '').trim();
  return stripped || content.trim();
}

export function prepareCitationDisplayContent(
  content: string,
  hasReferences = false,
): string {
  const deduped = hasDuplicateAnswerBlocks(content)
    ? dedupeRepeatedAnswerBlocks(content)
    : content;
  const withoutStatus = stripRetrievalStatusPrefix(deduped);
  const withoutDisclaimer = stripContradictoryRetrievalDisclaimer(
    withoutStatus,
    hasReferences || extractCitedReferenceIndices(withoutStatus).length > 0,
  );
  return normalizeCitationMarkdown(withoutDisclaimer);
}

export function normalizeCitationMarkdown(content: string): string {
  return indentNestedListUnderNumberedSections(
    content
      .replace(/\*\*\s*\n\s*《/g, '**《')
      .replace(/》\s*\n+\s*([^*\n]+?)\s*\n+\s*\*\*/g, '》$1**')
      .replace(/》\s*\n\s*\*\*/g, '》**')
      .replace(/\*\*\s+(?=[《])/g, '**')
      .replace(/(?<=[》])\s+\*\*/g, '**')
      .replace(/》\s*\n+\s*\[(\d+)\]/g, '》[$1]')
      .replace(/\*\*\s*\n+\s*\[(\d+)\]/g, '**[$1]')
      .replace(/\n+\s*([，。；：、])/g, '$1')
      .replace(
        /([，。；：、])\s*\n(?!\n)(?!\*\*[一二三四五六七八九十百]+、)(?!\d+\.\s)(?![一二三四五六七八九十百]+、)(?!\s*[-*]\s)(?!\*\s)/g,
        '$1',
      )
      .replace(/(\d+\.\s+\*\*[^*\n]+?\*\*)\s*\n\s*(-\s)/g, '$1\n   $2')
      .replace(/(\d+\.\s+\*\*[^*\n]+?\*\*)\s+(-\s)/g, '$1\n   $2')
      .replace(/([。！？])\s*(\d+\.\s)/g, '$1\n\n$2')
      .replace(/([。！？])\s*(首先|其次|此外|最后|综上)/g, '$1\n\n$2')
      .replace(/([。！？])\s*(\*\*[一二三四五六七八九十百]+、[^*]*\*\*)/g, '$1\n\n$2')
      .replace(/([。！？])\s*([一二三四五六七八九十百]+、)/g, '$1\n\n$2')
      .replace(/([。！？])\s*(-\s+\*\*)/g, '$1\n\n$2')
      .replace(/([。！？])\s*\n-\s/g, '$1\n\n- ')
      .replace(/；\s*-\s/g, '；\n\n- '),
  );
}

const BOLD_CITED_ARTICLE_PATTERN = /\*\*《[^》]+》(.*?)\*\*\[(\d+)\]/g;
const PLAIN_CITED_ARTICLE_PATTERN = /《[^》]+》([^[\n]*?)\[(\d+)\]/g;

export function extractCitedArticlesByIndex(content: string): Map<number, string> {
  const result = new Map<number, string>();
  let match: RegExpExecArray | null;
  const boldPattern = new RegExp(BOLD_CITED_ARTICLE_PATTERN.source, 'g');
  while ((match = boldPattern.exec(content)) !== null) {
    const articlePart = match[1]?.trim() ?? '';
    const index = Number(match[2]);
    if (!Number.isFinite(index)) continue;
    const label = extractArticleLabel(articlePart) ?? (articlePart || undefined);
    if (label) {
      result.set(index, label);
    }
  }
  const plainPattern = new RegExp(PLAIN_CITED_ARTICLE_PATTERN.source, 'g');
  while ((match = plainPattern.exec(content)) !== null) {
    const articlePart = match[1]?.trim() ?? '';
    const index = Number(match[2]);
    if (!Number.isFinite(index) || result.has(index)) continue;
    const label = extractArticleLabel(articlePart) ?? (articlePart || undefined);
    if (label) {
      result.set(index, label);
    }
  }
  return result;
}

export function extractCitedReferenceIndices(content: string): number[] {
  const indices = new Set<number>();
  let match: RegExpExecArray | null;
  const boldPattern = new RegExp(CITED_FOOTNOTE_PATTERN.source, 'g');
  while ((match = boldPattern.exec(content)) !== null) {
    indices.add(Number(match[1]));
  }
  const plainPattern = new RegExp(CITED_PLAIN_FOOTNOTE_PATTERN.source, 'g');
  while ((match = plainPattern.exec(content)) !== null) {
    indices.add(Number(match[1]));
  }
  return [...indices].sort((a, b) => a - b);
}

export const ARTICLE_LABEL_PATTERN = /第[^\s，。；：、]{1,12}条/;

export function extractArticleLabel(text: string): string | undefined {
  const match = text.match(ARTICLE_LABEL_PATTERN);
  return match?.[0];
}

export function articleLabelsMismatch(cited?: string, excerpt?: string): boolean {
  if (!cited || !excerpt) return false;
  const citedLabel = extractArticleLabel(cited);
  const excerptLabel = extractArticleLabel(excerpt);
  if (!citedLabel || !excerptLabel) return false;
  return citedLabel !== excerptLabel;
}
