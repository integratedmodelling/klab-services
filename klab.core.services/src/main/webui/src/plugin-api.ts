// Stable browser module shared with dynamically installed k.LAB component UIs. Component builds
// externalize `vue` and import Quasar composables from `@klab/webui` so they use the host runtime.
export * from "vue";
export { Dialog, Loading, Notify, useQuasar } from "quasar";

export { default as FlowChartViewer } from "./components/FlowChartViewer.vue";
export type { FlowChart, DiagramSelection } from "./flowchart/model";
export { default as SemanticComposer } from "./components/SemanticComposer.vue";
export { default as WorldviewBrowser } from "./components/WorldviewBrowser.vue";
export type { Concept, Declaration, WorldviewCatalog, SemanticResponse, SemanticMatch } from "./knowledge/model";
export type { ServiceApi } from "./services/api";
