import { accessToken, invalidateServiceAuthentication } from "./auth";

export interface ServiceApi {
  get<T = Record<string, unknown>>(path: string, authenticated?: boolean): Promise<T>;
  request<T = unknown>(path: string, init?: RequestInit): Promise<T>;
  text?(path: string, init?: RequestInit): Promise<string>;
}

async function send(path: string, init: RequestInit = {},
                    authenticated = !path.startsWith("public/")): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  // Public dashboard data remains available even when the hub or sign-in is unavailable.
  const token = authenticated ? await accessToken() : undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...init, headers, credentials: "omit" });
  if (!response.ok) {
    if (token && response.status === 401) invalidateServiceAuthentication();
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response;
}

async function request<T>(path: string, init: RequestInit = {}, authenticated = !path.startsWith("public/")): Promise<T> {
  const response = await send(path, init, authenticated);
  // Spring's nullable @ResponseBody methods also return an empty 200, not just 204.
  const body = await response.text();
  if (!body.trim()) return undefined as T;
  try { return JSON.parse(body) as T; }
  catch { throw new Error(`Invalid JSON from ${path} (HTTP ${response.status})`); }
}

export const serviceApi: ServiceApi = {
  get: (path, authenticated) => request(path, {}, authenticated),
  request,
  text: async (path, init = {}) => {
    const headers = new Headers(init.headers); headers.set("Accept", "text/plain");
    return (await send(path, { ...init, headers })).text();
  },
};
