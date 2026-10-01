# Orbit

Orbit **1.1.0** is a collaborative project workspace built with **Java 17 and Spring Boot 4.1.1**. Its browser and transactional API ship in one JAR. Organize projects, assign work, discuss tasks, manage access, and review activity. Screens use the real API; changes persist across reloads/restarts.

PostgreSQL is the production database; persistent H2 supports immediate local use. Spring serves HTML/CSS/JavaScript: **no Node.js build, CDN, or separate frontend server is required**. Optional tools support browser/format checks. Customer rollout requires security review, capacity planning, backups, and operational ownership.

## Product capabilities

| Area | Capabilities |
| --- | --- |
| Workspaces | Create/switch independent workspaces; owners rename with conflict protection. |
| Overview | Review total, completed, in-progress, and overdue work; inspect project progress, deadlines, and recent activity. |
| Projects | Create and edit named, colored projects; review task progress; archive finished initiatives. |
| Tasks | Switch board/list views; edit descriptions, status, priority, assignee, project, and deadline; select tasks for atomic status/priority/assignment changes; delete with confirmation. |
| Search | Combine literal search with status, priority, project, and assignee; use all-tasks/assigned-to-me shortcuts, private saved views, paged results, and shareable filter/layout URLs. |
| Discussion | Add comments with author identity/time and revisit them in task details. |
| Team | Add registered accounts, change roles/remove access, or send expiring email invitations with acceptance history. |
| Account | Edit display name; verify email; recover/change passwords; inspect/revoke active sessions. |
| Notifications | Assignment/comment/access inbox, unread count, individual/all read controls, and workspace/task links. |
| Activity/export | Browse activity history and download workspace tasks as spreadsheet-safe CSV. |
| Browser | Responsive navigation and bulk controls, labeled forms/dialogs, keyboard command palette, optional shortcuts, and loading, empty, error, and conflict states. |

Statuses are `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `DONE`; priorities are `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Tasks require a project; assignee/deadline are optional. Overdue counts use the UTC date. Archived projects remain editable and can be restored. Boards use status selectors, initially loading 50 filtered tasks with **Load more**; lane counts describe loaded results. Bulk selection covers loaded tasks, at most 100, and does not select every matching record in the database. CSV exports every workspace task regardless of filters.

### First-session walkthrough

1. Start Orbit and choose the demo entry, or register an account. The browser completes sign-in and offers workspace creation; registration alone does neither automatically through the API.
2. Open **Overview**, inspect progress, then select or create a project with a name, description, and color.
3. In **My workspace**, create work and choose its project, status, priority, assignee, and optional deadline. Try board/list filters, save a private view, and select loaded tasks to apply several changes together. A stale bulk version changes none of the selected tasks; refresh and reselect.
4. Open task details, edit the task, and add a comment. Reload and reopen it to verify persistence.
5. An owner can add a registered email immediately or invite an address through configured email. Invitations expire in seven days; the recipient must sign in with the matching email. MEMBER collaborates; VIEWER reads.
6. Review **Activity**, export tasks, or open **Notifications**. Links restore the workspace and open task after reload. Owners can rename workspace settings; account settings manage display name, passwords, and sessions.

Use Ctrl/Cmd+K for the command palette: search workspace tasks, jump to sections/private views, or create work. Task search shows up to eight matches with access to the full search. Arrow keys/Enter/Escape navigate it. Optional `N`, `/`, and `?` shortcuts respect typing/modal contexts and can be disabled in Help; the preference persists. Saved views belong to their creator, including viewers, with an 80-character label and a limit of 100 per user/workspace. Rename, replace filters, apply, or delete them; workspace owners cannot inspect another member's private view.

## Requirements and quickstart

| Purpose | Requirements |
| --- | --- |
| Build/run | JDK 17 or later, `JAVA_HOME`, and network access for the first wrapper run. |
| Linux/macOS wrapper | `unzip`, so Maven's pinned ZIP can be checksum-verified. |
| Docker deployment/PostgreSQL tests | Docker Engine/Desktop with Compose and a running daemon. |
| Optional browser/format tools | Node.js 20 or later, npm, and Playwright Chromium; CI uses Node.js 24. |
| Optional OpenAPI validation | Python 3.10 or later with pip/venv; [commands](api/README.md). |

The wrapper pins Maven 3.9.16 and its distribution checksum. A global Maven installation is unnecessary. Run commands from the repository root.

**Windows PowerShell:**

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```sh
chmod +x mvnw start.sh
./mvnw verify
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080), double-click `start.cmd`, or run `./start.sh`. Launchers honor `JAVA_HOME` with a PATH fallback, build only a missing JAR, and force **local** on loopback. Rebuild after source edits before reusing a JAR. Direct `java` commands/browser-test startup require Java on PATH.

Local demo credentials:

```text
Email:    alex@orbit.local
Password: OrbitDemo!2026
```

**Northstar Studio** contains three projects, 21 tasks, teammates, comments, and activity with relative deadlines. It seeds once; edits survive restarts. Public demo credentials belong only in local development.

### Persistence and profiles

Default `local` binds loopback and stores H2 in `data/` relative to the working directory. Records/JDBC sessions survive restarts. Stop Orbit before copying database files. Git/images exclude `data/`.

To initialize without demo content:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--orbit.demo.enabled=false'
```

This does not delete data. For a fresh demo, preserve local work and choose a new disposable database. `prod` requires PostgreSQL/HTTPS configuration, disables demo, and defaults to secure cookies/required verification. Never deploy with `local`.

### Local email workflows

Local email is disabled by default. To exercise verification, recovery, and invitations without external delivery, start `docker compose -f compose.dev.yaml up -d` and configure the loopback Mailpit SMTP sink. Its inbox is [http://127.0.0.1:8025](http://127.0.0.1:8025). The [account/email guide](docs/ACCOUNT_SECURITY.md) provides runnable Windows/Linux startup, test-key generation, and mandatory SMTP settings. The UI explains when email actions are unavailable.

## Configuration

Override Spring properties through environment variables or application arguments. Cookie protections and the API body limit also have explicit code enforcement.

| Setting | Default/requirement | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Default `local`; deploy with `prod` | Select database/deployment profile. |
| `DB_URL` | Required in `prod` | PostgreSQL JDBC URL; configure verified database TLS where appropriate. |
| `DB_USERNAME`, `DB_PASSWORD` | Required in `prod` | Runtime/migration credentials; no production password fallback. |
| `PORT` / `server.port` | `8080` | Application HTTP port. |
| `server.address` | Loopback in `local` | Local bind address; Compose publishes only loopback separately. |
| `ORBIT_SESSION_SECURE` | Local `false`; production `true` | Secure cookie; production browser traffic requires HTTPS. |
| `ORBIT_PUBLIC_BASE_URL` | Local `http://127.0.0.1:8080`; required HTTPS origin in `prod` | Action-link origin; no path, credentials, query, or fragment. |
| `ORBIT_REGISTRATION_ENABLED` | `true` | Control self-service registration. |
| `ORBIT_EMAIL_VERIFICATION_REQUIRED` | Local `false`; production `true` | Deny sign-in until email verification. |
| `ORBIT_MAIL_ENABLED` | Local `false`; production Compose `true` | Enable durable SMTP; verification with signup requires it. |
| `ORBIT_MAIL_HOST`, `ORBIT_MAIL_PORT` | Host required when enabled; port `587` | SMTP destination. |
| `ORBIT_MAIL_USERNAME`, `ORBIT_MAIL_PASSWORD` | Provider configuration | Authentication is used when username is nonblank. |
| `ORBIT_MAIL_FROM` | Required valid sender when enabled | Provider-approved single email address. |
| `ORBIT_MAIL_STARTTLS` | `true` | Require STARTTLS/identity validation; ten-second SMTP timeouts. |
| `ORBIT_MAIL_ENCRYPTION_KEY` | Required Base64 32-byte key when enabled | AES-GCM outbox-body encryption; stable/shared across replicas. |
| `orbit.accounts.reset-token-ttl` | `30m` | Reset link expiry; configurable one minute to seven days. |
| `orbit.accounts.verification-token-ttl` | `24h` | Verification expiry; same range. |
| `orbit.accounts.token-send-cooldown` | `1m` | Per account/purpose issuance interval; one second to one hour. |
| `orbit.mail.dispatch-delay-ms` | `5000` | Background outbox dispatch interval. |
| Session cookie | `ORBIT_SESSION`, path `/` | Explicit HttpOnly, SameSite=Lax serializer. |
| `server.servlet.session.timeout` | `8h` | Configured session inactivity timeout. |
| `management.server.port/address` | Production `9091` / `127.0.0.1` | Private management server; unpublished in Compose. |
| Management routes | Health and Prometheus | Health details hidden; readiness includes DB; other management requests denied. |
| `orbit.demo.enabled` | Local `true`; otherwise `false` | Seeds only with the local-profile demo runner. |
| `orbit.auth.rate-limit-enabled` | `true` | Per-instance auth limit: 20 requests/IP/minute; 10,000 bounded IP windows. |
| API body cap | Fixed 65,536 bytes | Declared and streamed `/api/` payloads are limited before deserialization; 413 on excess. |
| `server.max-http-request-header-size` | `16KB` | Header bound. |
| Hikari maximum/minimum idle | `12` / `2` | Connections per replica; acquisition timeout 10 seconds. |
| `spring.session.jdbc.cleanup-cron` | Every five minutes | Expired-session cleanup. |
| Graceful shutdown | Lifecycle timeout `30s` | Controlled request completion. |
| Production console logs | ECS structured format | Request-ID correlation. |

Compose also reads `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and optional `ORBIT_PORT` from `.env`. The latter changes the host mapping; container HTTP remains 8080. General forwarding-header trust is disabled by default. Sanitize ingress headers and restrict backend access before enabling it for a hosting platform.

## Production deployment

Copy `.env.example` to `.env`, replace every placeholder, and keep it private. Supply a long random database password, HTTPS public origin, approved SMTP settings, and a securely generated 32-byte Base64 outbox key:

```powershell
Copy-Item .env.example .env
# Edit .env before starting.
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 app
```

Linux/macOS use `cp .env.example .env` and `chmod 600 .env`, then the same Compose commands. Required database/public-origin variables are checked. Enabled mail validates its host, sender, and key; test actual delivery before signup. Use secret injection for managed deployments, and keep a protected outbox-key recovery copy separately from database backups.

The multi-stage image builds and verifies Java, then runs a Java 17 runtime as UID/GID **10001**. Compose drops capabilities, prevents privilege escalation, sets a read-only application root filesystem, and provides bounded `/tmp`. PostgreSQL 17 uses a persistent named volume. Neither DB nor management is published; HTTP maps to `127.0.0.1:8080` by default.

### HTTPS with Caddy

Install Caddy, point your domain at the host, and allow certificate issuance/ports 80/443. Save this `Caddyfile`, replacing the domain:

```caddyfile
orbit.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
    # Enable after confirming reliable HTTPS:
    # header Strict-Transport-Security "max-age=31536000"
}
```

```sh
caddy validate --config Caddyfile
caddy run --config Caddyfile
```

Use the Caddy service for persistent operation and preserve the original Host at the proxy. Keep `ORBIT_SESSION_SECURE=true`. Sign-in over plain HTTP will not persist with a Secure cookie; set it to `false` only for an isolated local HTTP smoke test and restore it afterward. See [RUNBOOK.md](docs/RUNBOOK.md) for TLS/HSTS and recovery guidance.

### Managed PostgreSQL/JAR

```powershell
.\mvnw.cmd verify
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:DB_URL = 'jdbc:postgresql://database.internal:5432/orbit?sslmode=verify-full'
$env:DB_USERNAME = 'orbit'
# Supply DB_PASSWORD through deployment secret injection.
$env:ORBIT_SESSION_SECURE = 'true'
$env:ORBIT_PUBLIC_BASE_URL = 'https://orbit.example.com'
# Supply required SMTP variables and the outbox key through secret injection.
java -jar .\target\orbit-1.1.0.jar
```

On Linux, supply these variables through your service manager before `java -jar target/orbit-1.1.0.jar`. Configure database certificate trust. Flyway applies migrations at startup; stricter runtime privileges require a separate migration step.

## Source layout and architecture

```text
src/main/java/com/orbit/auth/       Accounts, tokens, authentication, sessions
src/main/java/com/orbit/config/     Security, cookies, limits, errors, request IDs
src/main/java/com/orbit/domain/     Workspaces, tasks, invitations, notifications
src/main/java/com/orbit/mail/       Encrypted transactional outbox and SMTP dispatcher
src/main/resources/db/migration/   Domain schema and JDBC-session migrations
src/main/resources/static/         Browser HTML, CSS, JavaScript, favicon
src/test/java/                    H2/security and PostgreSQL contract checks
e2e/                              Desktop/mobile browser checks
docs/                             API, architecture, operations
api/                              OpenAPI contract and validation tools
scripts/                          Backup and isolated recovery tools
.github/                          CI and dependency updates
```

Orbit is a modular monolith using Spring MVC, Security, JDBC, Session JDBC, Jakarta Validation, Flyway, and Actuator. Controllers accept validated records and pass the authenticated actor ID to services. Parameterized queries and explicit response models keep SQL structure and database internals out of user-controlled JSON.

Membership and object lookups are workspace-scoped; composite foreign keys prevent foreign project/assignee references. Mutation, activity, notifications, and mail intent share a transaction. Removing membership clears assignments/increments versions while retaining historical authorship. Inbox visibility follows current membership. JDBC sessions support replicas; account versions enforce password revocation. Email uses authenticated encryption and a leased, retrying outbox; SMTP is at least once. Database availability and shared throttling remain separate concerns.

## HTTP API

Same-origin JSON uses camelCase, UUID IDs, ISO timestamps, and calendar-date deadlines. Creation returns 201, edits 200, deletion/logout 204. [API.md](docs/API.md) specifies fields/limits.

### Sessions and CSRF

Get `/api/auth/csrf`, retain cookies, and send its token/header on every mutation, including registration/login/logout. Login rotates the session and clears CSRF; fetch a fresh token. Local `curl`/`jq`:

```sh
BASE=http://localhost:8080
umask 077
TOKEN=$(curl -fsS -c cookies.txt "$BASE/api/auth/csrf" | jq -r .token)
curl -fsS -b cookies.txt -c cookies.txt \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" \
  -d '{"email":"alex@orbit.local","password":"OrbitDemo!2026"}' \
  "$BASE/api/auth/login"
TOKEN=$(curl -fsS -b cookies.txt -c cookies.txt "$BASE/api/auth/csrf" | jq -r .token)
curl -fsS -b cookies.txt "$BASE/api/workspaces"
rm cookies.txt
```

The cookie file is a credential. This example uses public local demo credentials; do not place customer passwords/tokens in shell history.

### Endpoint groups and task payload

Workspace-relative paths start with `/api/workspaces/{workspaceId}`. Every write requires CSRF.

| Routes | Methods/behavior |
| --- | --- |
| `/api/auth/{csrf,config,me}` | GET token/config/current user. |
| `/api/auth/{register,login,logout}` | POST account creation/sign-in/session invalidation. |
| `/api/auth/{forgot-password,reset-password,verify-email,resend-verification}` | POST email lifecycle flows. |
| `/api/account`, `/api/account/{password,verification,sessions}` | Profile, password change, resend, session list/revocation. |
| `/api/workspaces` | GET memberships; POST workspace. |
| `/settings`, `/invitations` | Versioned owner settings and email invitation management. |
| `/members`, `/members/{userId}` | GET/POST members; PATCH role; DELETE membership. |
| `/projects`, `/projects/{projectId}` | GET/POST projects; PATCH project. |
| `/tasks`, `/tasks/{taskId}` | GET/POST tasks; GET/PATCH/DELETE task. |
| `/tasks/bulk` | POST atomic status/priority/assignment changes for 1–100 task IDs with their exact current version map; OWNER/MEMBER. |
| `/tasks/{taskId}/comments` | GET/POST comments. |
| `/saved-views` | GET own private views; POST label and filters; any current member, including VIEWER. |
| `/saved-views/{viewId}` | GET/PATCH/DELETE own private view; PATCH replaces filters with current version; foreign owners' IDs return 404. |
| `/overview`, `/activity`, `/export` | GET dashboard, paged activity, or CSV. |
| `/api/invitations/{preview,accept}` | Token preview and email-bound acceptance. |
| `/api/notifications` | Paged inbox and scoped read/all-read mutations. |

```json
{
  "title": "Review launch checklist",
  "description": "Confirm accessibility and rollback steps.",
  "projectId": "d7c5ac86-fbbb-414b-9e25-c2b8dbfa5716",
  "status": "TODO",
  "priority": "HIGH",
  "assigneeId": null,
  "dueDate": "2026-10-10"
}
```

Task PATCH requires the complete editable object and current numeric `version`, rather than JSON Merge Patch. Task DELETE requires `?version=N`. Atomic comparison/increment prevents silent overwrites; stale edits receive **409**. Bulk mutation validates every selected version and changes none if any ID/version is invalid. Private-view PATCH replaces all filters using its version; omitted/null filters clear them. Reload and reconcile before resubmitting.

Task queries support `q`, `status`, `priority`, `projectId`, `assigneeId`, `page`, and `size`. Search is literal and case-insensitive, including `%`, `_`, and `!`. Pages start at zero; sizes are 1–100 with task/activity/inbox defaults 50/30/30. The envelope is `{items,page,size,total,totalPages}`; inbox adds `unreadCount`. Concurrent writes can move records between requests. Creates/comments lack idempotency keys, so blind retries may duplicate data.

ProblemDetail-style errors include `status`, `title`, `detail`, and `requestId`; validation can add `errors`. Responses carry `X-Request-ID`. Statuses include 400 input, 401 unauthenticated, 403 role/CSRF/verification, 404 absent/inaccessible, 405 method (with `Allow`), 406 unacceptable response media, 409 conflict, 413 oversized body, 415 unsupported request media, 429 authentication/invitation limits, and 503 invitations without mail. JSON mutation bodies require `Content-Type: application/json`. The auth limiter supplies `Retry-After`. Unexpected failures return generic 500 details.

## Verification and developer tools

**73 backend test executions passed without failures or skips**: 41 H2/security/mail checks plus 32 scenarios against real PostgreSQL 17.11, including accounts, collaboration, HTTP negotiation, and productivity. **Ten Chromium flows passed without skips**, covering eight product workflows and two captured-SMTP workflows. Thirteen release guard tests passed, including source/provenance identity, registry failure handling, and checksum repeatability. Each published release records its executed browser/backend totals in `release.json`. These checks are not a penetration test or throughput claim.

| Checks | Coverage |
| --- | --- |
| Backend | Real filters/cookies, CSRF, rotation/logout, input validation, tenant isolation, roles, stale versions, assignment removal, comments, overview, literal search/paging, CSV, and abuse controls. |
| Account/mail | Verification/recovery, token expiry/single-use/cooldowns, legacy sessions, all-session revocation, encrypted outbox/retries. |
| PostgreSQL | Shared workflow, account, collaboration, and productivity scenarios with real migrations. |
| Collaboration | Settings/roles, invite email binding/reissue/revocation/expiry/concurrency, scoped notification audiences. |
| Productivity | Atomic bulk changes, scoped IDs/current versions, private saved-view ownership, viewer access, limits, and removal cleanup. |
| Desktop | Task creation, assignment, date/status/priority edits, comments, reload persistence, and deletion. |
| Mobile | Signup, workspace/project creation, navigation/overflow, reload, and subsequent sign-in. |
| Browser lifecycle | Profile/workspace rename, session revocation/password change, notification links/reload, captured verification/reset/invitation email. |

An isolated 1.1.0 production image passed required verification through captured SMTP, password reset/session revocation, and task/comment/bulk/private-view persistence with unchanged session after app-only restart. Saved-view filters and task/view versions were checked exactly. Secure/HttpOnly/Lax defaults, UID 10001, read-only filesystem, readiness/liveness, disabled demo, and private management were checked. Functional HTTP checks explicitly disabled Secure only in that isolated fixture after confirming its default.

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Ppostgres-tests verify
```

Use `./mvnw` on Linux/macOS. Choose plain `verify` for H2 alone, or `-Ppostgres-tests verify` to run both suites in one invocation; running both commands repeats H2. The PostgreSQL profile uses an isolated PostgreSQL 17 Testcontainers container. Missing Docker does not silently select H2. Reports are in `target/surefire-reports/` and `target/failsafe-reports/`.

For a dedicated disposable database instead of Docker:

```powershell
.\mvnw.cmd -Ppostgres-tests verify `
  '-Dorbit.test.db.url=jdbc:postgresql://127.0.0.1:5432/orbit_contract' `
  '-Dorbit.test.db.username=orbit_test' `
  '-Dorbit.test.db.password=<test-only-password>'
```

Tests migrate it and retain generated records. Never use production/shared data. Workflow tests disable seeding and the local auth limiter; separate checks verify abuse controls.

Optional browser/format tools, after building the JAR:

```sh
npm ci
npx playwright install chromium
npm run test:e2e
npm run format:check
# Intentional formatting:
npm run format
```

Linux may need `npx playwright install --with-deps chromium`. Playwright resolves the executable from the current POM or reuses a server outside CI. `ORBIT_BASE_URL` selects a disposable alternate server; `ORBIT_BROWSER_EXECUTABLE` selects installed Chromium. Email scenarios require enabled SMTP and `ORBIT_MAILPIT_URL`, otherwise explicitly skip. CI enables the sink and rejects skips/retries in its verification summary. Tests retain accounts/workspaces: use disposable data. Failure traces can contain test credentials/links; protect `test-results/`. See [browser guidance](e2e/README.md).

## Security and known limits

| Role | Permissions |
| --- | --- |
| OWNER | Read/export, edit/bulk-update/delete tasks, comment, manage membership/invitations/settings, maintain own private views. |
| MEMBER | Read/export, create/edit projects and tasks, bulk-update/delete tasks, add comments, maintain own private views. |
| VIEWER | Read/export shared work and maintain own private views. |

Initial membership accepts MEMBER/VIEWER; owners may promote existing members. Self-demotion and deleting owner membership return 409. Each request checks current database membership; foreign resource IDs are rejected. Inaccessible workspaces use 404 to avoid disclosure.

BCrypt cost 12 protects passwords; registration allows 12–72 characters and at most 72 UTF-8 bytes. The session identifier stays in an HttpOnly cookie, not browser local storage; passwords are never stored in that cookie. JDBC persists security contexts and session/CSRF state; logout invalidates the session. Parameterized SQL, input constraints, workspace guards, database relationships, escaped browser text, and a same-origin script CSP provide overlapping protections. Inline styles remain allowed. CSV fields are quoted and formula-like values prefixed for spreadsheet safety.

Production defaults to configurable signup and verified email. Migration marks historical accounts unverified and invalidates legacy sessions: plan SMTP/support. Action secrets are hashed, expiring, single use. Password replacement revokes sessions; listed IDs are opaque row IDs. Generic recovery reduces direct disclosure without identical-timing guarantees. IP limits are per instance; proxies can collapse addresses. Public traffic needs shared abuse controls. Activity is not tamper-proof.

There is no SSO/MFA, email-address change, account/workspace deletion, billing, attachments, or live push. Notifications refresh on demand. Task/workspace links restore context; access is rechecked. Task deletion is permanent. Exports are synchronous; large workspaces require measurement. Account-token/security-event/notification retention needs an operator policy. Outbox key rotation and operator-wide account administration are not automated. No SLA, formal penetration-test result, or automatic disaster-recovery guarantee is claimed.

## Operations and troubleshooting

Container management uses loopback `http://127.0.0.1:9091` with `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`. Docker probes liveness; readiness includes DB. Keep management private. Monitor latency/errors, JVM memory, pool saturation, DB locks/storage/connections, throttling, mail failures, and backup age.

The volume survives normal replacement, but persistence is not a backup. **`docker compose down -v` deletes database storage**; never use it for routine updates. Encrypt backups, copy them off-host, and define retention/PITR requirements. Windows binary-safe backup and isolated recovery:

```powershell
.\scripts\backup.ps1 -ProjectName orbit -EnvFile .\.env
.\scripts\restore.ps1 -BackupFile .\backups\YOUR_BACKUP.dump -Name orbit-restore-drill
```

Linux/macOS use `scripts/backup.sh` and `scripts/restore.sh`. The scripts catalog-check a logical dump/checksum and restore only into a new labeled volume/container with no network/ports and read-only default transactions. Sessions are removed by default. They never promote production. PostgreSQL drills verified Unicode/multiline content, relations/versions, binary sessions opt-in, checksum/collision rejection, and unchanged source data. See [script guidance](scripts/README.md).

Applied migrations are immutable: add a version rather than editing history. Back up before changes and plan compatible rollout; code rollback cannot undo migration, and restoration loses newer writes. Review recovered tokens and pending email; keep SMTP disabled until replay is approved and recover the outbox key separately. [RUNBOOK.md](docs/RUNBOOK.md) provides procedures.

| Symptom | First checks |
| --- | --- |
| Wrapper checksum failure | Install `unzip`; retain checksum verification. |
| Startup fails | Profile/DB variables, TLS/network, database health, first migration error. |
| Mail startup/delivery fails | HTTPS public origin, SMTP host/from/key, TLS/credentials, outbox age/status. |
| Verification blocks old account | Migration policy; resend to real mailbox and follow support recovery. |
| Port in use | Existing server or `PORT`; browser-test server reuse. |
| Login followed by 401 | HTTPS/Secure cookie, origin/path, browser cookie acceptance. |
| Mutation 403 | Fresh CSRF after login/logout and correct role/session. |
| Edit/delete 409 | Reload/reconcile version; member removal also changes versions. |
| Resource 404 | Workspace context, membership, object ID. |
| Auth 429 | Proxy-shared address, bursts, ingress policy. |
| Payload 413 | Reduce below the 64 KiB cap. |
| Pool exhaustion | Query latency, blocked transactions, capacity, per-replica connection budget. |

## CI, release workflow, and documentation

CI runs on pull requests, weekly schedules, and manual requests; ordinary branch/main pushes do not duplicate the full pipeline. The required `verify` and `dependency-review` jobs keep their names. Shared verification runs H2/PostgreSQL once, validates OpenAPI, exercises isolated Linux recovery and all Chromium scenarios with Mailpit, checks formatting, packages the tested JAR into a runtime image, and probes production behavior. Trivy scans packaged Java and OS dependencies, publishes a CycloneDX SBOM/report, and fails selected HIGH/CRITICAL findings including unfixed ones. Trivy normally uses vendor severity; the full SBOM retains lower-severity CVEs and alternate-source ratings. A passing gate does not mean zero vulnerabilities. See [severity selection](https://trivy.dev/docs/latest/guide/scanner/vulnerability/).

PR dependency review rejects new high/critical findings including development scopes; enable the dependency graph and supported private-repository security configuration. Optional OWASP runs with `NVD_API_KEY`, failing at CVSS 7; otherwise it explicitly skips while Trivy still runs. OWASP has not been run locally. Passing builds do not prove completed remote checks. Dependabot checks Maven, npm, Python validation tools, actions, Dockerfile images, and Compose images weekly. Java 17 and PostgreSQL 17 remain deliberate baselines: major Temurin/PostgreSQL image updates are ignored and require a compatibility/data migration review; minor and patch updates remain eligible.

The reference repository protects `main`: changes require a pull request, an up-to-date branch, passing `verify` and `dependency-review` checks, and resolved review conversations. Force pushes and branch deletion are disabled; these rules apply to administrators. Separate approval votes are not required.

Release through focused reviewed PRs, then an exact POM-matching version tag on main ancestry. The release pipeline runs fresh complete verification and promotes the same tested image into version/full-commit GHCR tags without rebuilding. It publishes the executable, tracked source ZIP, SBOM, vulnerability report, manifest, checksums, and GitHub OIDC provenance. Manual dispatch requires a tag at the current main tip so signed source identity remains exact. Registry tags are never overwritten by the workflow; deploy by the manifest digest. New GHCR packages may be private until the owner configures visibility and verifies anonymous access. See [release and verification instructions](docs/RELEASE.md). Publishing these artifacts does not deploy a public application.

- [API guide](docs/API.md): exact fields, validation, roles, paging, errors.
- [OpenAPI contract](api/openapi.yaml): validated machine-readable operations/schemas.
- [Account/email security](docs/ACCOUNT_SECURITY.md): verification migration, recovery, sessions, SMTP/key operations.
- [Architecture](docs/ARCHITECTURE.md): modules, relationships, transactions, sessions.
- [Runbook](docs/RUNBOOK.md): rollout, ingress, monitoring, backup/restore, incidents.
- [Release guide](docs/RELEASE.md): exact tags, production gates, GHCR access, manifest/checksum/provenance verification, and rollback.
- [Backup/recovery tools](scripts/README.md): safe Windows/Linux commands and limitations.
- [Browser checks](e2e/README.md): test data, browser selection, traces, cleanup.

Compose is a single-host reference, without database failover, autoscaling, automated remote backups, or alert delivery. Choose and verify those services for your environment before customer rollout.
