import { describe, expect, it } from 'vitest';
import {
  buildKnowledgeDetailReturnParams,
  buildKnowledgeListPath,
  buildKnowledgeSearchParams,
  parseKnowledgeSearchParams,
} from './knowledgeSearch';

describe('knowledgeSearch', () => {
  it('parses search params from URL', () => {
    const params = new URLSearchParams('docType=CASE&q=劳动合同&l2Path=/CASE/CIVIL&page=2');
    expect(parseKnowledgeSearchParams(params)).toEqual({
      q: '劳动合同',
      docType: 'CASE',
      l2Path: '/CASE/CIVIL',
      page: 2,
    });
  });

  it('defaults missing values', () => {
    expect(parseKnowledgeSearchParams(new URLSearchParams())).toEqual({
      q: '',
      docType: 'STATUTE',
      l2Path: '',
      page: 0,
    });
  });

  it('builds search params and list path', () => {
    const built = buildKnowledgeSearchParams({
      q: '劳动合同',
      docType: 'STATUTE',
      l2Path: '/STATUTE/CIVIL',
      page: 1,
    });
    expect(built.toString()).toBe('docType=STATUTE&q=%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C&l2Path=%2FSTATUTE%2FCIVIL&page=1');
    expect(buildKnowledgeListPath({
      q: '劳动合同',
      docType: 'STATUTE',
      l2Path: '/STATUTE/CIVIL',
      page: 1,
    })).toBe('/knowledge/statutes?docType=STATUTE&q=%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C&l2Path=%2FSTATUTE%2FCIVIL&page=1');
  });

  it('builds detail return params for search back navigation', () => {
    const params = buildKnowledgeDetailReturnParams({
      q: '劳动合同',
      docType: 'STATUTE',
      l2Path: '/STATUTE/CIVIL',
      page: 0,
    });
    expect(params.get('from')).toBe('search');
    expect(params.get('q')).toBe('劳动合同');
    expect(params.get('docType')).toBe('STATUTE');
    expect(params.get('l2Path')).toBe('/STATUTE/CIVIL');
    expect(params.get('page')).toBeNull();
  });
});
