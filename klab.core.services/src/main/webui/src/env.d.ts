/// <reference types="vite/client" />

declare module "virtual:klab-dashboard-extensions" {
  import type { Component } from "vue";
  const components: Record<string, Component>;
  export default components;
}
