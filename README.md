# Orbit

Orbit is a collaborative project workspace built with **Java 17 and Spring Boot 4.1.1**. A browser application and transactional HTTP API ship in one executable JAR. Teams can organize projects, assign work, discuss tasks, manage access, and review activity. Every product screen uses the real API; changes persist across reloads and application restarts.

PostgreSQL is the production database. Persistent H2 provides immediate local use without installing a database. Spring serves the HTML, CSS, and JavaScript directly: **no Node.js build, CDN, or separate frontend server is required to run Orbit**. Optional Node.js tools support browser tests and formatting. This guide covers product use, development, deployment, and operations. Customer rollout still requires environment-specific security review, capacity planning, backups, and operational ownership.

## Product capabilities

| Area | Capabilities |
| --- | --- |
| Workspaces | Create and switch workspaces with separate projects, tasks, and membership. |
| Overview | Review total, completed, in-progress, and overdue work; inspect project progress, deadlines, and recent activity. |
| Projects | Create and edit named, colored projects; review task progress; archive finished initiatives. |
| Tasks | Switch board/list views; edit descriptions, status, priority, assignee, project, and deadline; delete with confirmation. |
| Search | Combine literal text search with status, priority, project, and assignee filters; navigate paged results. |
| Discussion | Add comments with author identity/time and revisit them in task details. |
| Team | Add registered accounts as members/viewers, change roles, and remove membership. |
| Activity/export | Browse activity history and download workspace tasks as spreadsheet-safe CSV. |
| Browser | Responsive desktop/mobile navigation, labeled forms, keyboard-accessible dialogs, and loading, empty, error, and conflict states. |

Task statuses are `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, and `DONE`; priorities are `LOW`, `MEDIUM`, `HIGH`, and `URGENT`. Tasks belong to a project and may have an assignee and deadline. Overdue counts compare unfinished tasks with the current UTC date. Archived projects can be restored, remain editable, and may contain tasks. Board changes use status selectors. Each filter initially loads 50 tasks with a **Load more** control; board lane counts describe loaded results. CSV exports all workspace tasks independently of the current filters.

### First-session walkthrough

1. Start Orbit and choose the demo entry, or register an account. The browser completes sign-in and offers workspace creation; registration alone does neither automatically through the API.
2. Open **Overview**, inspect progress, then select or create a project with a name, description, and color.
3. In **My workspace**, create work and choose its project, status, priority, assignee, and optional deadline. Switch views and try search/filters.
4. Open task details, edit the task, and add a comment. Reload and reopen it to verify persistence.
5. An owner can add someone in **Team** using their already registered email. MEMBER can collaborate; VIEWER can read. Membership is immediate; no invitation email is sent.
6. Review **Activity** or export tasks. Switch workspaces to see each team's independent resources.

Account settings support profile updates, verification, password changes/recovery, and individual session revocation. Owners can rename workspace settings and send/revoke seven-day email invitations for members/viewers. The account notification inbox records assignments, comments, and access changes, with individual/all read controls and saved workspace/task links. Email flows require configured SMTP; local Mailpit captures test delivery.

## Requirements and quickstart

| Purpose | Requirements |
| --- | --- |
| Build/run | JDK 17 or later, `JAVA_HOME`, and network access for the first wrapper run. |
| Linux/macOS wrapper | `unzip`, so Maven's pinned ZIP can be checksum-verified. |
| Docker deployment/PostgreSQL tests | Docker Engine/Desktop with Compose and a running daemon. |
| Optional browser/format tools | Node.js 20 or later, npm, and Playwright Chromium; CI uses Node.js 24. |

The wrapper pins Maven 3.9.11 and its distribution checksum. A global Maven installation is unnecessary. Run commands from the repository root.

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

Open [http://localhost:8080](http://localhost:8080). Alternatively, double-click `start.cmd` or run `./start.sh`. The launchers use Java from `JAVA_HOME` when set, with a PATH fallback. They build a missing JAR and deliberately select the **local** profile on `127.0.0.1`; they do not select production from environment variables. Rebuild with `verify` after source changes before reusing an existing JAR. Direct `java` commands and automatic browser-test startup additionally require Java on PATH.

Local demo credentials:

```text
Email:    alex@orbit.local
Password: OrbitDemo!2026
```

**Northstar Studio** contains three projects, 21 tasks, teammates, comments, and activity. Dates are relative to initialization. The seed runs once: edits remain, and restarting does not restore original demo content. Published demo credentials belong only in local development.

### Persistence and profiles

The default `local` profile binds HTTP to loopback and stores H2 in `data/`, relative to the working directory. Users, domain records, and JDBC sessions survive restarts. Stop Orbit before copying/replacing database files. `data/` is ignored by Git and excluded from the image.

To initialize without demo content:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--orbit.demo.enabled=false'
```

This does not delete existing data. For a fresh demo, stop the app and choose a new disposable database, preserving local work first. The explicit `prod` profile requires PostgreSQL credentials, disables local demo initialization, and defaults to secure cookies. Never deploy with the default local profile.

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

Copy `.env.example` to `.env`, choose a long random database password, and keep the file private:

```powershell
Copy-Item .env.example .env
# Edit .env before starting.
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 app
```

Linux/macOS use `cp .env.example .env` and `chmod 600 .env`, then the same Compose commands. Required database variables are checked. Never commit `.env`; use platform secret injection rather than build arguments or passwords in URLs for managed deployments.

The multi-stage image builds and verifies Java, then runs a Java 17 runtime as UID/GID **10001**. Compose drops capabilities, prevents privilege escalation, sets a read-only application root filesystem, and provides bounded `/tmp`. PostgreSQL 17 uses a persistent named volume. Neither DB nor management is published; HTTP maps to `127.0.0.1:8080` by default.

### HTTPS with Caddy

On a host with Caddy installed, point a domain you control at the host and allow certificate issuance and inbound ports 80/443. Save this as `Caddyfile`, replacing the domain:

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
java -jar .\target\orbit-1.0.0.jar
```

On Linux, supply the same environment through your service manager before `java -jar target/orbit-1.0.0.jar`. Configure certificate trust for the database provider. Flyway validates/applies migrations at startup, so the credential currently needs migration privileges. Stricter runtime privileges require a separate approved migration step.

## Source layout and architecture

```text
src/main/java/com/orbit/auth/       Accounts, authentication, current user
src/main/java/com/orbit/config/     Security, cookies, limits, errors, request IDs
src/main/java/com/orbit/domain/     Workspace/project/task/comment/activity services
src/main/resources/db/migration/   Domain schema and JDBC-session migrations
src/main/resources/static/         Browser HTML, CSS, JavaScript, favicon
src/test/java/                    H2/security and PostgreSQL contract checks
e2e/                              Desktop/mobile browser checks
docs/                             API, architecture, operations
.github/                          CI and dependency updates
```

Orbit is a modular monolith using Spring MVC, Security, JDBC, Session JDBC, Jakarta Validation, Flyway, and Actuator. Controllers accept validated records and pass the authenticated actor ID to services. Parameterized queries and explicit response models keep SQL structure and database internals out of user-controlled JSON.

Membership and object lookups are workspace-scoped; composite foreign keys also prevent foreign project/assignee references. Mutation and activity inserts share a transaction. Removing a member clears assignments and increments affected task versions while retaining historical authorship. JDBC sessions support replicas sharing sign-in state; database availability and shared throttling remain separate concerns.

## HTTP API

Routes are same-origin. JSON uses camelCase, UUID-string IDs, ISO timestamps, and calendar-date deadlines. Creation returns 201, edits 200, and deletion/logout 204. See [API.md](docs/API.md) for exact fields and validation limits.

### Sessions and CSRF

Request `/api/auth/csrf`, retain the cookie, and send its token/header name for every mutation, including registration/login/logout. Login rotates the session ID and clears the previous CSRF token; fetch another token afterward. Local `curl`/`jq` example:

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
| `/api/workspaces` | GET memberships; POST workspace. |
| `/members`, `/members/{userId}` | GET/POST members; PATCH role; DELETE membership. |
| `/projects`, `/projects/{projectId}` | GET/POST projects; PATCH project. |
| `/tasks`, `/tasks/{taskId}` | GET/POST tasks; GET/PATCH/DELETE task. |
| `/tasks/{taskId}/comments` | GET/POST comments. |
| `/overview`, `/activity`, `/export` | GET dashboard, paged activity, or CSV. |

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

PATCH requires the complete editable object and current numeric `version`, rather than JSON Merge Patch. DELETE requires `?version=N`. Atomic comparison/increment prevents silent overwrites; stale edits receive **409**. Reload and reconcile before resubmitting.

Task queries support `q`, `status`, `priority`, `projectId`, `assigneeId`, `page`, and `size`. Search is literal and case-insensitive, including `%`, `_`, and `!`. Pages start at zero; sizes are 1–100 with task/activity defaults 50/30. The envelope is `{items,page,size,total,totalPages}`. Concurrent writes can move records between requests. Creates/comments lack idempotency keys, so blind retries may duplicate data.

RFC ProblemDetail-style errors include `status`, `title`, `detail`, and `requestId`; validation can add `errors`. Responses carry `X-Request-ID`. Statuses include 400 invalid input, 401 unauthenticated, 403 role/CSRF, 404 absent/inaccessible, 409 conflict, 413 oversized body, and 429 auth throttling with `Retry-After`. Unexpected failures return generic 500 details without stack traces.

## Verification and developer tools

**55 backend test executions passed**: 32 H2/security/account/email tests plus 23 account, collaboration, and workflow checks against PostgreSQL 17. **Six Chromium scenarios passed** for desktop and mobile. These counts are executed checks, not a penetration test or throughput claim.

| Checks | Coverage |
| --- | --- |
| Backend | Real filters/cookies, CSRF, rotation/logout, input validation, tenant isolation, roles, stale versions, assignment removal, comments, overview, literal search/paging, CSV, and abuse controls. |
| PostgreSQL | Shared eight-scenario HTTP contract, real database, and migrations. |
| Desktop | Task creation, assignment, date/status/priority edits, comments, reload persistence, and deletion. |
| Mobile | Signup, workspace/project creation, navigation/overflow, reload, and subsequent sign-in. |

Production Compose also passed a complete HTTP task/comment flow, an application-only restart with unchanged session identity, persisted data, and a subsequent CSRF-authenticated edit. Nonroot identity, read-only filesystem, healthy readiness, disabled demo, and private management networking were checked.

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Ppostgres-tests verify
```

Use `./mvnw` on Linux/macOS. The PostgreSQL profile uses an isolated PostgreSQL 17 Testcontainers container. Missing Docker does not silently select H2. Reports are in `target/surefire-reports/` and `target/failsafe-reports/`.

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

Linux may need `npx playwright install --with-deps chromium`. Playwright starts the local JAR or reuses an existing server outside CI. `ORBIT_BASE_URL` selects a disposable alternate server; `ORBIT_BROWSER_EXECUTABLE` selects compatible installed Chromium. Browser tests retain created accounts/workspaces because their deletion APIs are absent: use disposable data. Failure screenshots/traces are in `test-results/`; inspect with `npx playwright show-trace <trace.zip>`. See [browser guidance](e2e/README.md).

## Security and known limits

| Role | Permissions |
| --- | --- |
| OWNER | Read/export, create/edit projects and tasks, delete tasks, add comments, manage membership. |
| MEMBER | Read/export, create/edit projects and tasks, delete tasks, add comments. |
| VIEWER | Read/export only. |

Initial membership accepts MEMBER/VIEWER; owners may promote existing members. Self-demotion and deleting owner membership return 409. Each request checks current database membership; foreign resource IDs are rejected. Inaccessible workspaces use 404 to avoid disclosure.

BCrypt cost 12 protects passwords; registration allows 12–72 characters and at most 72 UTF-8 bytes. The session identifier stays in an HttpOnly cookie, not browser local storage; passwords are never stored in that cookie. JDBC persists security contexts and session/CSRF state; logout invalidates the session. Parameterized SQL, input constraints, workspace guards, database relationships, escaped browser text, and a same-origin script CSP provide overlapping protections. Inline styles remain allowed. CSV fields are quoted and formula-like values prefixed for spreadsheet safety.

Registration is open. Rate limits are per instance/IP, proxies can collapse addresses, and replicas do not share counters. Public traffic needs a gateway-wide abuse/signup policy. Activity is an application trail, not a tamper-proof compliance archive.

There is no SSO/MFA, account/workspace deletion, billing, attachments, or live push. Notifications refresh on demand. Workspace/task links restore context and recheck access. Task deletion is permanent. Exports are synchronous and large workspaces require measurement. No SLA, formal penetration-test result, or automatic disaster-recovery guarantee is claimed.

Account APIs support profile updates, email verification, password recovery, and active-session controls. SMTP delivery uses an encrypted transactional outbox with bounded retries. See [Account security and email](docs/ACCOUNT_SECURITY.md) for setup, migration behavior, and exact endpoints.

## Operations and troubleshooting

Within the app container, management uses `http://127.0.0.1:9091` with paths `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/prometheus`. These are unauthenticated within loopback. Docker probes liveness; readiness includes DB. Do not publish management to simplify scraping. Monitor errors/latency, JVM memory, pool saturation, DB locks/connections/storage, throttling, and backup age.

The volume survives normal replacement, but persistence is not a backup. **`docker compose down -v` deletes database storage**; never use it for routine updates. Encrypt backups, copy them off-host, and define retention/PITR requirements. Linux logical dump:

```sh
mkdir -p backups
chmod 700 backups
umask 077
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > backups/orbit.dump
```

Use binary-safe output. Older Windows PowerShell redirection can alter native binary data; dump inside the container and copy the file out, or use verified backup tooling. Dumps contain content, password hashes, and sessions. Test restoration in isolation and exercise product flows before relying on backups.

Applied Flyway migrations are immutable: add a version instead of editing history or disabling validation. Back up before schema changes and plan compatible rollouts. Code rollback does not undo migration; restoration can lose newer writes. Decide whether restored sessions need revocation during incident recovery. [RUNBOOK.md](docs/RUNBOOK.md) provides detailed procedures.

| Symptom | First checks |
| --- | --- |
| Wrapper checksum failure | Install `unzip`; retain checksum verification. |
| Startup fails | Profile/DB variables, TLS/network, database health, first migration error. |
| Port in use | Existing server or `PORT`; browser-test server reuse. |
| Login followed by 401 | HTTPS/Secure cookie, origin/path, browser cookie acceptance. |
| Mutation 403 | Fresh CSRF after login/logout and correct role/session. |
| Edit/delete 409 | Reload/reconcile version; member removal also changes versions. |
| Resource 404 | Workspace context, membership, object ID. |
| Auth 429 | Proxy-shared address, bursts, ingress policy. |
| Payload 413 | Reduce below the 64 KiB cap. |
| Pool exhaustion | Query latency, blocked transactions, capacity, per-replica connection budget. |

## CI, release workflow, and documentation

CI runs Java verification, PostgreSQL Testcontainers, Chromium scenarios, and an image build. PR dependency review rejects newly introduced high/critical vulnerabilities including development scopes. Enable the dependency graph; native review requires a public repository or supported GitHub Advanced Security configuration for private repositories.

OWASP Dependency-Check runs only with repository secret `NVD_API_KEY`; otherwise it reports an explicit skip notice. It fails at CVSS 7 or higher. **The full vulnerability scan has not been run locally.** Passing builds do not imply clean vulnerability reports or completed remote CI. Dependabot proposes Maven, npm, action, and image updates.

Release through a focused reviewed PR with relevant checks. Review migrations/security, record the tested artifact, and deploy with secrets/TLS, a backup, measured limits, and rollback ownership. Verify the deployed browser flow and restore procedure. Pin tested image digests/action commits where policy requires.

- [API guide](docs/API.md): exact fields, validation, roles, paging, errors.
- [Architecture](docs/ARCHITECTURE.md): modules, relationships, transactions, sessions.
- [Runbook](docs/RUNBOOK.md): rollout, ingress, monitoring, backup/restore, incidents.
- [Browser checks](e2e/README.md): test data, browser selection, traces, cleanup.

Compose is a single-host reference, without database failover, autoscaling, automated remote backups, or alert delivery. Choose and verify those services for your environment before customer rollout.