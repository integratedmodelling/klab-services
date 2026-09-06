// Stable browser module shared with dynamically installed k.LAB component UIs. Component builds
// externalize `vue` and import Quasar composables from `@klab/webui` so they use the host runtime.
export * from "vue";
export { Dialog, Loading, Notify, useQuasar } from "quasar";

export { default as FlowChartViewer } from "./components/FlowChartViewer.vue";
export type { FlowChart, DiagramSelection } from "./flowchart/model";
