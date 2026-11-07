import { createApp, h } from "vue";
import "../src/styles.css";
import ResourcesReview from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue";
// Explicit local test identities, not login, credentials, production tokens or roles supplied to a service.
const base = import.meta.env.VITE_REVIEW_E2E_API;
if (typeof base !== "string" || !/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) throw new Error("Loopback fixture required");
const actor = new URL(location.href).searchParams.get("actor") || "";
if (!["editor", "reviewer-a", "reviewer-b", "community"].includes(actor)) throw new Error("Fixture identity required");
async function send(path: string, init: RequestInit = {}, plain = false) {
  const headers = new Headers(init.headers); headers.set("X-Review-Fixture-Principal", actor);
  const response = await fetch(`${base}/${path}`, { ...init, headers });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return plain ? response.text() : response.json();
}
const context = { auth: { authenticated: true, username: actor }, api: { get: (path: string) => send(path), request: (path: string, init: RequestInit) => send(path, init),
  text: (path: string) => send(path, {}, true) } };
createApp({ render: () => h(ResourcesReview, { context: context as never }) }).mount("#app");
