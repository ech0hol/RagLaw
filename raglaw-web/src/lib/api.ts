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

const TOKEN_KEY = 'raglaw_access_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
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
  const res = await fetch(path, { ...init, headers, credentials: 'include' });
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
