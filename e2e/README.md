# Browser regression checks

These development-only checks drive the actual UI and API in fresh Chromium contexts. Use Java 17 or newer and Node.js 20 or newer. No frontend build step or production npm dependency is required.

Run these commands from the repository root. On Windows, use `./mvnw.cmd package` for the first command.

```sh
./mvnw package
npm ci
npx playwright install chromium
npm run test:e2e
```

With no `ORBIT_BASE_URL`, Playwright starts `target/orbit-1.0.0.jar` using the local profile at `http://127.0.0.1:8080`. Outside CI it reuses a server already running there; in CI the port must be available for a fresh server. The JAR must be built before this automatic startup.

Providing `ORBIT_BASE_URL` disables automatic server startup entirely, including when the value is the default local address. Start that server yourself with the local demo enabled. `ORBIT_BROWSER_EXECUTABLE` can point to an existing compatible Chromium installation instead of the downloaded Playwright browser. The runner launches its own browser and does not connect to an interactive user session.

The desktop test depends on the original demo fixture: the Northstar Studio workspace, Website relaunch project, and Jordan Lee account. Use a fresh, disposable local database if those records have been renamed or removed. Do not point this suite at a production database.

The two tests verify:

- Desktop demo sign-in; task creation with project, assignee, priority, and date; search; status changes; comments displayed as literal text; persistence after browser reload; and permanent task deletion confirmed through the API.
- Mobile account registration; workspace and project creation; drawer state and inaccessible offscreen navigation; absence of page overflow at 390 × 844; project persistence after reload; and sign-out followed by another sign-in.

Both tests also reject uncaught browser JavaScript exceptions. They are Chromium smoke checks; they do not provide exhaustive accessibility, cross-browser, load, or security coverage. Server-side authorization and data rules have separate Java tests.

Each run uses unique record names. The desktop test attempts task cleanup in a `finally` block, including after assertion failures; cleanup requires the server and authenticated session to remain available. Its audit events remain in the demo workspace. On success, the mobile test archives its project. The new account and workspace remain because the API has no deletion endpoint for them; a failed mobile test can also leave its project active. The default local profile uses the persistent `data/orbit` database, so use a disposable checkout or explicitly configure a temporary database for repeated tests.

Use `npm run test:e2e:headed` for visible debugging and `npm run format:check` to check frontend and test source formatting.

Failures retain screenshots and traces under `test-results/`. Inspect a trace with `npx playwright show-trace <trace.zip>`.
