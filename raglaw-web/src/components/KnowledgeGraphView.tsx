import { useMemo } from 'react';
import {
  Background,
  Controls,
  ReactFlow,
  type Edge,
  type Node,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

type RelatedDocument = {
  documentId: string;
  title: string;
  docType: string;
  refType: string;
};

type KnowledgeGraphViewProps = {
  documentId: string;
  title: string;
  docType: string;
  relatedDocuments: RelatedDocument[];
};

export function KnowledgeGraphView({
  documentId,
  title,
  docType,
  relatedDocuments,
}: KnowledgeGraphViewProps) {
  const { nodes, edges } = useMemo(() => {
    const center: Node = {
      id: documentId,
      position: { x: 220, y: 120 },
      data: { label: `${title}\n(${docType})` },
      style: {
        background: 'var(--rl-primary-soft)',
        border: '1px solid var(--rl-border)',
        borderRadius: '12px',
        padding: '8px 12px',
        fontSize: '12px',
        width: 160,
        textAlign: 'center',
      },
    };
    const relatedNodes: Node[] = relatedDocuments.map((doc, index) => {
      const angle = (index / Math.max(relatedDocuments.length, 1)) * Math.PI * 2;
      const radius = 150;
      return {
        id: doc.documentId,
        position: {
          x: 220 + Math.cos(angle) * radius,
          y: 120 + Math.sin(angle) * radius,
        },
        data: { label: `${doc.title}\n[${doc.docType}]` },
        style: {
          background: 'var(--rl-surface)',
          border: '1px solid var(--rl-border)',
          borderRadius: '10px',
          padding: '6px 10px',
          fontSize: '11px',
          width: 140,
          textAlign: 'center',
        },
      };
    });
    const relatedEdges: Edge[] = relatedDocuments.map((doc) => ({
      id: `${documentId}-${doc.documentId}`,
      source: documentId,
      target: doc.documentId,
      label: doc.refType,
      style: { stroke: 'var(--rl-text-muted)' },
      labelStyle: { fontSize: 10, fill: 'var(--rl-text-muted)' },
    }));
    return { nodes: [center, ...relatedNodes], edges: relatedEdges };
  }, [documentId, title, docType, relatedDocuments]);

  if (relatedDocuments.length === 0) {
    return null;
  }

  return (
    <div className="rl-knowledge-graph">
      <ReactFlow nodes={nodes} edges={edges} fitView proOptions={{ hideAttribution: true }}>
        <Background gap={16} size={1} color="var(--rl-border)" />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
