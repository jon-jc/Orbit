# Operations runbook

## Release checklist

- Run `./mvnw -Ppostgres-tests verify` for both H2 and PostgreSQL in one invocation, using Docker or a dedicated test database. Confirm tests ran rather than being skipped; a preceding plain `verify` repeats H2 unnecessarily.
- Inspect the unconditional Trivy runtime OS/Java scan and CycloneDX SBOM. Selected HIGH/CRITICAL findings fail CI, including unfixed findings. Review remaining lower-severity CVEs and alternate-source ratings; Trivy normally prefers distro vendor severity. Update vulnerable packages; do not treat an optional OWASP skip or a successful build as zero vulnerabilities.
- Use the exact release image digest, executable, checksums, and signed provenance from the [release guide](RELEASE.md). The version pipeline promotes the tested image without rebuilding; do not substitute an unverified local rebuild. CI action sources are pinned to verified official release commits; review Dependabot updates before refreshing them. Verify registry access explicitly; a new GHCR package may be private.
- Use the `prod` profile, secret-injected database credentials, secure cookies, and HTTPS. Set the HTTPS public link origin, SMTP sender/credentials, and a protected 32-byte outbox encryption key. Keep the application behind a trusted ingress and management endpoints private.
- Decide whether public registration is acceptable with `ORBIT_REGISTRATION_ENABLED`. Add shared signup/login/recovery throttling and controlled email delivery. Account verification, password recovery, and session revocation are implemented; MFA/SSO remain extensions.
- Restore a representative database backup into isolation and verify memberships, projects, tasks, private saved views, comments, activity, token state, and encrypted outbox recovery. Recovered sessions are purged by default. Record recovery time and recovery-point expectations.
- Measure representative workspace sizes and traffic. Set application memory/CPU limits, database connection budgets, ingress body/time limits, and alerts from observed behavior.
- Verify deployed cookies, session rotation, logout, CSRF rejection, owner/viewer access, and cross-workspace isolation over the real TLS route.
- Set privacy/retention policies and a support plan for accounts with unreachable email addresses. Account/workspace deletion and automated token/security-event/notification retention are not implemented.
- Keep the previous artifact, a pre-migration backup, and an operator responsible for recovery. A code rollback cannot undo a database migration.

## HTTPS ingress

Compose publishes the application at `127.0.0.1:8080`. An example Caddy configuration on the same host:

```caddyfile
orbit.example.com {
    encode zstd gzip
    header Strict-Transport-Security "max-age=31536000"
    reverse_proxy 127.0.0.1:8080
}
```

Replace the domain with one you own and configure DNS before use. Preserve the original Host at the proxy. Enable the HSTS header only after verifying that the domain has reliable HTTPS; the example retains that policy for one year. Orbit does not enable general forwarding-header trust by default. If your hosting environment requires it, sanitize headers at the ingress and restrict backend access before enabling Spring's forwarding-header support.

Use `ORBIT_SESSION_SECURE=true` with HTTPS. A Secure cookie intentionally prevents HTTP sign-in from persisting. If sign-in returns success but the next request is 401, first inspect whether the browser accepted/sent the cookie and whether its domain, path, protocol, and SameSite behavior match the deployed origin.

## Health and monitoring

Within the application container:

```sh
curl -fsS http://127.0.0.1:9091/actuator/health
curl -fsS http://127.0.0.1:9091/actuator/health/liveness
curl -fsS http://127.0.0.1:9091/actuator/health/readiness
curl -fsS http://127.0.0.1:9091/actuator/prometheus
```

Port 9091 binds to loopback and is not published. A scraper outside the container cannot reach it without an intentional secure arrangement such as a shared network namespace or controlled local agent. Do not casually publish it to solve scraping. Docker's liveness probe confirms the application is running; use the overall health endpoint, database monitoring, and a synthetic signed-in workflow to detect database or product failures. A readiness response alone does not prove all business operations work.

Monitor HTTP error rates/latency, authentication 429 responses, JDBC pool saturation, JVM memory/GC, process restarts, PostgreSQL connections/locks/storage, and backup age. Logs use request IDs; use the response ID when investigating a failed request. Do not log passwords, session cookies, CSRF tokens, or complete sensitive task content.

Monitor mail queue age, `orbit.mail.pending`, `orbit.mail.delivery`, FAILED outbox rows, and SMTP-provider delivery failures. A healthy app does not prove email reached an inbox. The dispatcher retries eight times and SMTP is at least once; duplicate messages can occur after a crash. Review the [account/email guide](ACCOUNT_SECURITY.md) for encryption-key retention, retry behavior, and Mailpit drills.

## Startup and migration

```sh
docker compose up --build -d
docker compose ps
docker compose logs --tail=200 app
```

The database must be healthy before the app starts. Flyway applies schema migrations and refuses changed checksums. Do not edit an already applied migration: add a new version. Do not disable validation or run `flyway repair` without understanding and reviewing the exact mismatch. The current startup performs migrations with the runtime credential; it therefore needs appropriate migration privileges. If your policy requires a least-privilege runtime account, introduce a separate approved migration step before removing those permissions.

V3 account security marks historical accounts unverified and invalidates legacy unversioned sessions. The production verification policy requires these users to verify before signing in again. Configure and test SMTP before rollout; there is no automatic bulk verification campaign. V4 creates the encrypted mail outbox; retain its key separately from database backups. V5 adds versioned workspace settings, hashed invitations, and account-scoped notifications.

Plan forward-compatible migrations for rolling upgrades. Back up before schema changes. If the new app fails after migration, diagnose schema compatibility before starting the previous version. Restore into a separate database when feasible, verify it, and deliberately switch traffic. Database rollback can lose writes made after the backup.

## Backups and restoration

Persistent storage survives ordinary container replacement. `docker compose down -v` deletes the named database volume and must never be part of a routine update. Volume persistence is not a backup.

Use PostgreSQL-native backups, encrypt them, copy them off-host, and define retention. For production use managed point-in-time recovery or a tested WAL archive in addition to logical dumps. The executable scripts verify the selected Compose project and create a custom-format archive, validate its catalog, copy it without PowerShell binary redirection, and create a SHA-256 sidecar.

Windows, from the repository root, with the same project name as your running deployment:

```powershell
.\scripts\backup.ps1 -ProjectName orbit -EnvFile .\.env -BackupDirectory .\backups
.\scripts\restore.ps1 -BackupFile .\backups\YOUR_BACKUP.dump -Name orbit-restore-drill
```

Linux/macOS:

```sh
chmod +x scripts/backup.sh scripts/restore.sh
ORBIT_COMPOSE_PROJECT=orbit ORBIT_ENV_FILE="$PWD/.env" ./scripts/backup.sh "$PWD/backups"
./scripts/restore.sh ./backups/YOUR_BACKUP.dump orbit-restore-drill
```

Replace the filename with an actual backup and keep its matching `.sha256`. Restore refuses existing resources, creates a fresh PostgreSQL 17 volume/container, binds the archive read-only, uses no network/published ports, and sets new sessions to read-only transactions. It removes recovered JDBC sessions by default. Source records are untouched. Checksum validation detects corruption, not malicious replacement; only restore trusted archives. Protect files because they contain customer data, password hashes, token hashes, and possibly sessions.

Inspect recovered content through the isolated container:

```sh
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SHOW default_transaction_read_only'
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SELECT COUNT(*) FROM task'
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SELECT COUNT(*) FROM spring_session'
```

Verify Unicode/multiline content, relations, memberships, assignment/version/deadline state, comments, activity, migrations, and token/outbox policy. Keep SMTP disabled so pending messages are not replayed. Recovery does not automatically promote the database, reconnect a production network, or switch traffic. A privileged operator can deliberately override default read-only transactions; it is a protective default rather than immutable storage.

The [script guide](../scripts/README.md) documents parameters, session-preservation opt-in, exact labeled-resource cleanup, and a Linux CI recovery drill. Before activation, plan outstanding account/invitation tokens, obtain the stable mail encryption key, apply the matching release, verify signed-in operations in isolation, and approve cutover. A code rollback cannot reverse schema changes; restoring an older snapshot loses later writes. Preserve the previous artifact and the pre-migration backup, and measure recovery rather than assuming an objective.

## Incident checks

**Application will not start:** Confirm `SPRING_PROFILES_ACTIVE=prod`, database variables, absolute HTTPS public origin, SMTP host/from/key when enabled, DNS/TLS connectivity, Postgres health, and migration logs. Enabled verification with registration requires enabled mail. Missing configuration should fail rather than select the local database. Avoid repeatedly restarting a crash loop without inspecting its first error.

**Login returns 429:** The limiter is per remote IP and instance. Proxy traffic can share an address. Check gateway configuration and traffic patterns; use a shared limiter policy rather than globally disabling protection. Tests deliberately disable the local limiter because they repeatedly authenticate from one test address.

**Mutations return 403:** Refresh the CSRF token after sign-in/logout, verify the cookie was sent, then inspect membership role. A viewer cannot mutate. Do not disable CSRF to resolve an integration mismatch.

**An edit returns 409:** Reload the current task/project and reconcile the change. Deletion requires a current version. Removing an assignee also changes the task version to protect stale editors.

**Requests return 404:** Validate workspace context, membership, and object IDs. The API intentionally hides inaccessible workspaces with 404.

**Email not received:** A generic 202 or invitation 201 is acceptance, not delivery confirmation. Check `mailEnabled`, outbox status/age, SMTP credentials/TLS/network, the outbox encryption key, and provider quarantine/bounces. Local mail is disabled unless explicitly enabled; use Mailpit for drills. Do not log raw links or resend expired tokens blindly.

**Verification blocks an existing account:** Migration intentionally did not trust historical email addresses. Use the anonymous resend flow and the user's actual mailbox. Plan support recovery for unavailable addresses; the API does not change account email. Do not bulk-mark accounts verified without a reviewed identity-validation policy.

**Database latency/pool exhaustion:** Inspect query latency, active/blocked transactions, storage, and connection counts. The pool is bounded at 12 connections per instance by default. More replicas multiply that demand; do not raise the pool size before checking database capacity and lock contention.

**Suspected credential compromise:** Restrict ingress, preserve logs, use password recovery/change to revoke all account sessions or revoke individual sessions from the account interface, rotate relevant secrets, inspect membership/activity/security events, and follow your incident policy. The API is self-service; it does not provide an operator-wide session-administration console or tamper-proof audit export. Account/invitation tokens and pending mail require a separate reviewed incident policy.

## Reference deployment limits

Compose runs one app and one database on a single host. It does not configure automatic failover, remote backups, autoscaling, certificate issuance, centralized logs, or alert delivery. JDBC sessions support replicas, but they do not make the database highly available. Public deployment still needs an environment-specific threat review and operational ownership.
