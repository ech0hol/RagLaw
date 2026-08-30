import type { ChatReference } from './ReferenceList';
import { stripOrphanFootnotes } from './citationMarkdownUtils';

export type CitationMeta = {
  lawName?: string;
  articleSuffix?: string;
  refIndex?: number;
  bold: boolean;
  reference?: ChatReference;
};

export type PreprocessResult = {
  markdown: string;
  citationMap: Map<string, CitationMeta>;
};

const CODE_SEGMENT_PATTERN = /(```[\s\S]*?```|`[^`\n]+`)/g;
const COMBINED_CITATION_PATTERN =
  /\*\*《([^》]+)》(.*?)?\*\*(?:\[(\d+)\])?|《([^》]+)》(?:\[(\d+)\])?/g;

function normalizeLawName(name: string): string {
  return name.replace(/[《》\s]/g, '');
}

export function lawNamesMatch(cited: string, title: string): boolean {
  const a = normalizeLawName(cited);
  const b = normalizeLawName(title);
  if (!a || !b) return false;
  return a.includes(b) || b.includes(a);
}

function findLawReference(lawTitle: string, references: ChatReference[]): ChatReference | undefined {
  return references.find((ref) => ref.title && lawNamesMatch(lawTitle, ref.title));
}

function findRefReference(index: number, references: ChatReference[]): ChatReference | undefined {
  return references.find((ref) => ref.index === index);
}

function resolveReference(
  lawName: string | undefined,
  refIndex: number | undefined,
  references: ChatReference[],
): ChatReference | undefined {
  if (refIndex != null) {
    return findRefReference(refIndex, references);
  }
  if (lawName) {
    return findLawReference(lawName, references);
  }
  return undefined;
}

function formatLawLabel(lawName: string, articleSuffix: string | undefined, bold: boolean): string {
  const core = `《${lawName}》${articleSuffix ?? ''}`;
  return bold ? `**${core}**` : core;
}

function processTextSegment(
  text: string,
  references: ChatReference[],
  citationMap: Map<string, CitationMeta>,
  nextId: { value: number },
): string {
  return text.replace(COMBINED_CITATION_PATTERN, (match, ...groups) => {
    const [boldLawName, articleSuffix, boldRefIndex, plainLawName, plainRefIndex] = groups;

    if (boldLawName) {
      const refIndex = boldRefIndex ? Number(boldRefIndex) : undefined;
      const resolved = resolveReference(boldLawName, refIndex, references);
      if (!resolved) {
        const label = formatLawLabel(boldLawName, articleSuffix, true);
        return refIndex != null ? `${label}[${refIndex}]` : label;
      }
      const href = `#raglaw-cite-${nextId.value++}`;
      citationMap.set(href, {
        lawName: boldLawName,
        articleSuffix: articleSuffix ?? '',
        refIndex,
        bold: true,
        reference: resolved,
      });
      return `[${formatLawLabel(boldLawName, articleSuffix, true)}](${href})`;
    }

    if (plainLawName) {
      const refIndex = plainRefIndex ? Number(plainRefIndex) : undefined;
      const resolved = resolveReference(plainLawName, refIndex, references);
      if (!resolved) {
        const label = formatLawLabel(plainLawName, '', false);
        return label;
      }
      const href = `#raglaw-cite-${nextId.value++}`;
      citationMap.set(href, {
        lawName: plainLawName,
        articleSuffix: '',
        refIndex,
        bold: false,
        reference: resolved,
      });
      return `[${formatLawLabel(plainLawName, '', false)}](${href})`;
    }

    return match;
  });
}

export function preprocessCitationLinks(
  content: string,
  references: ChatReference[] = [],
): PreprocessResult {
  const citationMap = new Map<string, CitationMeta>();
  const nextId = { value: 1 };
  const parts = content.split(CODE_SEGMENT_PATTERN);
  const processed = parts
    .map((part, index) => (index % 2 === 1 ? part : processTextSegment(part, references, citationMap, nextId)))
    .join('');
  return { markdown: stripOrphanFootnotes(processed), citationMap };
}
