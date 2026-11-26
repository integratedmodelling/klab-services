import { accessToken, invalidateServiceAuthentication } from "./auth";

interface ServiceRequestInit extends RequestInit { includeProblemDetail?: boolean }

export interface ServiceApi {
  get<T = Record<string, unknown>>(path: string, authenticated?: boolean): Promise<T>;
  request<T = unknown>(path: string, init?: ServiceRequestInit): Promise<T>;
  text?(path: string, init?: RequestInit): Promise<string>;
}

async function send(path: string, init: ServiceRequestInit = {},
                    authenticated = !path.startsWith("public/")): Promise<Response> {
  const { includeProblemDetail, ...options } = init;
  const headers = new Headers(options.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  // Public dashboard data remains available even when the hub or sign-in is unavailable.
  const token = authenticated ? await accessToken() : undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...options, headers, credentials: "omit" });
  if (!response.ok) {
    if (token && response.status === 401) invalidateServiceAuthentication();
    let detail = "";
    if (includeProblemDetail && response.status === 400) {
      const problem = await response.json().catch(() => undefined);
      if (typeof problem?.detail === "string") detail = " · " + problem.detail;
    }
    throw new Error(`${response.status} ${response.statusText}${detail}`);
  }
  return response;
}

async function request<T>(path: string, init: ServiceRequestInit = {}, authenticated = !path.startsWith("public/")): Promise<T> {
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
