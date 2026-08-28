import { defineAsyncComponent, type Component } from "vue";
import compiledExtensions from "virtual:klab-dashboard-extensions";
import type { DashboardConfiguration } from "../types";

const runtimeExtensions = new Map<string, Component>();

/** Resolve a built-in component or lazily import a module declared by an installed component. */
export function resolveExtension(
  componentId: string,
  configuration: DashboardConfiguration,
): Component | undefined {
  const moduleUrl = configuration.modules?.[componentId];
  if (!moduleUrl) return compiledExtensions[componentId];

  const absoluteUrl = new URL(moduleUrl, document.baseURI).href;
  let component = runtimeExtensions.get(absoluteUrl);
  if (!component) {
    component = defineAsyncComponent(async () => {
      const loaded = await import(/* @vite-ignore */ absoluteUrl);
      if (!loaded.default) {
        throw new Error(`Web UI module ${componentId} has no default Vue component export`);
      }
      return loaded.default as Component;
    });
    runtimeExtensions.set(absoluteUrl, component);
  }
  return component;
}
