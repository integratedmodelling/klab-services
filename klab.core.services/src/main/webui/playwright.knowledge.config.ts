import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", testMatch: "knowledge.spec.ts", timeout: 30000, workers: 1,
  use: { baseURL: "http://127.0.0.1:4229", headless: true, channel: "chrome" },
  webServer: { command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4229 --strictPort",
    url: "http://127.0.0.1:4229/tests/knowledge.html", reuseExistingServer: true },
});
