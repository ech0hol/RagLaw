import type { ChatReference } from '../components/ReferenceList';

const ARTICLE_ANCHOR_PATTERN = /第[^\s，。；：、]{1,12}条/g;
const CHAPTER_OR_SECTION_LINE = /^第[^\s，。；：、]{1,12}[章节编].*$/;

function looksLikeChapterHeading(text: string): boolean {
  const trimmed = text.trim();
  return trimmed.length > 0
    && trimmed.length <= 30
    && trimmed.startsWith('第')
    && trimmed.includes('章');
}

function containsArticleBody(text: string): boolean {
  const trimmed = text.trim();
  return trimmed.includes('条') && !looksLikeChapterHeading(trimmed);
}

export function extractArticleAnchors(text: string): string[] {
  if (!text?.trim()) return [];
  const anchors: string[] = [];
  const pattern = new RegExp(ARTICLE_ANCHOR_PATTERN.source, 'g');
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text.trim())) !== null) {
    anchors.push(match[0]);
  }
  return anchors;
}

export function looksLikeTableOfContents(text: string): boolean {
  if (!text?.trim()) return false;
  if (containsArticleBody(text)) return false;
  const trimmed = text.trim();
  if (trimmed.startsWith('目录') || trimmed.startsWith('## 目录')) return true;
  const lines = trimmed.split(/\r?\n/);
  let chapterSectionLines = 0;
  for (const line of lines) {
    const normalized = line.trim();
    if (!normalized) continue;
    if (CHAPTER_OR_SECTION_LINE.test(normalized) || looksLikeChapterHeading(normalized)) {
      chapterSectionLines += 1;
    }
  }
  return chapterSectionLines >= 2;
}

function findTermIndex(text: string, term: string, fromIndex = 0): number {
  const index = text.indexOf(term, fromIndex);
  if (index >= 0) return index;
  return text.toLowerCase().indexOf(term.toLowerCase(), fromIndex);
}

function buildSnippet(text: string, index: number, maxChars: number): string {
  const contextChars = 120;
  const start = Math.max(0, index - contextChars);
  const end = Math.min(text.length, index + contextChars);
  let snippet = text.substring(start, end).trim();
  if (start > 0) snippet = `…${snippet}`;
  if (end < text.length) snippet = `${snippet}…`;
  if (maxChars > 0 && snippet.length > maxChars) {
    return `${snippet.slice(0, maxChars)}…`;
  }
  return snippet;
}

function expandArticleAnchorVariants(anchor: string): string[] {
  const variants = [anchor.trim()];
  const match = anchor.match(/第(\d+)条/);
  if (match) {
    const chinese = arabicToChineseArticleNumber(match[1]);
    if (chinese) variants.push(`第${chinese}条`);
  }
  return variants;
}

function arabicToChineseArticleNumber(digits: string): string {
  const value = Number.parseInt(digits, 10);
  if (!Number.isFinite(value) || value <= 0) return '';
  const digitMap = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
  if (value < 10) return digitMap[value];
  if (value < 20) return `十${value === 10 ? '' : digitMap[value - 10]}`;
  if (value < 100) {
    const tens = Math.floor(value / 10);
    const ones = value % 10;
    return `${digitMap[tens]}十${ones === 0 ? '' : digitMap[ones]}`;
  }
  if (value < 1000) {
    const hundreds = Math.floor(value / 100);
    const remainder = value % 100;
    if (remainder === 0) return `${digitMap[hundreds]}百`;
    if (remainder < 10) return `${digitMap[hundreds]}百零${digitMap[remainder]}`;
    return `${digitMap[hundreds]}百${arabicToChineseArticleNumber(String(remainder))}`;
  }
  return String(value);
}

export function extractAroundAnchor(fullText: string, anchor: string, maxChars: number): string {
  if (!fullText?.trim() || !anchor?.trim()) return '';
  for (const variant of expandArticleAnchorVariants(anchor)) {
    const index = findTermIndex(fullText, variant);
    if (index >= 0) {
      return buildSnippet(fullText, index, maxChars);
    }
  }
  return '';
}

export function extractAroundArticles(fullText: string, anchors: string[], maxChars = 320): string {
  if (!fullText?.trim() || anchors.length === 0) return '';
  const perAnchor = Math.max(80, Math.floor(maxChars / anchors.length));
  const parts: string[] = [];
  for (const anchor of anchors) {
    const snippet = extractAroundAnchor(fullText, anchor, perAnchor);
    if (!snippet || looksLikeTableOfContents(snippet)) continue;
    parts.push(snippet);
  }
  const combined = parts.join('\n\n');
  if (maxChars > 0 && combined.length > maxChars) {
    return `${combined.slice(0, maxChars)}…`;
  }
  return combined;
}

export function extractCitedArticleSnippet(
  ref: ChatReference,
  citedArticleLabel?: string,
): string {
  if (!citedArticleLabel?.trim()) return '';
  const sourceText = ref.content?.trim() || ref.excerpt?.trim() || '';
  if (!sourceText) return '';
  const anchors = extractArticleAnchors(citedArticleLabel);
  if (anchors.length === 0) return '';
  const fromArticles = extractAroundArticles(sourceText, anchors);
  if (!fromArticles || looksLikeTableOfContents(fromArticles)) return '';
  return fromArticles;
}

export function referenceSnippet(ref: ChatReference, citedArticleLabel?: string): string {
  if (citedArticleLabel) {
    const cited = extractCitedArticleSnippet(ref, citedArticleLabel);
    if (cited) return cited;
    return '';
  }

  const excerpt = ref.excerpt?.trim() || '';
  const content = ref.content?.trim() || '';

  if (excerpt && looksLikeChapterHeading(excerpt) && content.length > excerpt.length && containsArticleBody(content)) {
    return content;
  }

  if (!excerpt) {
    return looksLikeTableOfContents(content) ? '' : content;
  }
  if (!content) {
    return looksLikeTableOfContents(excerpt) ? '' : excerpt;
  }

  const candidate = excerpt;
  if (looksLikeTableOfContents(candidate)) {
    return '';
  }
  return candidate;
}

export function needsRemoteExcerpt(ref: ChatReference, citedArticleLabel?: string): boolean {
  if (!ref.documentId || !citedArticleLabel?.trim()) return false;
  const local = extractCitedArticleSnippet(ref, citedArticleLabel);
  return !local;
}
