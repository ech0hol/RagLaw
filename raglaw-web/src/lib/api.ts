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

export function getToken(): string | null {
  return memoryToken;
}

export function setToken(token: string) {
  memoryToken = token;
}

export function clearToken() {
  memoryToken = null;
}

export async function refreshAccessToken(): Promise<boolean> {
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

async function parseJsonResponse<T>(res: Response): Promise<ApiResponse<T>> {
  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    return {
      success: false,
      data: null as T,
      error: { code: 'HTTP_ERROR', message: `请求失败 (${res.status})` },
    };
  }
  return res.json();
}

export async function api<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Content-Type') && init?.body) {
    headers.set('Content-Type', 'application/json');
  }
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  let res = await fetch(path, { ...init, headers, credentials: 'include' });
  if (res.status === 401 && path !== '/api/v1/auth/refresh' && path !== '/api/v1/auth/login') {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      headers.set('Authorization', `Bearer ${getToken()}`);
      res = await fetch(path, { ...init, headers, credentials: 'include' });
    }
  }
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

export async function uploadDocument(categoryId: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  form.append('categoryId', categoryId);
  const token = getToken();
  const headers: HeadersInit = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch('/api/v1/admin/documents/upload', {
    method: 'POST',
    headers,
    body: form,
    credentials: 'include',
  });
  return parseJsonResponse<{
    id: string;
    title: string;
    status: string;
    categoryId: string;
  }>(res);
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

export async function fetchKnowledgePreviewUrl(documentId: string): Promise<string | null> {
  const token = getToken();
  const headers: HeadersInit = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(`/api/v1/knowledge/documents/${documentId}/preview`, {
    headers,
    credentials: 'include',
  });
  if (!res.ok) {
    return null;
  }
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

export async function fetchContractFileUrl(documentId: string): Promise<string | null> {
  const token = getToken();
  const headers: HeadersInit = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(`/api/v1/contracts/${documentId}/file`, {
    headers,
    credentials: 'include',
  });
  if (!res.ok) {
    return null;
  }
  const blob = await res.blob();
  return URL.createObjectURL(blob);
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

export async function fetchKnowledgeStats() {
  return api<KnowledgeStats>('/api/v1/knowledge/stats');
}

async function downloadAuthedFile(path: string, filename: string) {
  const token = getToken();
  const headers: HeadersInit = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(path, {
    headers,
    credentials: 'include',
  });
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
