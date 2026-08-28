import { readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig, type Plugin } from "vite";

const rootDirectory = path.dirname(fileURLToPath(import.meta.url));
const virtualId = "virtual:klab-dashboard-extensions";
const resolvedVirtualId = `\0${virtualId}`;

function extensionDirectories(): string[] {
  const repositoryRoot = path.resolve(rootDirectory, "../../../..");
  const builtIn = path.resolve(rootDirectory, "src/extensions");
  const serviceDirectories = [
    "klab.services.reasoner.server",
    "klab.services.resources.server",
    "klab.services.resolver.server",
    "klab.services.runtime.server",
  ].map((module) => path.resolve(repositoryRoot, module, "src/main/webui/extensions"));
  const configured = (process.env.KLAB_WEBUI_EXTENSION_DIRS || "")
    .split(path.delimiter)
    .filter(Boolean)
    .map((directory) => path.resolve(directory));
  return [builtIn, ...serviceDirectories, ...configured];
}

function vueFiles(directory: string): string[] {
  try {
    return readdirSync(directory).flatMap((name) => {
      const candidate = path.join(directory, name);
      return statSync(candidate).isDirectory()
        ? vueFiles(candidate)
        : candidate.endsWith(".vue")
          ? [candidate]
          : [];
    });
  } catch {
    return [];
  }
}

function componentId(file: string): string {
  return path
    .basename(file, ".vue")
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .replace(/[_\s]+/g, "-")
    .toLowerCase();
}

function dashboardExtensions(): Plugin {
  return {
    name: "klab-dashboard-extensions",
    resolveId(id) {
      return id === virtualId ? resolvedVirtualId : undefined;
    },
    load(id) {
      if (id !== resolvedVirtualId) return undefined;
      const files = extensionDirectories().flatMap(vueFiles);
      const imports = files.map((file, index) => `import Extension${index} from ${JSON.stringify(file.replace(/\\/g, "/"))};`);
      const registrations = files.map((file, index) => `${JSON.stringify(componentId(file))}: Extension${index}`);
      return `${imports.join("\n")}\nexport default {${registrations.join(",")}};`;
    },
  };
}

export default defineConfig({
  base: "./",
  plugins: [dashboardExtensions(), vue()],
  resolve: {
    alias: { "@klab-dashboard": path.resolve(rootDirectory, "src") },
  },
  build: {
    outDir: path.resolve(rootDirectory, "../../../target/generated-resources/webui/static"),
    emptyOutDir: true,
    sourcemap: true,
  },
});
