import { useEffect, useState } from 'react';
import { Badge, Card, MainHeader } from '@raglaw/ui';
import { api } from '../../lib/api';

type CategoryNode = {
  id: string;
  parentId: string | null;
  level: number;
  code: string;
  name: string;
  path: string;
  docType: string;
  enabled: boolean;
  children: CategoryNode[];
};

const DOC_TYPE_LABELS: Record<string, string> = {
  STATUTE: '法规',
  CASE: '案例',
  CONTRACT: '合同',
};

function CategoryRow({ node, depth = 0 }: { node: CategoryNode; depth?: number }) {
  return (
    <>
      <tr>
        <td style={{ paddingLeft: `${depth * 1.25}rem` }}>{node.name}</td>
        <td><code>{node.code}</code></td>
        <td>{node.level}</td>
        <td><code>{node.path}</code></td>
        <td>{DOC_TYPE_LABELS[node.docType] ?? node.docType}</td>
        <td>
          <Badge variant={node.enabled ? 'default' : 'muted'}>
            {node.enabled ? '启用' : '禁用'}
          </Badge>
        </td>
      </tr>
      {node.children.map((child) => (
        <CategoryRow key={child.id} node={child} depth={depth + 1} />
      ))}
    </>
  );
}

export function CategoriesAdminPage() {
  const [tree, setTree] = useState<CategoryNode[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void api<CategoryNode[]>('/api/v1/admin/categories').then((res) => {
      if (res.success) {
        setTree(res.data);
      } else {
        setError(res.error?.message ?? '加载失败');
      }
    });
  }, []);

  return (
    <div>
      <MainHeader title="类目管理" />
      <p className="rl-muted" style={{ marginBottom: '1.25rem' }}>
        L1/L2/L3 知识库类目树（上传文档需选择 L3 类目）
      </p>
      {error && <p className="rl-form-error">{error}</p>}
      <Card>
        <div className="rl-data-table-wrap">
          <table className="rl-data-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>编码</th>
                <th>层级</th>
                <th>路径</th>
                <th>类型</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {tree.map((node) => (
                <CategoryRow key={node.id} node={node} />
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
