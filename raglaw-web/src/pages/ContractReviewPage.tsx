import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, ArrowLeft, ArrowLeftRight, Bot, RotateCcw, Upload } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ConversationPanel, Spinner } from '@raglaw/ui';
import { ContractClauseRiskGroup } from '../components/ContractClauseRiskGroup';
import { ContractDocPanelTopbar, ContractDocumentViewer } from '../components/ContractDocumentViewer';
import { ContractRiskCard } from '../components/ContractRiskCard';
import { fetchDocumentExcerpt, fetchDocumentTitle, useAguiChat } from '../hooks/useAguiChat';
import { useContractReviewPipeline, type ContractText } from '../hooks/useContractReviewPipeline';
import {
  acceptAllContractRevisions,
  acceptContractRisk,
  downloadContractExport,
  fetchContractFileUrl,
  unacceptContractRisk,
  type ContractReview,
  type ContractRisk,
} from '../lib/api';
import { groupRisksByChunk } from '../lib/contractRiskGroups';

function contractFileMime(text: ContractText) {
  if (text.pdf) return 'application/pdf';
  if (text.image) {
    return text.filename.toLowerCase().endsWith('.png') ? 'image/png' : 'image/jpeg';
  }
  return undefined;
}

type PanelTab = 'risks' | 'assistant';
type RiskSubTab = 'clause' | 'grammar';
type RiskListMode = 'flat' | 'clause';

const GRAMMAR_DIMENSION_PATTERN = /文法|逻辑|语法|表述|用语/;
const CONTRACT_QUICK_PROMPTS = [
  '这份合同的主要风险点有哪些？',
  '付款条款是否存在不利约定？',
  '违约责任是否对等？',
];

function isGrammarRisk(risk: ContractRisk) {
  return GRAMMAR_DIMENSION_PATTERN.test(risk.dimension);
}

function filterRisksBySubTab(risks: ContractRisk[], subTab: RiskSubTab) {
  if (subTab === 'grammar') {
    return risks.filter(isGrammarRisk);
  }
  return risks.filter((risk) => !isGrammarRisk(risk));
}

function showAnalysisModel(review: ContractReview) {
  return review.reviewStatus === 'COMPLETED' && review.analysisModel;
}

export function ContractReviewPage() {
  const [params, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const docId = params.get('doc');
  const conversationId = params.get('c');
  const autoPipeline = params.get('auto') === '1';

  const {
    review,
    text,
    initialLoading,
    parseLoading,
    reviewLoading,
    error,
    sessionExpired,
    forceRerun,
    resumePipeline,
    reloadText,
    reloadReview,
  } = useContractReviewPipeline(docId, autoPipeline);

  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [activeRiskId, setActiveRiskId] = useState<string | null>(null);
  const [riskSubTab, setRiskSubTab] = useState<RiskSubTab>('clause');
  const [riskListMode, setRiskListMode] = useState<RiskListMode>('flat');
  const [activeTab, setActiveTab] = useState<PanelTab>('risks');
  const [refreshing, setRefreshing] = useState(false);
  const [acceptLoading, setAcceptLoading] = useState(false);

  const handleConversationIdChange = useCallback((id: string) => {
    const next = new URLSearchParams(params);
    if (docId) next.set('doc', docId);
    if (id) next.set('c', id);
    else next.delete('c');
    setSearchParams(next);
  }, [docId, params, setSearchParams]);

  const assistantChat = useAguiChat({
    agentCode: review?.suggestedAgentCode ?? 'CONTRACT',
    contextDocumentId: docId,
    conversationId,
    onConversationIdChange: handleConversationIdChange,
    useExplicitAgent: true,
  });

  useEffect(() => {
    if (!docId || !text || (!text.pdf && !text.image)) {
      setFileUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      return;
    }

    let cancelled = false;
    let objectUrl: string | null = null;
    const mime = contractFileMime(text);
    void fetchContractFileUrl(docId, mime).then((url) => {
      if (cancelled) {
        if (url) URL.revokeObjectURL(url);
        return;
      }
      objectUrl = url;
      setFileUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return url;
      });
    });

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
      setFileUrl((prev) => {
        if (prev && prev !== objectUrl) {
          URL.revokeObjectURL(prev);
        }
        return null;
      });
    };
  }, [docId, text]);

  useEffect(() => {
    if (!review || review.risks.length === 0) {
      return;
    }
    setActiveRiskId((prev) => prev ?? review.risks[0].id);
  }, [review]);

  const acceptedRisks = useMemo(
    () => review?.risks.filter((risk) => risk.accepted) ?? [],
    [review],
  );

  const filteredRisks = useMemo(() => {
    const risks = review?.risks ?? [];
    return filterRisksBySubTab(risks, riskSubTab);
  }, [review, riskSubTab]);

  const clauseRiskGroups = useMemo(
    () => groupRisksByChunk(filteredRisks, text?.chunks ?? []),
    [filteredRisks, text?.chunks],
  );

  useEffect(() => {
    if (!activeRiskId) return;
    const card = document.getElementById(`risk-card-${activeRiskId}`);
    card?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [activeRiskId, riskListMode, filteredRisks.length]);

  const reviewStance = useMemo(() => {
    if (!text) return '合同双方';
    return text.filename.replace(/\.[^.]+$/, '') || '合同双方';
  }, [text]);

  async function handleRefresh() {
    if (!docId || refreshing) return;
    setRefreshing(true);
    await forceRerun();
    setRefreshing(false);
  }

  async function refreshAfterRevisionChange() {
    if (!docId) return;
    await Promise.all([reloadReview(), reloadText()]);
  }

  async function handleAcceptRisk(riskId: string) {
    if (!docId || acceptLoading) return;
    setAcceptLoading(true);
    const res = await acceptContractRisk(docId, riskId);
    if (res.success) {
      await refreshAfterRevisionChange();
    }
    setAcceptLoading(false);
  }

  async function handleUnacceptRisk(riskId: string) {
    if (!docId || acceptLoading) return;
    setAcceptLoading(true);
    const res = await unacceptContractRisk(docId, riskId);
    if (res.success) {
      await refreshAfterRevisionChange();
    }
    setAcceptLoading(false);
  }

  async function handleAcceptAllRevisions() {
    if (!docId || acceptLoading) return;
    setAcceptLoading(true);
    const res = await acceptAllContractRevisions(docId);
    if (res.success) {
      await Promise.all([reloadReview(), reloadText()]);
    }
    setAcceptLoading(false);
  }

  function emptyRiskMessage() {
    if (!review) return '';
    if (review.reviewStatus === 'SKIPPED_NO_CHUNKS') {
      return '文档尚未完成解析或无可用段落，请重新上传或点击重新审查。';
    }
    if (review.reviewStatus === 'COMPLETED') {
      return riskSubTab === 'grammar'
        ? '审查已完成，未发现文法逻辑类风险。'
        : '审查已完成，未发现条款风险项。可使用 AI 助手追问补充意见。';
    }
    if (riskSubTab === 'grammar') {
      return '暂无文法逻辑类风险项。';
    }
    return '未识别到条款风险项，可点击重新审查或使用 AI 助手追问。';
  }

  if (!docId) {
    return (
      <div className="rl-contract-workbench">
        <header className="rl-contract-workbench__header">
          <button type="button" className="rl-contract-workbench__back" onClick={() => navigate('/contracts')}>
            <ArrowLeft size={18} />
            返回
          </button>
        </header>
        <p className="rl-contract-workbench__error">{error ?? '缺少合同文档 ID'}</p>
      </div>
    );
  }

  const displayReview: ContractReview = review ?? {
    documentId: docId,
    suggestedAgentCode: 'CONTRACT',
    extractMethod: 'text',
    ocrUsed: false,
    risks: [],
  };

  const clauseCount = filterRisksBySubTab(displayReview.risks, 'clause').length;
  const grammarCount = filterRisksBySubTab(displayReview.risks, 'grammar').length;
  const showDocViewer = Boolean(text);
  const defaultDocTab = parseLoading && text && (text.pdf || text.image) ? 'original' as const : 'text' as const;

  return (
    <div className="rl-contract-workbench">
      {error && (
        <div className="rl-contract-workbench__banner rl-contract-workbench__banner--error">
          <span>{error}</span>
          {sessionExpired && review ? (
            <div className="rl-contract-workbench__banner-actions">
              <button type="button" className="rl-btn rl-btn--sm" onClick={() => navigate('/login')}>
                重新登录
              </button>
              <button type="button" className="rl-btn rl-btn--primary rl-btn--sm" onClick={() => void resumePipeline()}>
                续跑审查
              </button>
            </div>
          ) : null}
        </div>
      )}
      <div className="rl-contract-workbench__body">
        <section className="rl-contract-doc-panel">
          {showDocViewer ? (
            <div className="rl-contract-doc-panel__content">
              <ContractDocumentViewer
                content={text!.content}
                filename={text!.filename}
                isPdf={text!.pdf}
                isImage={text!.image ?? false}
                pdfUrl={text!.pdf ? fileUrl : null}
                fileUrl={fileUrl}
                risks={displayReview.risks}
                activeRiskId={activeRiskId}
                acceptedRisks={acceptedRisks}
                ocrUsed={text!.ocrUsed ?? false}
                onRiskSelect={setActiveRiskId}
                onBack={() => navigate('/contracts')}
                defaultDocTab={defaultDocTab}
              />
              {parseLoading && (
                <div className="rl-contract-doc-panel__loading-overlay">
                  <Spinner />
                  <p className="rl-text-muted">OCR 识别与解析中…</p>
                </div>
              )}
            </div>
          ) : (
            <>
              <ContractDocPanelTopbar onBack={() => navigate('/contracts')} />
              <div className="rl-contract-doc-panel__loading">
                <Spinner />
                <p className="rl-text-muted">
                  {initialLoading ? '正在加载合同…' : 'OCR 识别与解析中…'}
                </p>
              </div>
            </>
          )}
        </section>
        <aside className="rl-contract-risk-panel">
          <nav className="rl-contract-panel-modes" aria-label="分析模式">
            <button
              type="button"
              className={['rl-contract-panel-mode', activeTab === 'assistant' && 'rl-contract-panel-mode--active'].filter(Boolean).join(' ')}
              onClick={() => setActiveTab('assistant')}
            >
              <Bot size={16} />
              <span>AI 助手</span>
            </button>
            <button
              type="button"
              className={['rl-contract-panel-mode', activeTab === 'risks' && 'rl-contract-panel-mode--active'].filter(Boolean).join(' ')}
              onClick={() => setActiveTab('risks')}
            >
              <AlertTriangle size={16} />
              <span>风险</span>
            </button>
          </nav>

          <div className="rl-contract-panel-toolbar">
            <span className="rl-contract-panel-toolbar__stance">
              审查立场：<strong>{reviewStance}</strong>
              {showAnalysisModel(displayReview) ? (
                <span className="rl-contract-panel-toolbar__meta">
                  · AI 模型：{displayReview.analysisModel}
                  {typeof displayReview.ragHitCount === 'number' && displayReview.ragHitCount > 0
                    ? ` · 检索 ${displayReview.ragHitCount} 条依据`
                    : ''}
                </span>
              ) : null}
            </span>
            <div className="rl-contract-panel-toolbar__actions">
              {displayReview.risks.some((risk) => !risk.accepted) ? (
                <button
                  type="button"
                  className="rl-btn rl-btn--sm"
                  disabled={acceptLoading || reviewLoading}
                  onClick={() => void handleAcceptAllRevisions()}
                >
                  全部采纳
                </button>
              ) : null}
              <button
                type="button"
                className="rl-btn rl-btn--primary rl-btn--sm"
                onClick={() => void downloadContractExport(displayReview.documentId, 'docx')}
              >
                <Upload size={14} />
                导出
              </button>
              <button
                type="button"
                className="rl-icon-btn"
                title={refreshing ? 'AI 正在重新分析…' : '重新审查'}
                disabled={refreshing || reviewLoading}
                onClick={() => void handleRefresh()}
              >
                <RotateCcw size={16} className={refreshing ? 'rl-icon-btn--spin' : ''} />
              </button>
            </div>
          </div>

          {activeTab === 'risks' ? (
            <>
              <div className="rl-contract-risk-subtabs" role="tablist" aria-label="风险类型">
                <div className="rl-contract-risk-subtabs__left">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={riskSubTab === 'clause'}
                    className={['rl-contract-risk-subtab', riskSubTab === 'clause' && 'rl-contract-risk-subtab--active'].filter(Boolean).join(' ')}
                    onClick={() => setRiskSubTab('clause')}
                  >
                    条款风险
                    <span className="rl-contract-risk-subtab__count">{clauseCount}</span>
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={riskSubTab === 'grammar'}
                    className={['rl-contract-risk-subtab', riskSubTab === 'grammar' && 'rl-contract-risk-subtab--active'].filter(Boolean).join(' ')}
                    onClick={() => setRiskSubTab('grammar')}
                  >
                    文法逻辑
                    <span className="rl-contract-risk-subtab__count">{grammarCount}</span>
                  </button>
                </div>
                <button
                  type="button"
                  className={[
                    'rl-contract-risk-subtabs__perspective',
                    riskListMode === 'clause' && 'rl-contract-risk-subtabs__perspective--active',
                  ].filter(Boolean).join(' ')}
                  aria-pressed={riskListMode === 'clause'}
                  onClick={() => setRiskListMode((mode) => (mode === 'flat' ? 'clause' : 'flat'))}
                >
                  <ArrowLeftRight size={14} />
                  {riskListMode === 'clause' ? '列表视角' : '条款视角'}
                </button>
              </div>
              <div className="rl-contract-risk-panel__list">
                {reviewLoading && displayReview.risks.length === 0 ? (
                  <div className="rl-contract-risk-panel__loading">
                    <Spinner />
                    <p className="rl-text-muted">AI 正在审查合同…</p>
                  </div>
                ) : (
                  <>
                    {filteredRisks.length === 0 && (
                      <p className="rl-text-muted">{emptyRiskMessage()}</p>
                    )}
                    {riskListMode === 'clause'
                      ? clauseRiskGroups.map((group, index) => (
                          <ContractClauseRiskGroup
                            key={group.chunkId}
                            group={group}
                            groupIndex={index}
                            activeRiskId={activeRiskId}
                            onSelectRisk={setActiveRiskId}
                            onAcceptRisk={(riskId) => void handleAcceptRisk(riskId)}
                            onUnacceptRisk={(riskId) => void handleUnacceptRisk(riskId)}
                            acceptLoading={acceptLoading}
                          />
                        ))
                      : filteredRisks.map((risk: ContractRisk) => (
                          <ContractRiskCard
                            key={risk.id}
                            risk={risk}
                            active={activeRiskId === risk.id}
                            onSelect={() => setActiveRiskId(risk.id)}
                            onAccept={(riskId) => void handleAcceptRisk(riskId)}
                            onUnaccept={(riskId) => void handleUnacceptRisk(riskId)}
                            acceptLoading={acceptLoading}
                          />
                        ))}
                  </>
                )}
              </div>
            </>
          ) : (
            <div className="rl-contract-assistant-panel rl-contract-assistant-panel--embedded">
              <ConversationPanel
                compact
                messages={assistantChat.messages}
                input={assistantChat.input}
                streaming={assistantChat.streaming}
                onInputChange={assistantChat.setInput}
                onSubmit={assistantChat.handleSubmit}
                recommendQuestions={assistantChat.recommendQuestions}
                onRecommendClick={(q) => void assistantChat.sendMessage(q)}
                onCopy={(content) => void navigator.clipboard.writeText(content)}
                onRegenerate={(id) => void assistantChat.regenerateAt(id)}
                canRegenerate={assistantChat.canRegenerate}
                messageListRef={assistantChat.messageListRef}
                onMessageListScroll={assistantChat.onMessageListScroll}
                fetchDocumentTitle={fetchDocumentTitle}
                fetchDocumentExcerpt={fetchDocumentExcerpt}
                disclaimer="AI 回答仅供参考，不构成法律意见。"
                welcome={
                  <>
                    <h2 className="rl-contract-assistant-welcome__title">合同 AI 助手</h2>
                    <p className="rl-text-muted">
                      已加载当前合同
                      {displayReview.risks.length > 0 ? `与 ${displayReview.risks.length} 条审查意见` : ''}
                      ，可直接追问风险与修订建议。
                    </p>
                    <div className="rl-contract-assistant-prompts">
                      {CONTRACT_QUICK_PROMPTS.map((prompt) => (
                        <button
                          key={prompt}
                          type="button"
                          className="rl-btn rl-contract-assistant-prompt"
                          onClick={() => void assistantChat.sendMessage(prompt)}
                        >
                          {prompt}
                        </button>
                      ))}
                    </div>
                  </>
                }
              />
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
