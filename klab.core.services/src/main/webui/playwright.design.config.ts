import { defineConfig } from "@playwright/test";

// The UI regression suite uses isolated API fixtures, never live service mutations.
export default defineConfig({
  testDir: "./tests",
  testMatch: ["flowchart.spec.ts", "credentials.spec.ts", "authority-codelists.spec.ts", "application.spec.ts", "hub-session.spec.ts", "hub-session.browser.spec.ts", "review.spec.ts", "review-rounds.spec.ts", "review-workspace.spec.ts", "theme.spec.ts"],
  timeout: 30000,
  workers: 1,
  use: { baseURL: "http://127.0.0.1:4229", headless: true, channel: "chrome" },
  webServer: {
    command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4229 --strictPort",
    url: "http://127.0.0.1:4229/tests/review.html",
    reuseExistingServer: false,
  },
});
