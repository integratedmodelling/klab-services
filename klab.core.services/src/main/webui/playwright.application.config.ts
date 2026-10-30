import {defineConfig} from "@playwright/test";
export default defineConfig({
  testDir: "./tests", testMatch: ["application.spec.ts"], timeout: 30000,
  use: {baseURL: "http://127.0.0.1:4218", headless: true, channel: process.env.KLAB_TEST_BROWSER_CHANNEL || undefined},
  webServer: {command: "npm run dev -- --host 127.0.0.1 --port 4218 --strictPort",
    url: "http://127.0.0.1:4218/tests/application.html", reuseExistingServer: false},
});
