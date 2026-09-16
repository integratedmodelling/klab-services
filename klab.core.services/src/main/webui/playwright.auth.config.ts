import { defineConfig } from "@playwright/test";

// No Vite server is needed: the browser regression loads the production session class directly.
export default defineConfig({
  testDir: "./tests",
  testMatch: ["hub-session.spec.ts", "hub-session.browser.spec.ts"],
  use: { channel: process.env.KLAB_TEST_BROWSER_CHANNEL || undefined },
});
