import { accessToken } from "./auth";

export interface ServiceApi {
  get<T = Record<string, unknown>>(path: string, authenticated?: boolean): Promise<T>;
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  const token = await accessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const serviceApi: ServiceApi = {
  get: (path) => request(path),
  request,
};
