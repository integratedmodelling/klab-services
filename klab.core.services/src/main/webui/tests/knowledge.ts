import { createApp, defineComponent, h, ref } from "vue";
import ReasonerWorkspace from "../../../../../klab.services.reasoner.server/src/main/webui/extensions/ReasonerWorkspace.vue";
import WorldviewBrowser from "../src/components/WorldviewBrowser.vue";
import SemanticComposer from "../src/components/SemanticComposer.vue";
import { fixtureResponse } from "./knowledge-fixtures";
import type { ServiceApi } from "../src/services/api";
import { serviceApi } from "../src/services/api";
import "quasar/dist/quasar.css";
const params = new URLSearchParams(location.search);
document.documentElement.dataset.theme = params.get("theme") || "light";
let declaration = "";
const request: ServiceApi["request"] = async (path, init) => {
  if (params.has("preview")) {
    const result = fixtureResponse(path, init?.body ? JSON.parse(String(init.body).startsWith("{") ? String(init.body) : JSON.stringify(init.body)) : null, declaration);
    if (path.endsWith("semanticSearch")) declaration = result.declaration;
    return result;
  }
  return serviceApi.request(`/${path}`, init);
};
const api: ServiceApi = { request, get: request };
createApp(defineComponent({ setup() {
  const shown = ref(true), showTree = ref(!params.has("graph-only"));
  return () => h("main", { style: "padding: 16px; max-width: 1440px; margin: auto;" }, [
    h("header", { style: "display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px" }, [
      h("h1", { style: "font: 500 24px var(--klab-font); margin:0;" }, "Knowledge explorer"),
      h("button", { onClick: () => { document.documentElement.dataset.theme = document.documentElement.dataset.theme === "light" ? "dark" : "light"; } }, "Change theme"),
    ]),
    params.has("standalone") ? [
      h("button", { onClick: () => { shown.value = !shown.value; } }, "Toggle components"),
      h("button", { onClick: () => { showTree.value = !showTree.value; } }, "Configure tree"),
      shown.value ? h(SemanticComposer, { api }) : null,
      shown.value ? h(WorldviewBrowser, { api, showTree: showTree.value, focus: "earth:Landscape" }) : null,
      params.has("two") ? h(WorldviewBrowser, { api, showTree: false, focus: "earth:Forest" }) : null,
    ] : h(ReasonerWorkspace, { context: { api } as any }),
  ]);
} })).mount("#app");
if (params.has("preview") && params.has("compose")) setTimeout(() => {
  const input = document.querySelector<HTMLInputElement>('.semantic-expression input');
  if (input) { input.value = "Landscape"; input.focus(); input.dispatchEvent(new Event("input", { bubbles: true })); }
}, 1000);
