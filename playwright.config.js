import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  outputDir: "./test-results",
  webServer: process.env.ORBIT_BASE_URL
    ? undefined
    : {
        command:
          "java -jar target/orbit-1.0.0.jar --spring.profiles.active=local --server.address=127.0.0.1",
        url: "http://127.0.0.1:8080",
        timeout: 120_000,
        reuseExistingServer: !process.env.CI,
      },
  use: {
    baseURL: process.env.ORBIT_BASE_URL || "http://127.0.0.1:8080",
    browserName: "chromium",
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 12_000,
    navigationTimeout: 20_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    launchOptions: process.env.ORBIT_BROWSER_EXECUTABLE
      ? { executablePath: process.env.ORBIT_BROWSER_EXECUTABLE }
      : {},
  },
});
