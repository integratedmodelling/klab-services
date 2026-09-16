import { test, expect } from "@playwright/test";
import { HubSession } from "../src/services/hub-session";

test("coalesces exchanges and returns only the service credential", async () => {
  const calls: RequestInit[] = [];
  const session = new HubSession(async (url, init) => {
    expect(url).toBe("public/ui/authentication");
    calls.push(init!);
    return Response.json({ token: "webui_test", username: "alice", expiresAt: 300_000 });
  }, () => 0);
  const results = await Promise.all([session.get("keycloak"), session.get("keycloak")]);
  expect(calls).toHaveLength(1);
  expect(calls[0].headers).toEqual({ Authorization: "Bearer keycloak" });
  expect(results[0].token).toBe("webui_test");
  await session.get("refreshed-keycloak");
  expect(calls).toHaveLength(1);
});

test("refreshes before expiry and revokes the replaced credential", async () => {
  let now = 0;
  let exchanges = 0;
  const revoked: unknown[] = [];
  const session = new HubSession(async (_, init) => {
    if (init?.method === "DELETE") {
      revoked.push(init.headers);
      return new Response(null, { status: 204 });
    }
    return Response.json({ token: `webui_${++exchanges}`, username: "alice", expiresAt: now + 300_000 });
  }, () => now);
  await session.get("keycloak");
  now = 275_000;
  expect((await session.get("keycloak")).token).toBe("webui_2");
  expect(revoked).toEqual([{ Authorization: "Bearer webui_1" }]);
  session.clear();
  expect(revoked).toHaveLength(2);
});

test("logout cancels an in-flight exchange and revokes its late credential", async () => {
  let complete!: (response: Response) => void;
  const revoked: unknown[] = [];
  const session = new HubSession(async (_, init) => {
    if (init?.method === "DELETE") {
      revoked.push(init.headers);
      return new Response(null, { status: 204 });
    }
    return new Promise<Response>(resolve => { complete = resolve; });
  }, () => 0);
  const pending = session.get("keycloak");
  session.clear();
  complete(Response.json({ token: "webui_late", username: "alice", expiresAt: 300_000 }));
  await expect(pending).rejects.toThrow("cancelled");
  expect(revoked).toEqual([{ Authorization: "Bearer webui_late" }]);
});

test("reports hub and owner rejection and allows retry", async () => {
  let fail = true;
  const session = new HubSession(async () => fail
    ? Response.json({ message: "This local service is restricted to its owner." }, { status: 403 })
    : Response.json({ token: "webui_ok", username: "alice", expiresAt: 300_000 }), () => 0);
  await expect(session.get("keycloak")).rejects.toThrow("restricted to its owner");
  fail = false;
  expect((await session.get("keycloak")).username).toBe("alice");
});

test("rejects malformed or expired service credentials", async () => {
  for (const body of [
    { token: "raw-hub-jwt", username: "alice", expiresAt: 300_000 },
    { token: "webui_expired", username: "alice", expiresAt: 0 },
    { token: "webui_missing", username: "alice" },
  ]) {
    const session = new HubSession(async () => Response.json(body), () => 0);
    await expect(session.get("keycloak")).rejects.toThrow("invalid sign-in response");
  }
});
