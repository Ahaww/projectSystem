// 后端未就绪时默认使用内置 Mock；在 .env 设置 VITE_USE_MOCK=false 即切换为真实接口
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

const BASE = '/api';

export class ApiError extends Error {
  status: number;
  errors?: any[];
  constructor(status: number, message: string, errors?: any[]) {
    super(message);
    this.status = status;
    this.errors = errors;
  }
}

export async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const token = sessionStorage.getItem('token');
  const res = await fetch(BASE + url, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.message ?? `请求失败（${res.status}）`, data?.errors);
  return data as T;
}

export const get = <T>(url: string) => request<T>('GET', url);
export const post = <T>(url: string, body?: unknown) => request<T>('POST', url, body);
export const put = <T>(url: string, body?: unknown) => request<T>('PUT', url, body);
export const del = (url: string) => request<void>('DELETE', url);
