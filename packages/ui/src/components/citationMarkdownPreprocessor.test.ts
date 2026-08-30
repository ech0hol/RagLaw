import { describe, expect, it } from 'vitest';
import {
  lawNamesMatch,
  preprocessCitationLinks,
} from './citationMarkdownPreprocessor';
import type { ChatReference } from './ReferenceList';

const refs: ChatReference[] = [
  {
    index: 1,
    chunkId: 'c1',
    documentId: 'd1',
    path: '/STATUTE',
    excerpt: 'short excerpt',
    content: '第三条 各级监察委员会是行使国家监察职能的专责机关。',
    score: 0.9,
    title: '中华人民共和国监察法',
  },
];

describe('lawNamesMatch', () => {
  it('matches law names with mutual substring containment', () => {
    expect(lawNamesMatch('危险化学品安全管理条例', '危险化学品安全管理条例')).toBe(true);
    expect(lawNamesMatch('监察法', '中华人民共和国监察法')).toBe(true);
  });
});

describe('preprocessCitationLinks', () => {
  it('converts bold law citation with article and footnote to pseudo link', () => {
    const { markdown, citationMap } = preprocessCitationLinks(
      '依据 **《危化条例》第五十三条**[1] 规定',
      refs,
    );

    expect(markdown).toContain('[**《危化条例》第五十三条**](#raglaw-cite-1)');
    expect(markdown).not.toContain('[1]');
    expect(citationMap.get('#raglaw-cite-1')).toMatchObject({
      lawName: '危化条例',
      articleSuffix: '第五十三条',
      refIndex: 1,
      bold: true,
    });
  });

  it('resolves reference by fuzzy law title match', () => {
    const { citationMap } = preprocessCitationLinks(
      '依据 **《监察法》第三条** 规定',
      refs,
    );

    expect(citationMap.get('#raglaw-cite-1')?.reference?.title).toBe('中华人民共和国监察法');
  });

  it('resolves reference by ref index when law name does not match', () => {
    const { citationMap } = preprocessCitationLinks(
      '依据 **《未知法规》第一条**[1] 规定',
      refs,
    );

    expect(citationMap.get('#raglaw-cite-1')?.reference?.chunkId).toBe('c1');
  });

  it('does not create citation link when law name and index both fail', () => {
    const { markdown, citationMap } = preprocessCitationLinks(
      '依据 **《未知法规》第一条**[9] 规定',
      refs,
    );

    expect(markdown).toBe('依据 **《未知法规》第一条**[9] 规定');
    expect(citationMap.size).toBe(0);
  });

  it('does not fall back to law name when ref index is missing', () => {
    const multiRefs: ChatReference[] = [
      { ...refs[0], index: 1, chunkId: 'c1', title: '中华人民共和国刑法' },
      { ...refs[0], index: 2, chunkId: 'c2', title: '中华人民共和国刑法' },
    ];
    const { citationMap } = preprocessCitationLinks(
      '依据 **《中华人民共和国刑法》第35条**[2] 规定',
      multiRefs,
    );

    expect(citationMap.get('#raglaw-cite-1')?.reference?.chunkId).toBe('c2');
    expect(citationMap.get('#raglaw-cite-1')?.reference?.chunkId).not.toBe('c1');
  });

  it('leaves ordinary bold text untouched for markdown rendering', () => {
    const { markdown } = preprocessCitationLinks('**单纯想买不违法**', []);

    expect(markdown).toBe('**单纯想买不违法**');
  });

  it('keeps plain law citation as text when no backing reference exists', () => {
    const { markdown, citationMap } = preprocessCitationLinks('参见《劳动合同法》[3]', []);

    expect(markdown).toBe('参见《劳动合同法》');
    expect(citationMap.size).toBe(0);
  });

  it('strips standalone orphan footnotes', () => {
    const { markdown } = preprocessCitationLinks('见上文[4]说明，末尾[5] [1]', []);

    expect(markdown).toBe('见上文说明，末尾 ');
    expect(markdown).not.toMatch(/\[\d+\]/);
  });

  it('does not modify citations inside inline code', () => {
    const { markdown } = preprocessCitationLinks('`《条例》[1]`', []);

    expect(markdown).toBe('`《条例》[1]`');
  });

  it('does not create citation link for bold law without backing reference', () => {
    const { markdown, citationMap } = preprocessCitationLinks('**《A》第三条**[2]', []);

    expect(markdown).toBe('**《A》第三条**[2]');
    expect(citationMap.size).toBe(0);
  });
});
