import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { api, uploadDocument } from '../lib/api';

const CONTRACT_CATEGORY_ID = 'cat_l3_contract_civil_general';

type ConversationDto = { id: string };
type ContractReview = {
  documentId: string;
  suggestedAgentCode: string;
};

export function ContractsPage() {
  const navigate = useNavigate();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onUpload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const fileInput = form.elements.namedItem('contract') as HTMLInputElement;
    const file = fileInput.files?.[0];
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
    navigate(`/contracts/review?doc=${upload.data.id}&c=${conversation.data.id}`);
  }

  return (
    <div>
      <PageHeader
        title="合同审查"
        subtitle="上传合同文档，自动提取文本、识别风险并进入专项对话。"
      />
      <Card>
        <form className="rl-admin-form" onSubmit={onUpload}>
          <label>
            合同文件（PDF / Markdown / 文本）
            <input name="contract" type="file" accept=".pdf,.md,.txt" required />
          </label>
          <button type="submit" className="rl-btn rl-btn--primary" disabled={uploading}>
            {uploading ? '上传审查中…' : '上传并开始审查'}
          </button>
        </form>
        {uploading && <Spinner />}
        {error && <p className="rl-form-error">{error}</p>}
        <p className="rl-text-muted">PDF 优先使用文本层提取；扫描件在配置 API Key 后尝试 OCR。对话检索限定在当前合同内。</p>
      </Card>
      <p className="rl-disclaimer">AI 辅助参考，不构成法律意见。</p>
    </div>
  );
}
