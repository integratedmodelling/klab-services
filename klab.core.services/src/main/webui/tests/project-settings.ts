import { createApp, h } from "vue";
import { Quasar } from "quasar";
import "quasar/dist/quasar.css";
import "@quasar/extras/material-icons/material-icons.css";
import ResourcesWorkspaces from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesWorkspaces.vue";
import MetadataCard from "../src/components/MetadataCard.vue";
import { serviceApi } from "../src/services/api";
import type { DashboardContext } from "../src/types";

const params = new URLSearchParams(location.search);
document.documentElement.dataset.theme = params.get("theme") ?? "light";
const context = {
  api: { ...serviceApi, get: <T>(path: string) => serviceApi.get<T>("/" + path, false),
    request: <T>(path: string, init?: RequestInit) => serviceApi.request<T>("/" + path, init) },
} as DashboardContext;
const metadata = Object.freeze({ status: "Draft", hidden: { internal: true }, custom: ["A", "B"] });
createApp({ render: () => params.has("standalone")
  ? h(MetadataCard, { metadata, extended: true, options: {
    enumValues: { status: ["Draft", "Decline", "Published"] },
    editHandler: (_key: string, _old: unknown, value: unknown) => value !== "Decline",
    complexValueRenderer: (key: string) => key === "custom" ? h("strong", "Custom value") : null,
  } })
  : h(ResourcesWorkspaces, { context }) }).use(Quasar).mount("#app");
