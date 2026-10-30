import path from "node:path";
import {fileURLToPath} from "node:url";
import vue from "@vitejs/plugin-vue";
import {quasar, transformAssetUrls} from "@quasar/vite-plugin";
import {defineConfig} from "vite";
const root = path.dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  define: {"process.env.NODE_ENV": JSON.stringify("production")},
  plugins: [vue({template: {transformAssetUrls}}), quasar()],
  build: {
    target: "es2020", sourcemap: false, emptyOutDir: true, cssCodeSplit: false,
    outDir: path.resolve(root, "../../../target/generated-resources/webui/META-INF/klab/ui-embedded"),
    lib: {entry: path.resolve(root, "src/embedded-application.ts"), name: "KlabEmbeddedApplication", formats: ["iife"], fileName: () => "renderer.js", cssFileName: "renderer"},
  },
});
