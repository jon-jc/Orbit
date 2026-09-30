# Operations runbook

## Release checklist

- Run `./mvnw verify` and `./mvnw -Ppostgres-tests verify`; the latter uses Docker or a dedicated test database. Confirm tests ran rather than being skipped.
- Inspect dependency scan findings, update vulnerable packages, and review any narrowly scoped suppression with an expiry.
- Build the immutable application image; pin tested image digests and action commits for your deployment policy.
- Use the `prod` profile, secret-injected database credentials, secure cookies, and HTTPS. Keep the application behind a trusted ingress and management endpoints private.
- Decide whether public registration is acceptable. Add shared signup/login throttling and account lifecycle features appropriate to the product's audience.
- Restore a representative database backup into an isolated environment and verify sessions, memberships, projects, tasks, comments, and activity. Record recovery time and recovery-point expectations.
- Measure representative workspace sizes and traffic. Set application memory/CPU limits, database connection budgets, ingress body/time limits, and alerts from observed behavior.
- Verify deployed cookies, session rotation, logout, CSRF rejection, owner/viewer access, and cross-workspace isolation over the real TLS route.
- Set privacy/retention policies and a plan for account recovery and deletion. The current product does not automate those flows.
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

## Startup and migration

```sh
docker compose up --build -d
docker compose ps
docker compose logs --tail=200 app
```

The database must be healthy before the app starts. Flyway applies schema migrations and refuses changed checksums. Do not edit an already applied migration: add a new version. Do not disable validation or run `flyway repair` without understanding and reviewing the exact mismatch. The current startup performs migrations with the runtime credential; it therefore needs appropriate migration privileges. If your policy requires a least-privilege runtime account, introduce a separate approved migration step before removing those permissions.

Plan forward-compatible migrations for rolling upgrades. Back up before schema changes. If the new app fails after migration, diagnose schema compatibility before starting the previous version. Restore into a separate database when feasible, verify it, and deliberately switch traffic. Database rollback can lose writes made after the backup.

## Backups and restoration

Persistent storage survives ordinary container replacement. `docker compose down -v` deletes the named database volume and must never be part of a routine update. Volume persistence is not a backup.

Use PostgreSQL-native backups, encrypt them, copy them off-host, and define retention. For production use managed point-in-time recovery or a tested WAL archive in addition to logical dumps. The following Linux shell example makes a logical backup from Compose without embedding the password in the command:

```sh
mkdir -p backups
chmod 700 backups
umask 077
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "backups/orbit-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

Use a binary-safe shell for the dump redirection. Older Windows PowerShell versions can transform native-command redirected output, so use a verified backup tool or perform the dump inside the container and use `docker cp` on Windows. Protect the resulting files because they contain customer data, password hashes, and sessions.

Restore into a dedicated empty PostgreSQL database with a compatible server version, using a privileged recovery account under operator control:

```sh
# Destination connection settings belong to the isolated recovery environment.
PGHOST=recovery.internal PGPORT=5432 PGUSER=recovery_operator PGDATABASE=orbit_recovery \
  pg_restore --no-owner --exit-on-error --dbname=orbit_recovery backup.dump
```

Supply the recovery password through a protected password file or your secret tooling. Do not copy the example with production destination variables unless performing an approved recovery. Check the dump exit status and restoration logs, start the matching Orbit release, and exercise sign-in and domain workflows before changing traffic. A restored session table can revive previously valid sessions; decide whether to clear sessions as part of incident recovery.

## Incident checks

**Application will not start:** Confirm `SPRING_PROFILES_ACTIVE=prod`, database variables, DNS/TLS connectivity, Postgres health, and migration logs. Missing configuration should fail rather than select the local database. Avoid repeatedly restarting a crash loop without inspecting its first error.

**Login returns 429:** The limiter is per remote IP and instance. Proxy traffic can share an address. Check gateway configuration and traffic patterns; use a shared limiter policy rather than globally disabling protection. Tests deliberately disable the local limiter because they repeatedly authenticate from one test address.

**Mutations return 403:** Refresh the CSRF token after sign-in/logout, verify the cookie was sent, then inspect membership role. A viewer cannot mutate. Do not disable CSRF to resolve an integration mismatch.

**An edit returns 409:** Reload the current task/project and reconcile the change. Deletion requires a current version. Removing an assignee also changes the task version to protect stale editors.

**Requests return 404:** Validate workspace context, membership, and object IDs. The API intentionally hides inaccessible workspaces with 404.

**Database latency/pool exhaustion:** Inspect query latency, active/blocked transactions, storage, and connection counts. The pool is bounded at 12 connections per instance by default. More replicas multiply that demand; do not raise the pool size before checking database capacity and lock contention.

**Suspected credential compromise:** Restrict ingress, preserve logs, revoke affected sessions, rotate relevant secrets, inspect workspace membership/activity, and follow your incident policy. Password reset, per-user session administration, and tamper-proof audit export require product extensions; do not assume the UI supplies them.

## Reference deployment limits

Compose runs one app and one database on a single host. It does not configure automatic failover, remote backups, autoscaling, certificate issuance, centralized logs, or alert delivery. JDBC sessions support replicas, but they do not make the database highly available. Public deployment still needs an environment-specific threat review and operational ownership.
