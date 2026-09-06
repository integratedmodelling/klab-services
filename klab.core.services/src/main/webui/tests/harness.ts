import { createApp, defineComponent, h, ref } from "vue";
import FlowChartViewer from "../src/components/FlowChartViewer.vue";
import ResourcesWorkflows from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesWorkflows.vue";
import { Quasar } from "quasar";
import "quasar/dist/quasar.css";
const params = new URLSearchParams(location.search);
const app = createApp(defineComponent({ setup() {
  const url = ref("/chart"), shown = ref(true);
  const request = async (path: string, init = {}) => {
    const response = await fetch(path, init);
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response.json();
  };
  return () => params.has("workflows") ? h(ResourcesWorkflows, { context: { api: { get: request, request } } as any })
    : h("div", [h("button", { onClick: () => { url.value = "/other"; } }, "Change URL"),
      h("button", { onClick: () => { url.value = "/missing"; } }, "Fail URL"),
      h("button", { onClick: () => { shown.value = !shown.value; } }, "Toggle"),
      shown.value ? h(FlowChartViewer, { url: url.value, layout: !params.has("server") }) : null,
      params.has("two") ? h(FlowChartViewer, { url: "/chart", layout: false }) : null]);
}}));
app.use(Quasar); app.mount("#app");
