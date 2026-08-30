import { describe, expect, it } from 'vitest';
import {
  articleLabelsMismatch,
  extractCitedArticlesByIndex,
  extractCitedReferenceIndices,
  normalizeCitationMarkdown,
  prepareCitationDisplayContent,
  stripContradictoryRetrievalDisclaimer,
  stripOrphanFootnotes,
  stripRetrievalStatusPrefix,
} from './citationMarkdownUtils';

describe('stripRetrievalStatusPrefix', () => {
  it('removes leading retrieval status sentence', () => {
    const input = '正在检索相关法规的适用条件…\n\n正文开始。';
    expect(stripRetrievalStatusPrefix(input)).toBe('正文开始。');
  });
});

describe('stripContradictoryRetrievalDisclaimer', () => {
  it('removes trailing disclaimer when references exist', () => {
    const input = '正文内容。\n\n当前知识库未检索到与问题直接对应的法规片段。';
    expect(stripContradictoryRetrievalDisclaimer(input, true)).toBe('正文内容。');
  });

  it('removes trailing disclaimer when footnotes exist', () => {
    const input = '依据 **《刑法》第三十三条**[1]。\n\n知识库未检索到其他法规。';
    expect(stripContradictoryRetrievalDisclaimer(input, false)).toBe('依据 **《刑法》第三十三条**[1]。');
  });
});

describe('prepareCitationDisplayContent', () => {
  it('strips retrieval prefix and contradictory disclaimer together', () => {
    const input = '正在检索…\n\n依据 **《刑法》第三十三条**[1]。\n\n知识库未检索到其他法规。';
    const prepared = prepareCitationDisplayContent(input, true);
    expect(prepared).not.toContain('正在检索');
    expect(prepared).not.toContain('未检索到');
    expect(prepared).toContain('**《刑法》第三十三条**[1]');
  });

  it('dedupes interleaved duplicate answer blocks for display', () => {
    const input = [
      '可以异地报销。',
      '',
      '1. **异地就医前提**',
      '- 须备案',
      '',
      '2. **定点医院**',
      '- 选定点',
      '',
      '1. **必须事先备案**',
      '- 线上备案',
      '',
      '2. **报销流程**',
      '- 持票报销',
    ].join('\n');
    const prepared = prepareCitationDisplayContent(input, true);
    expect(prepared).toContain('1. **必须事先备案**');
    expect(prepared).not.toContain('1. **异地就医前提**');
  });
});

describe('normalizeCitationMarkdown', () => {
  it('merges broken bold law citations', () => {
    const input = '须遵守 **\n\n《危险化学品安全管理条例》\n**\n\n[5]';
    const normalized = normalizeCitationMarkdown(input);
    expect(normalized).toContain('**《危险化学品安全管理条例》**[5]');
    expect(normalized).not.toContain('**\n');
  });

  it('merges article suffix inside bold law citation', () => {
    const input = '依据 **《危化条例》\n第五十三条\n**[1]';
    const normalized = normalizeCitationMarkdown(input);
    expect(normalized).toContain('**《危化条例》第五十三条**[1]');
  });

  it('merges line-leading periods and commas', () => {
    expect(normalizeCitationMarkdown('第一段结束\n。第二段')).toBe('第一段结束。第二段');
    expect(normalizeCitationMarkdown('第一段结束\n，第二段')).toBe('第一段结束，第二段');
  });

  it('preserves paragraph breaks', () => {
    const input = '段落A\n\n段落B';
    expect(normalizeCitationMarkdown(input)).toBe('段落A\n\n段落B');
  });

  it('preserves blank line before numbered sections', () => {
    const input = '…明确体现。\n\n1. **注册条件与禁止性规定**';
    expect(normalizeCitationMarkdown(input)).toBe(input);
  });

  it('inserts blank line before numbered section after period on same line', () => {
    const input = '…明确体现。1. **注册条件**';
    expect(normalizeCitationMarkdown(input)).toBe('…明确体现。\n\n1. **注册条件**');
  });

  it('inserts paragraph break before discourse markers', () => {
    const input = '结论。首先说明';
    expect(normalizeCitationMarkdown(input)).toBe('结论。\n\n首先说明');
  });

  it('preserves newline before Chinese section header', () => {
    const input = '指普通话和规范汉字。\n**一、法律定义**';
    expect(normalizeCitationMarkdown(input)).toBe('指普通话和规范汉字。\n\n**一、法律定义**');
    expect(normalizeCitationMarkdown(input)).not.toContain('汉字。**一、');
  });

  it('preserves double newline before Chinese section header', () => {
    const input = '指普通话和规范汉字。\n\n**一、法律定义**';
    expect(normalizeCitationMarkdown(input)).toBe(input);
  });

  it('inserts blank line before inline Chinese section header', () => {
    const input = '指普通话和规范汉字。**一、法律定义**';
    expect(normalizeCitationMarkdown(input)).toBe('指普通话和规范汉字。\n\n**一、法律定义**');
  });

  it('inserts blank line before inline Chinese section header in bullet context', () => {
    const input = '及未被收录的旧字形。**三、适用范围**';
    expect(normalizeCitationMarkdown(input)).toBe('及未被收录的旧字形。\n\n**三、适用范围**');
  });

  it('inserts blank line before list item after newline', () => {
    const input = '国家通用共同语。\n- **规范汉字**：定义';
    expect(normalizeCitationMarkdown(input)).toBe('国家通用共同语。\n\n- **规范汉字**：定义');
    expect(normalizeCitationMarkdown(input)).not.toContain('共同语。-');
  });

  it('inserts blank line before inline list item', () => {
    const input = '国家通用共同语。- **规范汉字**：定义';
    expect(normalizeCitationMarkdown(input)).toBe('国家通用共同语。\n\n- **规范汉字**：定义');
  });

  it('inserts blank line before list item after semicolon on same line', () => {
    const input = '依据 **《刑法》第38条**[1]；- 有期徒刑：依据 **《刑法》第42条**[2]';
    const normalized = normalizeCitationMarkdown(input);
    expect(normalized).toContain('；\n\n- 有期徒刑');
  });

  it('preserves nested list after numbered section heading', () => {
    const input = '1. **民事实体法**\n- 核心依据为 **《民法典》**[1]\n- 合同效力规则';
    const normalized = normalizeCitationMarkdown(input);
    expect(normalized).toContain('1. **民事实体法**\n   - 核心依据');
    expect(normalized).toContain('\n   - 合同效力规则');
    expect(normalized).not.toContain('\n\n- 核心依据');
  });
});

describe('stripOrphanFootnotes', () => {
  it('removes orphan numeric footnotes outside code', () => {
    expect(stripOrphanFootnotes('句末[5] [1]')).toBe('句末 ');
  });

  it('preserves footnotes on law citations', () => {
    expect(stripOrphanFootnotes('依据 **《条例》第三条**[5] 规定')).toBe(
      '依据 **《条例》第三条**[5] 规定',
    );
    expect(stripOrphanFootnotes('参见《劳动合同法》[3]')).toBe('参见《劳动合同法》[3]');
  });

  it('preserves footnotes inside inline code', () => {
    expect(stripOrphanFootnotes('`[1]`')).toBe('`[1]`');
  });
});

describe('extractCitedReferenceIndices', () => {
  it('collects indices from bold and plain citations', () => {
    const content = '见 **《商标法》第八条**[1] 与《劳动合同法》[2]';
    expect(extractCitedReferenceIndices(content)).toEqual([1, 2]);
  });
});

describe('extractCitedArticlesByIndex', () => {
  it('maps footnote index to article label from bold citations', () => {
    const content = '依据 **《刑法》第34条**[1] 与 **《刑法》第三十三条**[2]';
    const map = extractCitedArticlesByIndex(content);
    expect(map.get(1)).toBe('第34条');
    expect(map.get(2)).toBe('第三十三条');
  });

  it('maps plain law citations without bold', () => {
    const content = '参见《劳动合同法》第四十七条[3]';
    const map = extractCitedArticlesByIndex(content);
    expect(map.get(3)).toBe('第四十七条');
  });
});

describe('articleLabelsMismatch', () => {
  it('detects different article numbers', () => {
    expect(articleLabelsMismatch('第八条', '第六条 国家推广')).toBe(true);
    expect(articleLabelsMismatch('第八条', '第八条 文字商标')).toBe(false);
  });
});
