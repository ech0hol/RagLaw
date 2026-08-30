import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock } from 'lucide-react';
import {
  SlidePanel,
  Spinner,
  UploadWorkbench,
} from '@raglaw/ui';
import {
  deleteContract,
  downloadContractExport,
  ensureSession,
  fetchContracts,
  uploadContractsBatch,
  type ContractSummary,
} from '../lib/api';
import { confirmIrreversibleDelete } from '../lib/confirmDelete';

const CONTRACT_CATEGORY_ID = 'cat_l3_contract_civil_general';

function formatApiError(error: { code: string; message: string } | undefined, fallback: string) {
  if (!error) return fallback;
  return `${error.code}: ${error.message}`;
}

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
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [contracts, setContracts] = useState<ContractSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

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
    if (files.length === 0) return;
    setUploading(true);
    setError(null);
    setMessage(null);
    try {
      if (!await ensureSession()) {
        setError('会话已过期，请从个人主页退出后重新登录');
        return;
      }
      const upload = await uploadContractsBatch(files, CONTRACT_CATEGORY_ID);
      if (!upload.success) {
        setError(`上传合同失败：${formatApiError(upload.error, '上传失败')}`);
        return;
      }
      const uploaded = upload.data.items;
      const firstId = uploaded[0]?.id;
      setFiles([]);
      if (!firstId) {
        setError('上传成功但未返回文档 ID');
        return;
      }
      if (uploaded.length > 1) {
        void refreshContracts();
      }
      navigate(`/contracts/review?doc=${firstId}&auto=1`);
    } catch (err) {
      const errMessage = err instanceof Error ? err.message : '操作失败，请重试';
      setError(errMessage);
    } finally {
      setUploading(false);
    }
  }

  async function removeContract(item: ContractSummary) {
    if (!confirmIrreversibleDelete(item.title)) return;
    setError(null);
    const res = await deleteContract(item.documentId);
    if (!res.success) {
      setError(`删除失败：${formatApiError(res.error, '删除失败')}`);
      return;
    }
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
          title="合同审查"
          subtitle="上传后自动进入审查页，OCR 与 AI 分析将并行进行"
          accept=".pdf,.doc,.docx,.md,.txt,.jpg,.jpeg,.png"
          formatHint="支持 doc、docx、pdf、md、txt、jpg、png，可多选，单文件最大 50M"
          submitLabel={uploading ? '上传中…' : '上传合同'}
          loading={uploading}
          loadingLabel="上传中…"
          multiple
          files={files}
          onFilesChange={setFiles}
          onSubmit={() => void startReview()}
          message={message}
          error={error}
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
                  {item.status === 'PENDING' ? ' · 审查未完成，点击查看继续' : ''}
                </p>
                <div className="rl-contract-history-item__actions">
                  <button
                    type="button"
                    className="rl-btn rl-btn--primary"
                    onClick={() => navigate(
                      `/contracts/review?doc=${item.documentId}${item.status === 'PENDING' ? '&auto=1' : ''}`,
                    )}
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
                    onClick={(e) => {
                      e.stopPropagation();
                      void removeContract(item);
                    }}
                  >
                    删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </SlidePanel>
    </div>
  );
}
