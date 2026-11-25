import type { ServiceApi } from "../services/api";
import type { Diagnostic } from "./model";

export type LanguageStatus = "starting" | "checking" | "connected" | "unavailable";
export interface LanguageEndpoint { api: ServiceApi; url: string }
export interface Buffer { version: number; text: string }
interface Position { line: number; character: number }
interface Problem { level: string; message: string; line: number; column: number; endLine: number; endColumn: number }
export interface Suggestion {
  label: string;
  detail?: string;
  text?: string;
  snippet: boolean;
  range?: { start: Position; end: Position };
}

export class WorkspaceLanguageSession {
  private id?: string;
  private disposed = false;
  private lastSent = 0;
  private timer?: number;
  private polling?: AbortController;

  constructor(
    private endpoint: LanguageEndpoint,
    private buffer: () => Buffer,
    private status: (value: LanguageStatus) => void,
    private diagnostics: (value: Diagnostic[]) => void,
  ) {
    void this.open();
  }

  private path(suffix = "", parameters: Record<string, string> = {}) {
    const [path, query] = this.endpoint.url.split("?", 2);
    const args = new URLSearchParams(query);
    for (const [key, value] of Object.entries(parameters)) args.set(key, value);
    return path + (this.id ? "/" + encodeURIComponent(this.id) : "") + suffix + "?" + args;
  }

  private async open() {
    this.status("starting");
    const initial = this.buffer();
    try {
      const { id } = await this.endpoint.api.request<{ id: string }>(this.path(), request("POST", initial));
      this.id = id;
      if (this.disposed) { this.closeRemote(); return; }
      this.lastSent = initial.version;
      this.changed();
    } catch {
      this.fail();
    }
  }

  changed() {
    if (this.disposed) return;
    this.diagnostics([]);
    window.clearTimeout(this.timer);
    this.polling?.abort();
    if (!this.id) return;
    this.status("checking");
    this.timer = window.setTimeout(() => {
      const controller = new AbortController();
      this.polling = controller;
      void this.check(this.buffer(), controller.signal).catch(() => {
        if (!controller.signal.aborted) this.fail();
      });
    }, 300);
  }

  private async synchronize(buffer: Buffer) {
    if (buffer.version <= this.lastSent) return;
    await this.endpoint.api.request(this.path(), request("PUT", buffer));
    this.lastSent = Math.max(this.lastSent, buffer.version);
  }

  private async check(buffer: Buffer, signal: AbortSignal) {
    await this.synchronize(buffer);
    for (let attempt = 0; attempt < 8; attempt++) {
      if (signal.aborted) return;
      const result = await this.endpoint.api.request<{ version: number; diagnostics: Problem[] } | undefined>(
        this.path(), { signal },
      );
      if (signal.aborted || this.buffer().version !== buffer.version) return;
      if (result?.version === buffer.version) {
        this.diagnostics(asOffsets(result.diagnostics, buffer.text));
        this.status("connected");
        this.timer = window.setTimeout(() => {
          void this.check(buffer, signal).catch(() => { if (!signal.aborted) this.fail(); });
        }, 45000);
        return;
      }
      if (attempt < 7) await new Promise(resolve => window.setTimeout(resolve, 400));
    }
    // Some documents have no Xtext diagnostics to publish. Keep the session for completion.
    if (signal.aborted || this.buffer().version !== buffer.version) return;
    this.status("connected");
    this.timer = window.setTimeout(() => {
      void this.check(buffer, signal).catch(() => { if (!signal.aborted) this.fail(); });
    }, 45000);
  }

  async complete(line: number, column: number, signal: AbortSignal) {
    if (!this.id || this.disposed || signal.aborted) return [];
    const buffer = this.buffer();
    try {
      await this.synchronize(buffer);
      if (signal.aborted || this.disposed || this.buffer().version !== buffer.version) return [];
      const result = await this.endpoint.api.request<Suggestion[]>(this.path("/completion", {
        version: String(buffer.version), line: String(line), column: String(column),
      }), { signal });
      return signal.aborted || this.disposed || this.buffer().version !== buffer.version ? [] : result;
    } catch { return []; }
  }

  private fail() {
    if (!this.disposed) {
      this.status("unavailable");
      this.diagnostics([]);
      this.dispose();
    }
  }

  private closeRemote() {
    if (!this.id) return;
    const path = this.path();
    this.id = undefined;
    void this.endpoint.api.request(path, { method: "DELETE", keepalive: true }).catch(() => {});
  }

  dispose() {
    this.disposed = true;
    window.clearTimeout(this.timer);
    this.polling?.abort();
    this.closeRemote();
  }
}

function request(method: string, value: Buffer): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(value) };
}

function asOffsets(problems: Problem[], text: string): Diagnostic[] {
  const lines = text.split("\n"), starts = [0];
  for (const line of lines) starts.push(starts[starts.length - 1] + line.length + 1);
  const offset = (line: number, column: number) =>
    line >= lines.length ? text.length : starts[line] + Math.min(column, lines[line].length);
  return problems.map(problem => {
    const start = offset(problem.line, problem.column);
    return { level: problem.level, message: problem.message, offset: start, normalizedOffsets: true,
      length: Math.max(0, offset(problem.endLine, problem.endColumn) - start) };
  });
}
