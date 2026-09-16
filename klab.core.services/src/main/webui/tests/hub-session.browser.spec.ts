import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { transpileModule, ScriptTarget, ModuleKind } from "typescript";

test("capabilities can use the negotiated credential while public and signed-out requests stay anonymous", async ({ page }) => {
  const headers: (string | undefined)[] = [];
  await page.route("https://dashboard.test/resources/", route => route.fulfill({
    contentType: "text/html", body: '<html><head><base href="/resources/"></head></html>',
  }));
  await page.route("https://dashboard.test/resources/public/capabilities", route => {
    const credential = route.request().headers().authorization;
    headers.push(credential);
    return route.fulfill({ json: { permissions: credential ? ["READ", "ADMINISTER"] : ["READ"] } });
  });
  await page.goto("https://dashboard.test/resources/");
  const compiled = transpileModule(readFileSync("src/services/api.ts", "utf8"), {
    compilerOptions: { target: ScriptTarget.ES2022, module: ModuleKind.ES2022 },
  }).outputText;
  const permissions = await page.evaluate(async compiled => {
    const authUrl = URL.createObjectURL(new Blob([
      'export async function accessToken() { return "webui_owner"; } export function invalidateServiceAuthentication() {}',
    ], { type: "text/javascript" }));
    const source = compiled.replace('"./auth"', JSON.stringify(authUrl));
    const { serviceApi } = await import(URL.createObjectURL(new Blob([source], { type: "text/javascript" })));
    return [
      (await serviceApi.get("public/capabilities")).permissions,
      (await serviceApi.get("public/capabilities", true)).permissions,
      (await serviceApi.get("public/capabilities", false)).permissions,
    ];
  }, compiled);
  expect(permissions).toEqual([["READ"], ["READ", "ADMINISTER"], ["READ"]]);
  expect(headers).toEqual([undefined, "Bearer webui_owner", undefined]);
});

test("native browser fetch supports exchange, renewal, logout, and retry under the service base", async ({ page }) => {
  const requests: { method: string; authorization?: string }[] = [];
  let exchanges = 0;
  await page.route("https://dashboard.test/resources/ui/workflows", route => route.fulfill({
    contentType: "text/html", body: '<html><head><base href="/resources/"></head><body></body></html>',
  }));
  await page.route("https://dashboard.test/resources/public/ui/authentication", async route => {
    const request = route.request();
    requests.push({ method: request.method(), authorization: request.headers().authorization });
    if (request.method() === "DELETE") {
      await route.fulfill({ status: 204 });
    } else {
      await route.fulfill({ json: {
        token: `webui_${++exchanges}`, username: "alice", expiresAt: Date.now() + 300_000,
      } });
    }
  });
  await page.goto("https://dashboard.test/resources/ui/workflows");
  const source = readFileSync("src/services/hub-session.ts", "utf8");
  const compiled = transpileModule(source, {
    compilerOptions: { target: ScriptTarget.ES2022, module: ModuleKind.ES2022 },
  }).outputText;
  // Run the production class with its default native fetch, not an injected transport mock.
  const result = await page.evaluate(async compiled => {
    const module = await import(URL.createObjectURL(new Blob([compiled], { type: "text/javascript" })));
    let now = Date.now();
    const session = new module.HubSession(undefined, () => now);
    const first = await session.get("keycloak-first");
    now += 275_000;
    const renewed = await session.get("keycloak-renewed");
    session.clear();
    const retried = await session.get("keycloak-retry");
    return [first.token, renewed.token, retried.token];
  }, compiled);
  expect(result).toEqual(["webui_1", "webui_2", "webui_3"]);
  await expect.poll(() => requests.filter(item => item.method === "DELETE").length).toBe(2);
  expect(requests.filter(item => item.method === "POST").map(item => item.authorization))
    .toEqual(["Bearer keycloak-first", "Bearer keycloak-renewed", "Bearer keycloak-retry"]);
  expect(requests.filter(item => item.method === "DELETE").map(item => item.authorization).sort())
    .toEqual(["Bearer webui_1", "Bearer webui_2"]);
});
