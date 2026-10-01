# Browser regression checks

These development-only checks drive the actual UI and API in fresh Chromium contexts. Use Java 17 or newer and Node.js 20 or newer. No frontend build step or production npm dependency is required.

Run these commands from the repository root. On Windows, use `./mvnw.cmd package` for the first command.

```sh
./mvnw package
npm ci
npx playwright install chromium
npm run test:e2e
```

With no `ORBIT_BASE_URL`, Playwright's Node launcher reads the root POM's artifact and version and starts that exact executable JAR from `target` using the local profile at `http://127.0.0.1:8080`. It fails if that JAR is missing, even when an older version exists. Outside CI it reuses a server already running there; in CI the port must be available for a fresh server. The JAR must be built before this automatic startup.

Providing `ORBIT_BASE_URL` disables automatic server startup entirely, including when the value is the default local address. Start that server yourself with the local demo enabled. `ORBIT_BROWSER_EXECUTABLE` can point to an existing compatible Chromium installation instead of the downloaded Playwright browser. The runner launches its own browser and does not connect to an interactive user session.

The desktop test depends on the original demo fixture: the Northstar Studio workspace, Website relaunch project, and Jordan Lee account. Use a fresh, disposable local database if those records have been renamed or removed. Do not point this suite at a production database.

Eight product tests always run:

- Desktop demo sign-in; task creation with project, assignee, priority, and date; search; status changes; comments displayed as literal text; restoration of an open task from its URL after reload; and permanent task deletion confirmed through the API.
- Mobile account registration; workspace and project creation; drawer state and inaccessible offscreen navigation; absence of page overflow at 390 × 844; project persistence after reload; and sign-out followed by another sign-in.
- Profile and owner workspace renaming; workspace creation and switching with deliberately held API responses, including rejection of stale settings and metadata failures; selection of the remembered workspace when the URL contains no workspace; revocation of another session and the current session; password changes invalidating every session; old-password rejection; and successful sign-in with the new password.
- Assignment, comment, and membership notifications for a separate account; individual and bulk read controls; links opening the correct task and workspace; persistence of that open task after reload; and mobile navigation without page overflow.
- Selection of loaded tasks; status, priority, and assignee changes saved together; a deliberately stale task version returning a conflict with every requested change unapplied; refresh and successful retry; and a held batch completion preserving a newer selection and its changes.
- Private saved-view creation, renaming, filter updates and deletion; another workspace member receiving neither the private view nor access to its ID; viewer access to personal views; URL filters and list layout surviving reload; browser Back restoring filters; keyboard task search and creation; a persisted single-key shortcut opt-out; and desktop-to-mobile board resizing with a populated final status column.
- A held profile response released after a different account signs in; a project-archive confirmation held across a workspace change; and verification that neither response changes the new account or workspace.
- Delayed command searches released after closing the palette, switching workspaces, and signing out; and a held saved-view response released after a workspace change without painting the other workspace.

Two email tests run when `ORBIT_MAILPIT_URL` is set:

- Registration email captured through actual SMTP delivery; verification through the emailed link; the confirmed account state; forgot-password delivery; password reset through its emailed link; old-password rejection; new-password sign-in; and receipt of the password-change security notice.
- Owner invitation delivery; opening its emailed link in a fresh browser context; registering the invited address; verifying that account through its captured verification email; returning to the pending invitation; accepting viewer access; and the owner's invitation history recording acceptance.

All executed tests reject uncaught browser JavaScript exceptions. They are Chromium smoke checks; they do not provide exhaustive accessibility, cross-browser, load, or security coverage. Server-side authorization, required-verification enforcement, token expiry, cooldowns, and data rules have separate Java tests. The browser suite expects the local profile's registration-enabled, verification-optional configuration so that its non-email tests work without an SMTP service.

The CI browser step uses `SPRING_APPLICATION_JSON` to set `orbit.auth.rate-limit-enabled=false` only for its disposable browser-test application. Its repeated account and session flows share the runner's loopback address and would otherwise compete for one abuse-limit bucket. The product's limiter remains enabled by default, and dedicated Java tests continue to verify its enforcement. An existing server keeps its own configuration when `ORBIT_BASE_URL` is supplied.

To include email coverage, start the loopback-only sink with `docker compose -f compose.dev.yaml up -d`, configure Orbit's real SMTP delivery as described in [ACCOUNT_SECURITY.md](../docs/ACCOUNT_SECURITY.md#local-mailpit-without-external-delivery), and set `ORBIT_MAILPIT_URL=http://127.0.0.1:8025` for the test process. Set `ORBIT_PUBLIC_BASE_URL` to the Orbit address being tested. For an existing server on port 8082, a PowerShell invocation is:

```powershell
$env:ORBIT_BASE_URL = 'http://127.0.0.1:8082'
$env:ORBIT_MAILPIT_URL = 'http://127.0.0.1:8025'
npm run test:e2e
```

With automatic startup, set the SMTP and encryption-key environment variables before `npm run test:e2e`; Playwright's Java process inherits them. When `ORBIT_MAILPIT_URL` is absent, only the two email tests are explicitly skipped. When it is present, email tests fail if Orbit reports mail disabled or if real messages fail to reach the sink. They select messages by unique recipient and subject, validate the email link's public origin, and use a separate API client for Mailpit so Orbit session cookies are not sent to the sink. CI configures the sink and executes all ten tests.

Each run uses unique record names. The desktop and notification tests attempt task cleanup in `finally` blocks, including after assertion failures; cleanup requires the server and authenticated session to remain available. Audit events remain. On success, the mobile project test archives its project. Accounts, workspaces, memberships, notifications, and the notification fixture's project remain because the API has no account or workspace deletion endpoint. Failed tests can leave their fixtures active. Email tests attempt to delete only captured messages addressed to their unique recipients; they never submit a Mailpit delete request with an empty message-ID list. The default local profile uses the persistent `data/orbit` database, so use a disposable checkout or explicitly configure a temporary database for repeated tests.

Use `npm run test:e2e:headed` for visible debugging and `npm run format:check` to check frontend and test source formatting.

Failures retain screenshots and traces under `test-results/`. Inspect a trace with `npx playwright show-trace <trace.zip>`. Traces can contain test session data and email-token URLs; keep them within the test environment. The runner does not print captured email bodies or tokens in assertions.

