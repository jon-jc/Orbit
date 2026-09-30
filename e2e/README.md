# Browser regression checks

These development-only checks drive the actual UI in fresh Chromium contexts. They require a built Orbit JAR, Java 17 or newer, and Node.js 20 or newer. The runner starts the JAR with the local demo profile, or reuses an existing local server outside CI. There is no frontend build step or production npm dependency.

```sh
./mvnw package
npm ci
npx playwright install chromium
npm run test:e2e
```

Set `ORBIT_BASE_URL` to target a server other than `http://127.0.0.1:8080`. Set `ORBIT_BROWSER_EXECUTABLE` to use an existing compatible Chromium installation. The runner starts its own browser and never connects to an interactive user session.

The desktop check creates, assigns, edits, comments on, reloads, and deletes a uniquely named demo task. Cleanup also runs when an assertion fails. The mobile check creates a unique account, workspace, and project, checks navigation and page overflow, and verifies another sign-in. It archives its project afterward. Account and workspace deletion are intentionally unavailable in the product API, so run this suite against a disposable database; those uniquely named records remain after the run.

Failures retain screenshots and traces under `test-results/`. Inspect a trace with `npx playwright show-trace <trace.zip>`.
