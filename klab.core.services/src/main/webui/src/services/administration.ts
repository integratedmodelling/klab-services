import { serviceApi } from "./api";

export interface SettingField {
  name: string;
  page: string;
  description: string;
  kind: "text" | "boolean" | "integer" | "password" | "path" | "action";
  value: string | number | boolean | null;
  configured: boolean;
}

export interface ComponentOption {
  id: string;
  version: string | { major: number; minor: number; build: number; modifier?: string };
  importType: string;
  mavenCoordinates?: string;
}

export function componentVersion(version: ComponentOption["version"]) {
  if (!version || typeof version === "string") return version || "";
  const { major, minor, build, modifier } = version;
  return [major, minor, build].every(Number.isInteger)
    ? `${major}.${minor}.${build}${modifier ? `-${modifier}` : ""}` : "";
}

export function getSettings(signal: AbortSignal) {
  return serviceApi.request<SettingField[]>("settings?view=editor", { signal, cache: "no-store" });
}

export function postJson<T>(path: string, body: unknown, signal: AbortSignal) {
  return serviceApi.request<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
  });
}

export async function changeSetting(field: SettingField, value: unknown, signal: AbortSignal) {
  const path = `set/${encodeURIComponent(field.name)}`;
  const job = typeof value === "string" && field.kind !== "path"
    ? await serviceApi.request<number>(path, {
      method: "POST", headers: { "Content-Type": "text/plain; charset=UTF-8" },
      body: value, signal, cache: "no-store",
    })
    : await postJson<number>(path, value, signal);
  if (!Number.isSafeInteger(job) || job < 0) throw new Error("The service rejected the change.");
  if (job === 0) return;
  await waitForJob(job, signal);
  // Scalar results echo the submitted value, which could be the SMTP password.
  if (field.kind === "action") return retrieveJob<Record<string, unknown>>(job, signal);
}

export function retrieveJob<T>(job: number, signal: AbortSignal) {
  return serviceApi.request<T>(`jobs/retrieve/${job}`, { signal, cache: "no-store" });
}

export async function waitForJob(job: number, signal: AbortSignal) {
  const deadline = Date.now() + 6 * 60 * 1000;
  while (Date.now() < deadline) {
    const { status } = await serviceApi.request<{ status: string }>(`jobs/status/${job}`, {
      signal, cache: "no-store",
    });
    if (status === "FINISHED") return;
    if (status === "ABORTED") throw new Error("The service could not complete the operation.");
    if (status === "INTERRUPTED") throw new Error("The service cancelled the operation.");
    if (status !== "WAITING") throw new Error("The job status is unavailable. Check the service before retrying.");
    await new Promise(resolve => window.setTimeout(resolve, 750));
    signal.throwIfAborted();
  }
  throw new Error("The job is still running. Check the service before retrying.");
}
