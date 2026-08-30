import { describe, expect, it } from 'vitest';
import type { ChatReference } from '../components/ReferenceList';
import {
  extractAroundArticles,
  extractArticleAnchors,
  looksLikeTableOfContents,
  needsRemoteExcerpt,
  referenceSnippet,
} from './referenceSnippet';

describe('referenceSnippet', () => {
  const base: ChatReference = {
    index: 1,
    chunkId: 'chunk-1',
    path: '/STATUTE/CIVIL',
    excerpt: '章节标题',
    score: 1,
  };

  it('prefers excerpt over content', () => {
    expect(referenceSnippet({ ...base, content: '章节标题', excerpt: '条文正文内容' })).toBe('条文正文内容');
  });

  it('falls back to content when excerpt missing', () => {
    expect(referenceSnippet({ ...base, excerpt: '', content: '条文正文内容' })).toBe('条文正文内容');
  });

  it('falls back to excerpt when content missing', () => {
    expect(referenceSnippet(base)).toBe('章节标题');
  });

  it('falls back to content when excerpt is chapter heading and content has article body', () => {
    expect(
      referenceSnippet({
        ...base,
        excerpt: '第一章 总则',
        content: '第七条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。',
      }),
    ).toBe('第七条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。');
  });

  it('returns empty string when both missing', () => {
    expect(referenceSnippet({ ...base, excerpt: '', content: '' })).toBe('');
  });

  it('extracts cited articles from content when excerpt is table of contents', () => {
    const fullText = [
      '第一编 总则',
      '第三章 刑罚',
      '第三十二条 刑罚分为主刑和附加刑。',
      '第三十三条 主刑包括管制、拘役、有期徒刑、无期徒刑和死刑。',
    ].join('\n');
    const ref: ChatReference = {
      ...base,
      excerpt: '第一编 总则\n第三章 刑罚',
      content: fullText,
    };
    const snippet = referenceSnippet(ref, '第32条、第33条');
    expect(snippet).toContain('第三十二条');
    expect(snippet).toContain('第三十三条');
    expect(looksLikeTableOfContents(snippet)).toBe(false);
  });

  it('returns empty when cited article is not in local chunk', () => {
    const ref: ChatReference = {
      ...base,
      documentId: 'doc-1',
      excerpt: '第三十一条 单位犯罪的…\n第三十二条 刑罚分为主刑和附加刑。',
      content: '第三十一条 单位犯罪的…\n第三十二条 刑罚分为主刑和附加刑。',
    };
    expect(referenceSnippet(ref, '第35条')).toBe('');
    expect(needsRemoteExcerpt(ref, '第35条')).toBe(true);
  });

  it('does not fall back to chunk excerpt when cited article missing locally', () => {
    const ref: ChatReference = {
      ...base,
      documentId: 'doc-1',
      excerpt: '第三十一条 单位犯罪的…',
      content: '第三十一条 单位犯罪的…',
    };
    expect(referenceSnippet(ref, '第48条')).toBe('');
  });
});

describe('extractArticleAnchors', () => {
  it('parses multiple article labels', () => {
    expect(extractArticleAnchors('第32条、第33条、第34条')).toEqual(['第32条', '第33条', '第34条']);
  });
});

describe('extractAroundArticles', () => {
  it('joins snippets for multiple anchors', () => {
    const fullText = '第三十二条 刑罚分为主刑和附加刑。\n第三十三条 主刑包括管制。';
    const snippet = extractAroundArticles(fullText, ['第32条', '第33条'], 320);
    expect(snippet).toContain('第三十二条');
    expect(snippet).toContain('第三十三条');
  });
});
