import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Card, PageHeader, Spinner } from '@raglaw/ui';
import { ContractTextViewer } from '../components/ContractTextViewer';
import { api, downloadContractExport, fetchContractFileUrl } from '../lib/api';

type ContractRisk = {
  id: string;
  severity: string;
  dimension: string;
  summary: string;
  excerpt: string;
  suggestion: string;
  pageNumber?: number | null;
  accepted?: boolean;
};

type ContractReview = {
  documentId: string;
  suggestedAgentCode: string;
  extractMethod: string;
  ocrUsed: boolean;
  risks: ContractRisk[];
};

type ContractText = {
  documentId: string;
  filename: string;
  content: string;
  pdf: boolean;
};

export function ContractReviewPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const docId = params.get('doc');
  const conversationId = params.get('c');
  const [review, setReview] = useState<ContractReview | null>(null);
  const [text, setText] = useState<ContractText | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [activeRiskId, setActiveRiskId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!docId) {
      setLoading(false);
      setError('缺少合同文档 ID');
      return;
    }
    let objectUrl: string | null = null;
    void Promise.all([
      api<ContractReview>(`/api/v1/contracts/${docId}/review`, { method: 'POST' }),
      api<ContractText>(`/api/v1/contracts/${docId}/text`),
    ]).then(async ([reviewRes, textRes]) => {
      if (!reviewRes.success) {
        setError(reviewRes.error?.message ?? '加载审查结果失败');
      } else {
        setReview(reviewRes.data);
      }
      if (textRes.success) {
        setText(textRes.data);
        if (textRes.data.pdf) {
          objectUrl = await fetchContractFileUrl(docId);
          setPdfUrl(objectUrl);
        }
      }
      setLoading(false);
    });
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [docId]);

  if (loading) return <Spinner />;
  if (error || !review || !text || !docId) return <PageHeader title="合同审查" subtitle={error ?? '未找到审查结果'} />;

  const chatHref = conversationId
    ? `/chat/${review.suggestedAgentCode}?c=${conversationId}`
    : `/chat/${review.suggestedAgentCode}`;
  const activeRisk = review.risks.find((risk) => risk.id === activeRiskId) ?? null;

  return (
    <div className="rl-page-center rl-page-center--wide">
      <PageHeader
        title="合同风险审查"
        subtitle={`提取方式：${review.extractMethod}${review.ocrUsed ? '（含 OCR）' : ''} · 推荐助手：${review.suggestedAgentCode}`}
      />
      <div className="rl-contract-review">
        <Card>
          <div className="rl-admin-form__row">
            <button type="button" className="rl-btn rl-btn--primary" onClick={() => navigate(chatHref)}>
              进入合同对话审查
            </button>
            <button type="button" className="rl-btn" onClick={() => void api(`/api/v1/contracts/${review.documentId}/accept-revisions`, { method: 'POST' }).then(() => window.location.reload())}>
              采纳全部修订建议
            </button>
            <button type="button" className="rl-btn" onClick={() => void downloadContractExport(review.documentId, 'docx')}>
              导出 DOCX
            </button>
            <button type="button" className="rl-btn" onClick={() => void downloadContractExport(review.documentId, 'pdf')}>
              导出 PDF
            </button>
            <Link className="rl-btn" to="/contracts">上传新合同</Link>
          </div>
        </Card>
        <div className="rl-contract-review-layout">
          <Card>
            <h3>合同原文{text.pdf ? '（PDF 预览）' : ''}</h3>
            <ContractTextViewer
              content={text.content}
              pdfUrl={text.pdf ? pdfUrl : null}
              activeRisk={activeRisk}
            />
          </Card>
          <div className="rl-contract-risks">
            <h3>风险清单</h3>
            {review.risks.length === 0 && <Card><p>未识别到规则风险项，可在对话中进一步追问具体条款。</p></Card>}
            {review.risks.map((risk) => (
              <Card
                key={risk.id}
                className={[
                  'rl-risk-card',
                  `rl-risk-card--${risk.severity.toLowerCase()}`,
                  activeRiskId === risk.id && 'rl-risk-card--active',
                ].filter(Boolean).join(' ')}
                onClick={() => setActiveRiskId(risk.id)}
              >
                <div className="rl-risk-card__header">
                  <span className="rl-risk-card__severity">{risk.severity}</span>
                  <span className="rl-risk-card__dimension">{risk.dimension}</span>
                  {risk.pageNumber ? <span className="rl-text-muted">P{risk.pageNumber}</span> : null}
                  {risk.accepted ? <span className="rl-text-muted">已采纳</span> : null}
                </div>
                <h4>{risk.summary}</h4>
                <p className="rl-text-muted">{risk.excerpt}</p>
                <p>{risk.suggestion}</p>
                {!risk.accepted && (
                  <button
                    type="button"
                    className="rl-btn rl-btn--sm"
                    onClick={(event) => {
                      event.stopPropagation();
                      void api(`/api/v1/contracts/${review.documentId}/risks/${risk.id}/accept`, { method: 'POST' })
                        .then(() => setReview((prev) => prev ? {
                          ...prev,
                          risks: prev.risks.map((item) => item.id === risk.id ? { ...item, accepted: true } : item),
                        } : prev));
                    }}
                  >
                    采纳本条
                  </button>
                )}
              </Card>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
