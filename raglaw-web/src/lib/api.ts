export type User = {
  id: string;
  email: string;
  displayName: string;
  role: 'ADMIN' | 'LAWYER';
};

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  error?: { code: string; message: string };
};

let memoryToken: string | null = null;

const TOKEN_STORAGE_KEY = 'raglaw_access_token';
const AUTH_EXEMPT_PATHS = new Set(['/api/v1/auth/refresh', '/api/v1/auth/login']);
const TOKEN_STALE_BUFFER_SEC = 5 * 60;

let refreshInFlight: Promise<boolean> | null = null;

const UNAUTHORIZED_RESPONSE = <T>(): ApiResponse<T> => ({
  success: false,
  data: null as T,
  error: { code: 'UNAUTHORIZED', message: '登录已过期，请重新登录' },
});

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  if (memoryToken) {
    return memoryToken;
  }
  const stored = readStoredToken();
  if (stored) {
    memoryToken = stored;
  }
  return memoryToken;
}

export function setToken(token: string) {
  memoryToken = token;
  try {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
  } catch {
    // sessionStorage unavailable
  }
}

export function clearToken() {
  memoryToken = null;
  try {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    // sessionStorage unavailable
  }
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function isAccessTokenStale(token: string | null = getToken()): boolean {
  if (!token) {
    return true;
  }
  const payload = decodeJwtPayload(token);
  const exp = payload?.exp;
  if (typeof exp !== 'number') {
    return false;
  }
  const nowSec = Math.floor(Date.now() / 1000);
  return exp <= nowSec + TOKEN_STALE_BUFFER_SEC;
}

async function refreshAccessTokenOnce(): Promise<boolean> {
  const res = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) {
    return false;
  }
  const body = (await res.json()) as ApiResponse<{ accessToken: string }>;
  if (body.success && body.data?.accessToken) {
    setToken(body.data.accessToken);
    return true;
  }
  return false;
}

export async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = refreshAccessTokenOnce().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function ensureFreshAccessToken(): Promise<boolean> {
  if (!getToken() || isAccessTokenStale()) {
    return refreshAccessToken();
  }
  return true;
}

async function parseJsonResponse<T>(res: Response): Promise<ApiResponse<T>> {
  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    if (res.status === 401 || res.status === 403) {
      return UNAUTHORIZED_RESPONSE<T>();
    }
    return {
      success: false,
      data: null as T,
      error: { code: 'HTTP_ERROR', message: `请求失败 (${res.status})` },
    };
  }
  const body = (await res.json()) as ApiResponse<T>;
  if (!body.success && (res.status === 401 || res.status === 403) && body.error?.code !== 'UNAUTHORIZED') {
    return {
      ...body,
      error: { code: 'UNAUTHORIZED', message: body.error?.message ?? '未登录' },
    };
  }
  return body;
}

export async function fetchWithAuth(
  path: string,
  init?: RequestInit,
  retryBody?: () => BodyInit | null | undefined,
): Promise<Response> {
  if (!getToken() || isAccessTokenStale()) {
    await refreshAccessToken();
  }

  async function doFetch(bodyOverride?: BodyInit | null | undefined): Promise<Response> {
    const headers = new Headers(init?.headers);
    const token = getToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    const body = bodyOverride !== undefined ? bodyOverride : init?.body;
    return fetch(path, { ...init, headers, body, credentials: 'include' });
  }

  let res = await doFetch();
  if (shouldRetryAuth(path, res.status)) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      const rebuiltBody = retryBody ? retryBody() : undefined;
      res = await doFetch(rebuiltBody !== undefined ? rebuiltBody : undefined);
    }
  }
  return res;
}

function shouldRetryAuth(path: string, status: number) {
  return (status === 401 || status === 403) && !AUTH_EXEMPT_PATHS.has(path);
}

export async function callWithAuthRetry<T>(
  request: () => Promise<ApiResponse<T>>,
): Promise<ApiResponse<T>> {
  let res = await request();
  if (res.success || res.error?.code !== 'UNAUTHORIZED') {
    return res;
  }
  const refreshed = await refreshAccessToken();
  if (refreshed) {
    res = await request();
  }
  if (!res.success && res.error?.code === 'UNAUTHORIZED') {
    return UNAUTHORIZED_RESPONSE<T>();
  }
  return res;
}

/** Proactively refresh before long-running authenticated requests, then retry on 401. */
export async function runLongAuthRequest<T>(
  request: () => Promise<ApiResponse<T>>,
): Promise<ApiResponse<T>> {
  const refreshed = await ensureFreshAccessToken();
  if (!refreshed || !getToken()) {
    return UNAUTHORIZED_RESPONSE<T>();
  }
  return callWithAuthRetry(request);
}

export async function ensureSession(): Promise<boolean> {
  const refreshed = await ensureFreshAccessToken();
  if (!refreshed || !getToken()) {
    return false;
  }
  const res = await fetchMe();
  return res.success;
}

export async function api<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Content-Type') && init?.body) {
    headers.set('Content-Type', 'application/json');
  }
  const body = init?.body;
  const res = await fetchWithAuth(
    path,
    { ...init, headers, cache: 'no-store' },
    body != null ? () => body : undefined,
  );
  return parseJsonResponse<T>(res);
}

export async function login(email: string, password: string) {
  const body = await api<{ accessToken: string; user: User }>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  if (body.success) {
    setToken(body.data.accessToken);
  }
  return body;
}

export async function fetchMe() {
  return api<User>('/api/v1/auth/me');
}

export async function logout() {
  await api<void>('/api/v1/auth/logout', { method: 'POST' });
  clearToken();
}

type UploadedDocument = {
  id: string;
  title: string;
  status: string;
  docType: string;
  categoryId: string;
  ingestStage?: string | null;
  ingestError?: string | null;
};

export async function uploadDocumentsBatch(categoryId: string, files: File[]) {
  const buildForm = () => {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file);
    }
    form.append('categoryId', categoryId);
    return form;
  };
  const res = await fetchWithAuth(
    '/api/v1/admin/documents/upload-batch',
    { method: 'POST', body: buildForm() },
    buildForm,
  );
  return parseJsonResponse<{ items: UploadedDocument[]; ingestMode: string }>(res);
}

export async function uploadContractsBatch(files: File[], categoryId?: string) {
  const buildForm = () => {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file);
    }
    if (categoryId) {
      form.append('categoryId', categoryId);
    }
    return form;
  };
  const res = await fetchWithAuth(
    '/api/v1/contracts/upload-batch',
    { method: 'POST', body: buildForm() },
    buildForm,
  );
  return parseJsonResponse<{ items: UploadedDocument[]; ingestMode: string }>(res);
}

export async function uploadDocument(categoryId: string, file: File) {
  const buildForm = () => {
    const form = new FormData();
    form.append('file', file);
    form.append('categoryId', categoryId);
    return form;
  };
  const res = await fetchWithAuth(
    '/api/v1/admin/documents/upload',
    { method: 'POST', body: buildForm() },
    buildForm,
  );
  return parseJsonResponse<UploadedDocument>(res);
}

export async function uploadContract(file: File, categoryId?: string) {
  const buildForm = () => {
    const form = new FormData();
    form.append('file', file);
    if (categoryId) {
      form.append('categoryId', categoryId);
    }
    return form;
  };
  const res = await fetchWithAuth(
    '/api/v1/contracts/upload',
    { method: 'POST', body: buildForm() },
    buildForm,
  );
  return parseJsonResponse<UploadedDocument>(res);
}

export async function ingestDocument(documentId: string) {
  return api<{ id: string; title: string; status: string }>(
    `/api/v1/admin/documents/${documentId}/ingest`,
    { method: 'POST' },
  );
}

export async function downloadKnowledgeDocument(documentId: string, filename: string) {
  await downloadAuthedFile(
    `/api/v1/knowledge/documents/${documentId}/download`,
    filename,
  );
}

export async function downloadContractExport(documentId: string, format: 'docx' | 'pdf') {
  await downloadAuthedFile(
    `/api/v1/contracts/${documentId}/export?format=${format}`,
    `contract-revised.${format}`,
  );
}

export type AuthedBlobResult = {
  url: string;
  mimeType: string;
};

async function createBlobUrlFromResponse(res: Response, mimeType?: string): Promise<AuthedBlobResult | null> {
  if (!res.ok) {
    return null;
  }
  const contentType = res.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return null;
  }
  const buffer = await res.arrayBuffer();
  if (buffer.byteLength === 0) {
    return null;
  }
  const type = mimeType ?? contentType.split(';')[0]?.trim() ?? 'application/octet-stream';
  const blob = new Blob([buffer], { type });
  return { url: URL.createObjectURL(blob), mimeType: type };
}

export async function fetchAuthedBlobUrl(path: string, mimeType?: string): Promise<AuthedBlobResult | null> {
  const res = await fetchWithAuth(path);
  return createBlobUrlFromResponse(res, mimeType);
}

export async function fetchKnowledgePreviewUrl(documentId: string): Promise<string | null> {
  const result = await fetchAuthedBlobUrl(`/api/v1/knowledge/documents/${documentId}/preview`);
  return result?.url ?? null;
}

export async function fetchContractFileUrl(documentId: string, mimeType?: string): Promise<string | null> {
  const result = await fetchAuthedBlobUrl(
    `/api/v1/contracts/${documentId}/file`,
    mimeType ?? 'application/pdf',
  );
  return result?.url ?? null;
}

export async function downloadContractOriginal(documentId: string, filename: string) {
  await downloadAuthedFile(`/api/v1/contracts/${documentId}/file`, filename);
}

export async function deleteConversation(conversationId: string) {
  return api<void>(`/api/v1/conversations/${conversationId}`, { method: 'DELETE' });
}

export type ContractSummary = {
  documentId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  riskCount: number;
  suggestedAgentCode: string;
};

export type LegalReference = {
  documentId?: string | null;
  title: string;
  excerpt?: string | null;
  docType: string;
};

export type ContractRisk = {
  id: string;
  documentId: string;
  chunkId: string;
  severity: string;
  dimension: string;
  summary: string;
  excerpt: string;
  suggestion: string;
  pageNumber?: number | null;
  highlightRects?: Array<{
    page: number;
    x: number;
    y: number;
    width: number;
    height: number;
  }>;
  accepted: boolean;
  revisedExcerpt?: string | null;
  legalReferences?: LegalReference[];
  createdAt?: string;
};

export type ContractReview = {
  documentId: string;
  suggestedAgentCode: string;
  extractMethod: string;
  ocrUsed: boolean;
  analysisModel?: string | null;
  ragHitCount?: number;
  reviewStatus?: string | null;
  reviewError?: string | null;
  ingestStage?: string | null;
  risks: ContractRisk[];
};

export async function fetchContractReview(documentId: string) {
  return api<ContractReview>(`/api/v1/contracts/${documentId}/review`);
}

export async function rerunContractReview(documentId: string) {
  return runLongAuthRequest(() =>
    api<ContractReview>(`/api/v1/contracts/${documentId}/review`, { method: 'POST' }),
  );
}

export async function parseContractReview(documentId: string) {
  return runLongAuthRequest(() =>
    api<ContractReview>(`/api/v1/contracts/${documentId}/parse-review`, { method: 'POST' }),
  );
}

export async function ingestContractReview(documentId: string) {
  return runLongAuthRequest(() =>
    api<ContractReview>(`/api/v1/contracts/${documentId}/ingest-review`, { method: 'POST' }),
  );
}

export async function fetchContractText(documentId: string) {
  return api<{
    documentId: string;
    filename: string;
    content: string;
    pdf: boolean;
    image?: boolean;
    extractMethod?: string | null;
    ocrUsed?: boolean;
    chunks?: Array<{
      id: string;
      chunkIndex: number;
      content: string;
      chunkLevel?: string | null;
    }>;
  }>(`/api/v1/contracts/${documentId}/text`);
}

export async function acceptContractRisk(documentId: string, riskId: string) {
  return api<void>(`/api/v1/contracts/${documentId}/risks/${riskId}/accept`, { method: 'POST' });
}

export async function unacceptContractRisk(documentId: string, riskId: string) {
  return api<void>(`/api/v1/contracts/${documentId}/risks/${riskId}/unaccept`, { method: 'POST' });
}

export async function acceptAllContractRevisions(documentId: string) {
  return api<Awaited<ReturnType<typeof fetchContractText>>['data']>(
    `/api/v1/contracts/${documentId}/accept-revisions`,
    { method: 'POST' },
  );
}

export async function fetchContracts() {
  return api<ContractSummary[]>('/api/v1/contracts');
}

export async function deleteContract(documentId: string) {
  return api<void>(`/api/v1/contracts/${documentId}`, { method: 'DELETE' });
}

export type KnowledgeStats = {
  caseCount: number;
  statuteCount: number;
};

export type AdminDocumentRow = {
  id: string;
  title: string;
  status: string;
  docType: string;
  categoryId: string;
  ingestStage?: string | null;
  ingestError?: string | null;
  createdAt?: string | null;
  effectiveDate?: string | null;
};

export type AdminDocumentListPage = {
  items: AdminDocumentRow[];
  page: number;
  pageSize: number;
  total: number;
};

export type CategoryDocumentCount = {
  categoryId: string;
  path: string;
  count: number;
};

export async function fetchKnowledgeStats() {
  return api<KnowledgeStats>('/api/v1/knowledge/stats');
}

export async function fetchAdminDocuments(options: {
  categoryPath?: string;
  page?: number;
  pageSize?: number;
  docType?: string;
}) {
  const params = new URLSearchParams();
  if (options.categoryPath) {
    params.set('categoryPath', options.categoryPath);
  }
  if (options.page != null) {
    params.set('page', String(options.page));
  }
  if (options.pageSize != null) {
    params.set('pageSize', String(options.pageSize));
  }
  if (options.docType) {
    params.set('docType', options.docType);
  }
  const query = params.toString();
  return api<AdminDocumentListPage>(`/api/v1/admin/documents${query ? `?${query}` : ''}`);
}

export async function fetchAdminDocumentCategoryCounts() {
  return api<CategoryDocumentCount[]>('/api/v1/admin/documents/category-counts');
}

export async function deleteAdminDocument(documentId: string) {
  return api<void>(`/api/v1/admin/documents/${documentId}`, { method: 'DELETE' });
}

export type DocumentReindexBatchResult = {
  total: number;
  succeeded: number;
  failed: number;
  failures: { documentId: string; message: string }[];
};

export async function reindexAdminDocumentsSelected(documentIds: string[]) {
  return api<DocumentReindexBatchResult>('/api/v1/admin/documents/reindex-selected', {
    method: 'POST',
    body: JSON.stringify({ documentIds }),
  });
}

export async function updateAdminUser(
  userId: string,
  body: { displayName: string; enabled: boolean; role: 'LAWYER' | 'ADMIN' },
) {
  return api<AdminUserRow>(`/api/v1/admin/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

export async function deleteAdminUser(userId: string) {
  return api<void>(`/api/v1/admin/users/${userId}`, { method: 'DELETE' });
}

export type AdminUserRow = {
  id: string;
  email: string;
  displayName: string;
  role: string;
  enabled: boolean;
  createdAt: string;
};

export async function clearKnowledgeDocuments(docTypes = 'STATUTE,CASE') {
  return api<{ deleted: number }>(`/api/v1/admin/documents?docType=${encodeURIComponent(docTypes)}`, {
    method: 'DELETE',
  });
}

export function deleteAdminTrace(traceId: string) {
  return api<void>(`/api/v1/admin/traces/${traceId}`, { method: 'DELETE' });
}

export function deleteAdminTracesBatch(traceIds: string[]) {
  return api<{ deleted: number }>('/api/v1/admin/traces/batch-delete', {
    method: 'POST',
    body: JSON.stringify({ traceIds }),
  });
}

async function downloadAuthedFile(path: string, filename: string) {
  const res = await fetchWithAuth(path);
  if (!res.ok) {
    throw new Error(`下载失败 (${res.status})`);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
