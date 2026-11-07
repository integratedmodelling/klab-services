import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", testMatch: ["review.spec.ts", "review-rounds.spec.ts", "review-workspace.spec.ts"], timeout: 30000,
  use: { baseURL: "http://127.0.0.1:4229", headless: true,
    channel: process.env.KLAB_TEST_BROWSER_CHANNEL || undefined },
  webServer: { command: "npm run dev -- --host 127.0.0.1 --port 4229 --strictPort",
    url: "http://127.0.0.1:4229/tests/review.html", reuseExistingServer: false },
});
