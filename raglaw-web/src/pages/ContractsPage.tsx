import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock } from 'lucide-react';
import {
  ConfirmDialog,
  Select,
  SlidePanel,
  Spinner,
  UploadWorkbench,
} from '@raglaw/ui';
import {
  api,
  deleteContract,
  downloadContractExport,
  fetchContracts,
  uploadDocument,
  type ContractSummary,
} from '../lib/api';

const CONTRACT_CATEGORY_ID = 'cat_l3_contract_civil_general';

type ConversationDto = { id: string };
type ContractReview = {
  documentId: string;
  suggestedAgentCode: string;
};

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function ContractsPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [contracts, setContracts] = useState<ContractSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [reviewMode, setReviewMode] = useState('basic');

  const refreshContracts = useCallback(async () => {
    setHistoryLoading(true);
    const res = await fetchContracts();
    if (res.success) {
      setContracts(res.data);
    }
    setHistoryLoading(false);
  }, []);

  useEffect(() => {
    if (historyOpen) {
      void refreshContracts();
    }
  }, [historyOpen, refreshContracts]);

  async function startReview() {
    if (!file) return;
    setUploading(true);
    setError(null);
    const upload = await uploadDocument(CONTRACT_CATEGORY_ID, file);
    if (!upload.success) {
      setError(upload.error?.message ?? '上传失败');
      setUploading(false);
      return;
    }
    const review = await api<ContractReview>(`/api/v1/contracts/${upload.data.id}/ingest-review`, {
      method: 'POST',
    });
    if (!review.success) {
      setError(review.error?.message ?? '审查失败');
      setUploading(false);
      return;
    }
    const conversation = await api<ConversationDto>('/api/v1/conversations', {
      method: 'POST',
      body: JSON.stringify({
        agentCode: review.data.suggestedAgentCode,
        contextDocumentId: upload.data.id,
      }),
    });
    if (!conversation.success) {
      setError(conversation.error?.message ?? '创建会话失败');
      setUploading(false);
      return;
    }
    void refreshContracts();
    navigate(`/contracts/review?doc=${upload.data.id}&c=${conversation.data.id}`);
  }

  async function confirmDeleteContract() {
    if (!deleteTargetId) return;
    const id = deleteTargetId;
    setDeleteLoading(true);
    const res = await deleteContract(id);
    setDeleteLoading(false);
    if (!res.success) return;
    setDeleteTargetId(null);
    void refreshContracts();
  }

  return (
    <div className="rl-chat-page rl-workbench-page">
      <div className="rl-chat-topbar">
        <button
          type="button"
          className={['rl-history-btn', historyOpen && 'rl-history-btn--open'].filter(Boolean).join(' ')}
          onClick={() => setHistoryOpen(true)}
        >
          <Clock size={18} className="rl-history-btn__icon" aria-hidden="true" />
          <span>历史合同</span>
        </button>
      </div>

      <div className="rl-page-center">
        <UploadWorkbench
          title="智能合同"
          subtitle="上传合同文档，自动提取文本、识别风险并进入专项对话"
          accept=".pdf,.doc,.docx,.md,.txt"
          formatHint="支持 doc、docx、pdf、md、txt，文件最大不超过 50M"
          submitLabel="开始审查"
          loading={uploading}
          file={file}
          onFileChange={setFile}
          onSubmit={() => void startReview()}
          error={error}
          footerSlot={
            <Select
              value={reviewMode}
              onChange={setReviewMode}
              options={[{ value: 'basic', label: '基础审查' }]}
            />
          }
        />
      </div>

      <SlidePanel
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        title="历史合同"
        anchor="sidebar"
      >
        {historyLoading ? (
          <Spinner />
        ) : contracts.length === 0 ? (
          <p className="rl-text-muted">暂无历史合同，请上传第一份合同。</p>
        ) : (
          <div className="rl-contract-history-list">
            {contracts.map((item) => (
              <div key={item.documentId} className="rl-contract-history-item">
                <p className="rl-contract-history-item__title">{item.title}</p>
                <p className="rl-contract-history-item__meta">
                  {formatDate(item.createdAt)} · 风险 {item.riskCount} 项 · {item.status}
                </p>
                <div className="rl-contract-history-item__actions">
                  <button
                    type="button"
                    className="rl-btn rl-btn--primary"
                    onClick={() => navigate(`/contracts/review?doc=${item.documentId}`)}
                  >
                    查看详情
                  </button>
                  <button
                    type="button"
                    className="rl-btn"
                    onClick={() => void downloadContractExport(item.documentId, 'docx')}
                  >
                    导出 DOCX
                  </button>
                  <button
                    type="button"
                    className="rl-btn"
                    onClick={() => void downloadContractExport(item.documentId, 'pdf')}
                  >
                    导出 PDF
                  </button>
                  <button
                    type="button"
                    className="rl-btn rl-btn--danger"
                    onClick={() => setDeleteTargetId(item.documentId)}
                  >
                    删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </SlidePanel>

      <ConfirmDialog
        open={deleteTargetId !== null}
        title="删除合同"
        description="将同步删除合同文件、审查记录与索引数据，且无法恢复。是否继续？"
        confirmLabel="删除"
        variant="danger"
        loading={deleteLoading}
        onConfirm={() => void confirmDeleteContract()}
        onCancel={() => !deleteLoading && setDeleteTargetId(null)}
      />
    </div>
  );
}
