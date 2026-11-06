import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", testMatch: "review-e2e.spec.ts", timeout: 120000, workers: 1,
  expect: { timeout: 30000 },
  use: { baseURL: "http://127.0.0.1:4233", headless: true, channel: process.env.KLAB_TEST_BROWSER_CHANNEL || undefined },
  webServer: { command: "npm run dev -- --host 127.0.0.1 --port 4233 --strictPort",
    url: "http://127.0.0.1:4233/tests/review-e2e.html", reuseExistingServer: false },
});
