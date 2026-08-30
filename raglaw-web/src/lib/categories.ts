export type CategoryNode = {
  id: string;
  parentId: string | null;
  level: number;
  code: string;
  name: string;
  path: string;
  docType: string;
  sortOrder: number;
  enabled: boolean;
  children: CategoryNode[];
};

export type CategoryOption = {
  value: string;
  label: string;
};

export type CategorySelection = {
  l1Id: string;
  l2Id: string;
  l3Id: string;
};

export function filterKnowledgeCategoryTree(nodes: CategoryNode[]): CategoryNode[] {
  return nodes
    .filter((node) => node.docType !== 'CONTRACT')
    .map((node) => ({
      ...node,
      children: filterKnowledgeCategoryTree(node.children ?? []),
    }));
}

export function flattenEnabledL3(nodes: CategoryNode[]): CategoryNode[] {
  return flattenL3(filterKnowledgeCategoryTree(nodes))
    .filter((node) => node.enabled && node.docType !== 'CONTRACT');
}

export function flattenL3(nodes: CategoryNode[]): CategoryNode[] {
  const result: CategoryNode[] = [];
  for (const node of nodes) {
    if (node.level === 3) {
      result.push(node);
    }
    if (node.children?.length) {
      result.push(...flattenL3(node.children));
    }
  }
  return result;
}

export function findCategoryById(nodes: CategoryNode[], id: string): CategoryNode | undefined {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    if (node.children?.length) {
      const found = findCategoryById(node.children, id);
      if (found) {
        return found;
      }
    }
  }
  return undefined;
}

function buildCategoryBreadcrumb(node: CategoryNode, tree: CategoryNode[]): string {
  const parts: string[] = [node.name];
  let parentId = node.parentId;
  while (parentId) {
    const parent = findCategoryById(tree, parentId);
    if (!parent) {
      break;
    }
    parts.unshift(parent.name);
    parentId = parent.parentId;
  }
  return parts.join(' / ');
}

export function buildL3UploadOptions(tree: CategoryNode[]): CategoryOption[] {
  const knowledgeTree = filterKnowledgeCategoryTree(tree);
  return flattenEnabledL3(tree)
    .map((node) => ({
      value: node.id,
      label: buildCategoryBreadcrumb(node, knowledgeTree),
    }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
}

export function filterL3BySelectedPath(l3Nodes: CategoryNode[], selectedPath: string | null): CategoryNode[] {
  if (!selectedPath) {
    return l3Nodes;
  }
  return l3Nodes.filter(
    (node) => node.path === selectedPath || node.path.startsWith(`${selectedPath}/`),
  );
}

export function resolveUploadCategoryId(
  tree: CategoryNode[],
  selectedPath: string | null,
  scopedL3: CategoryNode[],
): string {
  if (scopedL3.length === 0) {
    return '';
  }
  if (selectedPath) {
    const selected = findCategoryByPath(tree, selectedPath);
    if (selected?.level === 3) {
      return selected.id;
    }
    if (selected && selected.level <= 2) {
      const underBranch = scopedL3.find((node) => node.path.startsWith(`${selected.path}/`));
      if (underBranch) {
        return underBranch.id;
      }
    }
  }
  return scopedL3[0].id;
}

export function categorySubtreeCount(node: CategoryNode, countById: Map<string, number>): number {
  const self = countById.get(node.id) ?? 0;
  if (!node.children?.length) {
    return self;
  }
  return self + node.children.reduce(
    (sum, child) => sum + categorySubtreeCount(child, countById),
    0,
  );
}

export function listL1Nodes(tree: CategoryNode[]): CategoryNode[] {
  return filterKnowledgeCategoryTree(tree).filter((node) => node.enabled);
}

export function listL2Nodes(l1: CategoryNode | undefined): CategoryNode[] {
  if (!l1) {
    return [];
  }
  return (l1.children ?? []).filter((node) => node.enabled);
}

export function listL3Nodes(l2: CategoryNode | undefined): CategoryNode[] {
  if (!l2) {
    return [];
  }
  return (l2.children ?? []).filter((node) => node.enabled);
}

export function toCategoryOptions(nodes: CategoryNode[]): CategoryOption[] {
  return nodes
    .map((node) => ({ value: node.id, label: node.name }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
}

export function resolveCategoryPath(
  tree: CategoryNode[],
  l1Id: string,
  l2Id: string,
  l3Id: string,
): string {
  if (!l1Id || !l2Id || !l3Id) {
    return '';
  }
  const knowledgeTree = filterKnowledgeCategoryTree(tree);
  const l1 = findCategoryById(knowledgeTree, l1Id);
  if (!l1 || l1.level !== 1) {
    return '';
  }
  const l2 = findCategoryById(knowledgeTree, l2Id);
  if (!l2 || l2.level !== 2 || l2.parentId !== l1Id) {
    return '';
  }
  const l3 = findCategoryById(knowledgeTree, l3Id);
  if (!l3 || l3.level !== 3 || l3.parentId !== l2Id || !l3.enabled) {
    return '';
  }
  return l3.id;
}

export function findSelectionForCategoryId(
  tree: CategoryNode[],
  categoryId: string,
): CategorySelection | null {
  const knowledgeTree = filterKnowledgeCategoryTree(tree);
  const l3 = findCategoryById(knowledgeTree, categoryId);
  if (!l3 || l3.level !== 3) {
    return null;
  }
  const l2 = l3.parentId ? findCategoryById(knowledgeTree, l3.parentId) : undefined;
  if (!l2 || l2.level !== 2) {
    return null;
  }
  const l1 = l2.parentId ? findCategoryById(knowledgeTree, l2.parentId) : undefined;
  if (!l1 || l1.level !== 1) {
    return null;
  }
  return { l1Id: l1.id, l2Id: l2.id, l3Id: l3.id };
}

export function findSelectionForPath(
  tree: CategoryNode[],
  path: string | null,
): Partial<CategorySelection> {
  if (!path) {
    return {};
  }
  const knowledgeTree = filterKnowledgeCategoryTree(tree);
  const node = findCategoryByPath(knowledgeTree, path);
  if (!node) {
    return {};
  }
  if (node.level === 3) {
    return findSelectionForCategoryId(tree, node.id) ?? {};
  }
  if (node.level === 2) {
    return { l1Id: node.parentId ?? '', l2Id: node.id };
  }
  if (node.level === 1) {
    return { l1Id: node.id };
  }
  return {};
}

export function findCategoryByPath(nodes: CategoryNode[], path: string): CategoryNode | undefined {
  for (const node of nodes) {
    if (node.path === path) {
      return node;
    }
    if (node.children?.length) {
      const found = findCategoryByPath(node.children, path);
      if (found) {
        return found;
      }
    }
  }
  return undefined;
}
