export interface ServiceSession {
  token: string;
  username: string;
  expiresAt: number;
}

/** One in-memory credential per dashboard; concurrent API calls share the same exchange. */
export class HubSession {
  private session?: ServiceSession;
  private pending?: Promise<ServiceSession>;
  private generation = 0;

  // Keep the native fetch receiver: calling a stored Window.fetch as this.send() is illegal.
  constructor(private readonly send: typeof fetch = (...args) => globalThis.fetch(...args),
              private readonly now = Date.now) {}

  async get(keycloakToken: string): Promise<ServiceSession> {
    if (this.session && this.session.expiresAt > this.now() + 30_000) return this.session;
    if (this.pending) return this.pending;
    const generation = this.generation;
    const pending = this.exchange(keycloakToken, generation);
    this.pending = pending;
    try {
      return await pending;
    } finally {
      if (this.pending === pending) this.pending = undefined;
    }
  }

  clear(): void {
    this.generation++;
    const token = this.session?.token;
    this.session = undefined;
    this.pending = undefined;
    if (token) this.revoke(token);
  }

  private revoke(token: string): void {
    void this.send("public/ui/authentication", {
      method: "DELETE", headers: { Authorization: `Bearer ${token}` },
      credentials: "omit", cache: "no-store", keepalive: true,
    }).catch(() => { /* Server-side expiry bounds failed logout requests. */ });
  }

  private async exchange(keycloakToken: string, generation: number): Promise<ServiceSession> {
    const response = await this.send("public/ui/authentication", {
      method: "POST", headers: { Authorization: `Bearer ${keycloakToken}` },
      credentials: "omit", cache: "no-store",
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || `Service sign-in failed (${response.status})`);
    }
    const session = await response.json() as ServiceSession;
    if (!session.token?.startsWith("webui_") || !session.username
        || !Number.isFinite(session.expiresAt) || session.expiresAt <= this.now()) {
      throw new Error("The service returned an invalid sign-in response.");
    }
    if (generation !== this.generation) {
      this.revoke(session.token);
      throw new Error("Sign-in was cancelled.");
    }
    const previous = this.session;
    this.session = session;
    if (previous) this.revoke(previous.token);
    return session;
  }
}
