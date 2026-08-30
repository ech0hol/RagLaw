import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ensureSession,
  fetchContractReview,
  fetchContractText,
  parseContractReview,
  rerunContractReview,
  type ContractReview,
} from '../lib/api';
import { needsLlmReview, needsParse } from '../lib/contractReviewLoad';
import type { ContractChunk } from '../lib/contractRiskGroups';

const POLL_MS = 2000;
const PARSE_STAGES_IN_PROGRESS = new Set(['PENDING', 'PARSING']);
const PARSED_STAGES = new Set(['PARSED', 'INDEXED']);
export type ContractText = {
  documentId: string;
  filename: string;
  content: string;
  pdf: boolean;
  image?: boolean;
  extractMethod?: string | null;
  ocrUsed?: boolean;
  chunks?: ContractChunk[];
};

function unauthorizedMessage(error: { code: string; message: string } | undefined, fallback: string) {
  if (error?.code === 'UNAUTHORIZED') {
    return '登录已过期，请重新登录';
  }
  return error?.message ?? fallback;
}

function shouldKickoffParse(review: ContractReview) {
  if (!needsParse(review)) return false;
  const stage = review.ingestStage ?? 'PENDING';
  return stage === 'PENDING' || stage === 'FAILED';
}

function isBackgroundParseStage(review: ContractReview) {
  const stage = review.ingestStage ?? 'PENDING';
  return stage === 'PARSING' || stage === 'INDEXING';
}

function isPipelineComplete(review: ContractReview) {
  if (needsParse(review)) return false;
  if (needsLlmReview(review)) return false;
  if (review.reviewStatus === 'RUNNING') return false;
  return true;
}

export function computeParseLoading(
  review: ContractReview | null,
  parsePostInFlight: boolean,
  initialLoading: boolean,
): boolean {
  if (initialLoading) return true;
  if (parsePostInFlight) return true;
  if (!review) return false;
  const stage = review.ingestStage ?? 'PENDING';
  if (needsParse(review)) return true;
  return PARSE_STAGES_IN_PROGRESS.has(stage);
}

export function computeReviewLoading(
  review: ContractReview | null,
  reviewPostInFlight: boolean,
  initialLoading: boolean,
): boolean {
  if (initialLoading) return false;
  if (reviewPostInFlight) return true;
  if (!review) return false;
  if (review.reviewStatus === 'RUNNING') return true;
  return needsLlmReview(review);
}

export function useContractReviewPipeline(docId: string | null, autoPipeline: boolean) {
  const [review, setReview] = useState<ContractReview | null>(null);
  const [text, setText] = useState<ContractText | null>(null);
  const [initialLoading, setInitialLoading] = useState(Boolean(docId));
  const [parsePostInFlight, setParsePostInFlight] = useState(false);
  const [reviewPostInFlight, setReviewPostInFlight] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  const parseKickoffRef = useRef(false);
  const reviewKickoffRef = useRef(false);
  const pipelineActiveRef = useRef(false);

  const loadText = useCallback(async () => {
    if (!docId) return null;
    const textRes = await fetchContractText(docId);
    if (textRes.success) {
      setText(textRes.data);
      return textRes.data;
    }
    return null;
  }, [docId]);

  const applyReview = useCallback((data: ContractReview) => {
    setReview(data);
    if (data.reviewStatus === 'FAILED') {
      setError(data.reviewError ?? '合同审查失败');
    }
  }, []);

  const reloadReview = useCallback(async () => {
    if (!docId) return null;
    const res = await fetchContractReview(docId);
    if (!res.success) {
      setError(unauthorizedMessage(res.error, '加载审查结果失败'));
      setSessionExpired(res.error?.code === 'UNAUTHORIZED');
      return null;
    }
    applyReview(res.data);
    return res.data;
  }, [applyReview, docId]);

  const kickoffParse = useCallback(async () => {
    if (!docId || parseKickoffRef.current) return;
    parseKickoffRef.current = true;
    setParsePostInFlight(true);
    const res = await parseContractReview(docId);
    setParsePostInFlight(false);
    if (!res.success) {
      const message = unauthorizedMessage(res.error, '合同解析失败');
      setError(message);
      setSessionExpired(res.error?.code === 'UNAUTHORIZED');
      parseKickoffRef.current = false;
      return;
    }
    applyReview(res.data);
    setSessionExpired(false);
    await loadText();
  }, [applyReview, docId, loadText]);

  const kickoffReview = useCallback(async () => {
    if (!docId || reviewKickoffRef.current) return;
    reviewKickoffRef.current = true;
    setReviewPostInFlight(true);
    const res = await rerunContractReview(docId);
    setReviewPostInFlight(false);
    if (!res.success) {
      const message = unauthorizedMessage(res.error, '合同 LLM 审查失败');
      setError(message);
      setSessionExpired(res.error?.code === 'UNAUTHORIZED');
      reviewKickoffRef.current = false;
      return;
    }
    applyReview(res.data);
    setSessionExpired(false);
    if (res.data.reviewStatus !== 'FAILED') {
      setError(null);
    }
    await loadText();
  }, [applyReview, docId, loadText]);

  const pollOnce = useCallback(async () => {
    if (!docId) return null;
    const res = await fetchContractReview(docId);
    if (!res.success) {
      setError(unauthorizedMessage(res.error, '加载审查结果失败'));
      setSessionExpired(res.error?.code === 'UNAUTHORIZED');
      return null;
    }
    applyReview(res.data);
    const stage = res.data.ingestStage ?? 'PENDING';
    if (PARSED_STAGES.has(stage)) {
      await loadText();
    }
    return res.data;
  }, [applyReview, docId, loadText]);

  const maybeStartPipelineSteps = useCallback((current: ContractReview) => {
    if (!autoPipeline || pipelineActiveRef.current === false) return;

    if (shouldKickoffParse(current)) {
      void kickoffParse();
      return;
    }

    if (isBackgroundParseStage(current)) {
      return;
    }

    if (needsLlmReview(current) && !reviewKickoffRef.current) {
      void kickoffReview();
    }
  }, [autoPipeline, kickoffParse, kickoffReview]);

  useEffect(() => {
    if (!docId) {
      setInitialLoading(false);
      setError('缺少合同文档 ID');
      return;
    }

    let cancelled = false;
    parseKickoffRef.current = false;
    reviewKickoffRef.current = false;
    pipelineActiveRef.current = autoPipeline;
    setInitialLoading(true);
    setError(null);
    setReview(null);
    setText(null);

    void (async () => {
      const [reviewRes] = await Promise.all([
        fetchContractReview(docId),
        loadText(),
      ]);
      if (cancelled) return;

      if (!reviewRes.success) {
        setError(unauthorizedMessage(reviewRes.error, '加载审查结果失败'));
        setSessionExpired(reviewRes.error?.code === 'UNAUTHORIZED');
        setInitialLoading(false);
        return;
      }

      applyReview(reviewRes.data);
      setInitialLoading(false);

      if (autoPipeline && (needsParse(reviewRes.data) || needsLlmReview(reviewRes.data))) {
        const sessionOk = await ensureSession();
        if (cancelled) return;
        if (!sessionOk) {
          setError('登录已过期，请重新登录');
          setSessionExpired(true);
          return;
        }
        setSessionExpired(false);
        maybeStartPipelineSteps(reviewRes.data);
      }
    })();

    return () => {
      cancelled = true;
      pipelineActiveRef.current = false;
    };
  }, [applyReview, autoPipeline, docId, loadText, maybeStartPipelineSteps]);

  useEffect(() => {
    if (!docId || !autoPipeline || initialLoading) return;

    let cancelled = false;
    let timer: number | null = null;

    const tick = async () => {
      const current = await pollOnce();
      if (cancelled || !current) return;

      if (isPipelineComplete(current)) {
        if (timer !== null) {
          window.clearInterval(timer);
          timer = null;
        }
        if (current.reviewStatus === 'FAILED') {
          setError(current.reviewError ?? '合同审查失败');
        }
        return;
      }

      maybeStartPipelineSteps(current);
    };

    timer = window.setInterval(() => {
      void tick();
    }, POLL_MS);
    void tick();

    return () => {
      cancelled = true;
      if (timer !== null) {
        window.clearInterval(timer);
      }
    };
  }, [autoPipeline, docId, initialLoading, maybeStartPipelineSteps, pollOnce]);

  const forceRerun = useCallback(async () => {
    if (!docId) return;
    setError(null);
    setSessionExpired(false);
    reviewKickoffRef.current = false;
    pipelineActiveRef.current = true;
    await kickoffReview();
  }, [docId, kickoffReview]);

  const resumePipeline = useCallback(async () => {
    if (!docId || !review) return;
    const sessionOk = await ensureSession();
    if (!sessionOk) {
      setError('登录已过期，请重新登录');
      setSessionExpired(true);
      return;
    }
    setError(null);
    setSessionExpired(false);
    parseKickoffRef.current = false;
    reviewKickoffRef.current = false;
    pipelineActiveRef.current = true;
    maybeStartPipelineSteps(review);
  }, [docId, maybeStartPipelineSteps, review]);

  const parseLoading = computeParseLoading(review, parsePostInFlight, initialLoading);
  const reviewLoading = computeReviewLoading(review, reviewPostInFlight, initialLoading);

  return {
    review,
    text,
    initialLoading,
    parseLoading,
    reviewLoading,
    error,
    sessionExpired,
    setError,
    forceRerun,
    resumePipeline,
    reloadText: loadText,
    reloadReview,
  };
}
