import {
  extractCitedReferenceIndices,
  hasDuplicateAnswerBlocks,
} from '../components/citationMarkdownUtils';

const CONTRADICTORY_DISCLAIMER = /未检索到|知识库未检索|无法基于文档作答/;

export function countNumberedSectionStarts(content: string): number {
  const matches = content.match(/(?:^|\n\n)1\.\s+\*\*/g);
  return matches?.length ?? 0;
}

export { hasDuplicateAnswerBlocks };

export function hasContradictoryRetrievalDisclaimer(content: string, referenceCount: number): boolean {
  if (referenceCount <= 0) return false;
  return CONTRADICTORY_DISCLAIMER.test(content);
}

export function footnoteCount(content: string): number {
  return extractCitedReferenceIndices(content).length;
}

export type AnswerQualityReport = {
  duplicateBlocks: boolean;
  contradictoryDisclaimer: boolean;
  numberedSectionStarts: number;
  footnoteIndices: number[];
};

export function lintAnswerQuality(content: string, referenceCount = 0): AnswerQualityReport {
  const footnoteIndices = extractCitedReferenceIndices(content);
  return {
    duplicateBlocks: hasDuplicateAnswerBlocks(content),
    contradictoryDisclaimer: hasContradictoryRetrievalDisclaimer(content, referenceCount),
    numberedSectionStarts: countNumberedSectionStarts(content),
    footnoteIndices,
  };
}

export function passesAnswerQualityGates(content: string, referenceCount = 0): boolean {
  const report = lintAnswerQuality(content, referenceCount);
  return !report.duplicateBlocks && !report.contradictoryDisclaimer;
}
