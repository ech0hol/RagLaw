import { describe, expect, it } from 'vitest';
import {
  buildL3UploadOptions,
  filterL3BySelectedPath,
  findSelectionForCategoryId,
  findSelectionForPath,
  listL1Nodes,
  listL2Nodes,
  listL3Nodes,
  resolveCategoryPath,
  resolveUploadCategoryId,
  toCategoryOptions,
  type CategoryNode,
} from './categories';

const sampleTree: CategoryNode[] = [
  {
    id: 'cat_l1_statute',
    parentId: null,
    level: 1,
    code: 'STATUTE',
    name: '法规',
    path: '/STATUTE',
    docType: 'STATUTE',
    sortOrder: 1,
    enabled: true,
    children: [
      {
        id: 'cat_l2_statute_civil',
        parentId: 'cat_l1_statute',
        level: 2,
        code: 'STATUTE_CIVIL',
        name: '民法商法',
        path: '/STATUTE/CIVIL',
        docType: 'STATUTE',
        sortOrder: 2,
        enabled: true,
        children: [
          {
            id: 'cat_l3_statute_civil_labor',
            parentId: 'cat_l2_statute_civil',
            level: 3,
            code: 'STATUTE_CIVIL_LABOR',
            name: '劳动合同法规',
            path: '/STATUTE/CIVIL/LABOR',
            docType: 'STATUTE',
            sortOrder: 1,
            enabled: true,
            children: [],
          },
        ],
      },
      {
        id: 'cat_l2_statute_admin',
        parentId: 'cat_l1_statute',
        level: 2,
        code: 'STATUTE_ADMIN',
        name: '行政法',
        path: '/STATUTE/ADMIN',
        docType: 'STATUTE',
        sortOrder: 3,
        enabled: true,
        children: [
          {
            id: 'cat_l2_statute_admin_general',
            parentId: 'cat_l2_statute_admin',
            level: 3,
            code: 'STATUTE_ADMIN_GENERAL',
            name: '行政法',
            path: '/STATUTE/ADMIN/GENERAL',
            docType: 'STATUTE',
            sortOrder: 0,
            enabled: true,
            children: [],
          },
        ],
      },
    ],
  },
];

describe('buildL3UploadOptions', () => {
  it('builds breadcrumb labels for L3 nodes', () => {
    const options = buildL3UploadOptions(sampleTree);
    expect(options).toEqual(
      expect.arrayContaining([
        { value: 'cat_l2_statute_admin_general', label: '法规 / 行政法 / 行政法' },
        { value: 'cat_l3_statute_civil_labor', label: '法规 / 民法商法 / 劳动合同法规' },
      ]),
    );
    expect(options).toHaveLength(2);
  });
});

describe('filterL3BySelectedPath', () => {
  const l3 = [
    { path: '/STATUTE/CIVIL/LABOR' },
    { path: '/STATUTE/ADMIN/GENERAL' },
  ] as CategoryNode[];

  it('returns all nodes when no path selected', () => {
    expect(filterL3BySelectedPath(l3, null)).toHaveLength(2);
  });

  it('filters nodes under selected L2 path', () => {
    const filtered = filterL3BySelectedPath(l3, '/STATUTE/ADMIN');
    expect(filtered).toHaveLength(1);
    expect(filtered[0].path).toBe('/STATUTE/ADMIN/GENERAL');
  });
});

describe('resolveUploadCategoryId', () => {
  const l3 = [
    { id: 'cat_l3_statute_civil_labor', path: '/STATUTE/CIVIL/LABOR' },
    { id: 'cat_l2_statute_admin_general', path: '/STATUTE/ADMIN/GENERAL' },
  ] as CategoryNode[];

  it('prefers selected L3 node', () => {
    expect(resolveUploadCategoryId(sampleTree, '/STATUTE/CIVIL/LABOR', l3)).toBe('cat_l3_statute_civil_labor');
  });

  it('selects first L3 under selected L2', () => {
    expect(resolveUploadCategoryId(sampleTree, '/STATUTE/ADMIN', l3)).toBe('cat_l2_statute_admin_general');
  });
});

describe('cascade category helpers', () => {
  it('lists L1/L2/L3 nodes', () => {
    const l1 = listL1Nodes(sampleTree);
    expect(l1).toHaveLength(1);
    expect(l1[0].name).toBe('法规');

    const l2 = listL2Nodes(l1[0]);
    expect(l2.map((node) => node.name)).toEqual(['民法商法', '行政法']);

    const l3 = listL3Nodes(l2[0]);
    expect(l3).toHaveLength(1);
    expect(l3[0].name).toBe('劳动合同法规');
  });

  it('builds select options from nodes', () => {
    const l1 = listL1Nodes(sampleTree);
    expect(toCategoryOptions(l1)).toEqual([{ value: 'cat_l1_statute', label: '法规' }]);
  });

  it('resolves valid L1/L2/L3 path to category id', () => {
    expect(resolveCategoryPath(
      sampleTree,
      'cat_l1_statute',
      'cat_l2_statute_civil',
      'cat_l3_statute_civil_labor',
    )).toBe('cat_l3_statute_civil_labor');
    expect(resolveCategoryPath(sampleTree, 'cat_l1_statute', 'cat_l2_statute_civil', '')).toBe('');
  });

  it('finds selection for category id', () => {
    expect(findSelectionForCategoryId(sampleTree, 'cat_l3_statute_civil_labor')).toEqual({
      l1Id: 'cat_l1_statute',
      l2Id: 'cat_l2_statute_civil',
      l3Id: 'cat_l3_statute_civil_labor',
    });
  });

  it('finds partial selection for L2 path', () => {
    expect(findSelectionForPath(sampleTree, '/STATUTE/ADMIN')).toEqual({
      l1Id: 'cat_l1_statute',
      l2Id: 'cat_l2_statute_admin',
    });
  });

  it('finds full selection for L3 path', () => {
    expect(findSelectionForPath(sampleTree, '/STATUTE/CIVIL/LABOR')).toEqual({
      l1Id: 'cat_l1_statute',
      l2Id: 'cat_l2_statute_civil',
      l3Id: 'cat_l3_statute_civil_labor',
    });
  });
});
