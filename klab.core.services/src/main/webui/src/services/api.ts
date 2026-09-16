import { accessToken, invalidateServiceAuthentication } from "./auth";

export interface ServiceApi {
  get<T = Record<string, unknown>>(path: string, authenticated?: boolean): Promise<T>;
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
}

async function request<T>(path: string, init: RequestInit = {},
                          authenticated = !path.startsWith("public/")): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  // Public dashboard data remains available even when the hub or sign-in is unavailable.
  const token = authenticated ? await accessToken() : undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...init, headers, credentials: "omit" });
  if (!response.ok) {
    if (token && response.status === 401) invalidateServiceAuthentication();
    throw new Error(`${response.status} ${response.statusText}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const serviceApi: ServiceApi = {
  get: (path, authenticated) => request(path, {}, authenticated),
  request,
};
