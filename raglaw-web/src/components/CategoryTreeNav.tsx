import { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { categorySubtreeCount, type CategoryNode } from '../lib/categories';

type CategoryTreeNavProps = {
  tree: CategoryNode[];
  countById: Map<string, number>;
  selectedPath: string | null;
  totalCount: number;
  onSelect: (path: string | null) => void;
};

function CategoryTreeNodeRow({
  node,
  depth,
  countById,
  selectedPath,
  onSelect,
}: {
  node: CategoryNode;
  depth: number;
  countById: Map<string, number>;
  selectedPath: string | null;
  onSelect: (path: string | null) => void;
}) {
  const hasChildren = node.children.length > 0;
  const branchActive = selectedPath === node.path
    || (selectedPath?.startsWith(node.path + '/') ?? false);
  const [expanded, setExpanded] = useState(branchActive);
  const count = categorySubtreeCount(node, countById);
  const active = selectedPath === node.path;

  useEffect(() => {
    if (branchActive) {
      setExpanded(true);
    }
  }, [branchActive]);

  return (
    <div className="rl-doc-tree__branch">
      <button
        type="button"
        className={['rl-doc-tree__node', active && 'rl-doc-tree__node--active'].filter(Boolean).join(' ')}
        style={{ paddingLeft: `${0.35 + depth * 0.75}rem` }}
        onClick={() => onSelect(node.path)}
      >
        {hasChildren ? (
          <span
            className="rl-doc-tree__toggle"
            onClick={(e) => {
              e.stopPropagation();
              setExpanded((v) => !v);
            }}
            aria-hidden="true"
          >
            {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </span>
        ) : (
          <span className="rl-doc-tree__toggle rl-doc-tree__toggle--leaf" />
        )}
        <span className="rl-doc-tree__label">{node.name}</span>
        <span className="rl-doc-tree__count">({count})</span>
      </button>
      {hasChildren && expanded && (
        <div className="rl-doc-tree__children">
          {node.children.map((child) => (
            <CategoryTreeNodeRow
              key={child.id}
              node={child}
              depth={depth + 1}
              countById={countById}
              selectedPath={selectedPath}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function CategoryTreeNav({
  tree,
  countById,
  selectedPath,
  totalCount,
  onSelect,
}: CategoryTreeNavProps) {
  return (
    <aside className="rl-doc-tree">
      <h2 className="rl-doc-tree__title">文档目录</h2>
      <p className="rl-text-muted rl-doc-tree__hint">括号内为文档篇数</p>
      <button
        type="button"
        className={[
          'rl-doc-tree__node',
          'rl-doc-tree__node--root',
          selectedPath === null && 'rl-doc-tree__node--active',
        ].filter(Boolean).join(' ')}
        onClick={() => onSelect(null)}
      >
        <span className="rl-doc-tree__toggle rl-doc-tree__toggle--leaf" />
        <span className="rl-doc-tree__label">全部文档</span>
        <span className="rl-doc-tree__count">({totalCount})</span>
      </button>
      <div className="rl-doc-tree__list">
        {tree.map((node) => (
          <CategoryTreeNodeRow
            key={node.id}
            node={node}
            depth={0}
            countById={countById}
            selectedPath={selectedPath}
            onSelect={onSelect}
          />
        ))}
      </div>
    </aside>
  );
}
