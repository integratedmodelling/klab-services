import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", timeout: 30000,
  use: { baseURL: "http://127.0.0.1:4217", headless: true,
    channel: process.env.KLAB_TEST_BROWSER_CHANNEL || undefined },
  webServer: { command: "npm run dev -- --host 127.0.0.1 --port 4217 --strictPort",
    url: "http://127.0.0.1:4217/tests/flowchart.html", reuseExistingServer: false },
});
