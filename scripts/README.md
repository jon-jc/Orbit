# PostgreSQL backup and isolated recovery

These scripts operate on Orbit's PostgreSQL 17 reference deployment. Backup reads a consistent PostgreSQL snapshot. Restore creates a **new container and volume**, checks the archive checksum first, has no network or published ports, and sets restored database transactions read-only. It never accepts a production destination connection string. Windows PowerShell 5.1 and POSIX shell entry points are provided.

They are an operator tool, not a backup scheduler or automatic disaster-recovery system. Use trusted archives only: restoring SQL from an untrusted source is code execution under database privileges. SHA-256 detects corruption when the sidecar is trusted; it is not authentication or encryption. Encrypt dumps, protect keys separately, copy backups off-host, and define retention. Logical dumps do not supply point-in-time recovery or capture cluster roles/provider settings.

## Take a backup

Use the same Compose project name, Compose file, and protected environment file as the running database. The default project is `orbit`; explicitly supply it when you started Compose with `--project-name`. The scripts verify the database container's project label, create a custom-format dump inside it, validate the archive catalog, and use a binary-safe `docker cp`. A failed operation does not publish an incomplete `.dump` file. The database account comes from the existing container configuration; passwords are not printed or added to host command arguments.

Windows, from the repository root:

```powershell
.\scripts\backup.ps1 -ProjectName orbit -EnvFile .\.env -BackupDirectory .\backups
```

Linux/macOS:

```sh
chmod +x scripts/backup.sh scripts/restore.sh
ORBIT_COMPOSE_PROJECT=orbit ORBIT_ENV_FILE="$PWD/.env" ./scripts/backup.sh "$PWD/backups"
```

PowerShell also accepts `-ComposeFile`; shell accepts `ORBIT_COMPOSE_FILE`. Defaults point to this repository's `compose.yaml` and `.env`. Shell uses a private umask; on Windows, restrict the backup directory ACL to the recovery operators. The output is a timestamped `.dump` plus matching `.dump.sha256`. Keep both together. Source writes can continue during `pg_dump`, but the dump represents one consistent snapshot and consumes database resources; schedule appropriately.

## Restore without touching the live database

Choose an unused recovery name beginning `orbit-restore-`. Existing containers and volumes are refused, even if stopped. By default the scripts remove recovered JDBC sessions so old browser credentials are not revived.

Windows:

```powershell
.\scripts\restore.ps1 `
  -BackupFile .\backups\orbit-YYYYMMDDTHHMMSSZ-SUFFIX.dump `
  -Name orbit-restore-drill
```

Linux/macOS:

```sh
./scripts/restore.sh ./backups/orbit-YYYYMMDDTHHMMSSZ-SUFFIX.dump orbit-restore-drill
```

Replace the example filename with the actual archive. Docker must be running and the local archive must be available for a read-only bind mount. Restore runs official PostgreSQL 17; a newer major-version image can have different storage layout and compatibility and is not silently substituted. PowerShell can select an explicit compatible PostgreSQL 17 image with `-Image`; shell uses the tested `postgres:17-alpine`.

The recovery password is randomly generated and passed through process environment. The container has a read-only root filesystem, a fresh data volume, temporary writable directories, no network, and no ports. The dump is mounted read-only. After import, `default_transaction_read_only=on` applies to new database sessions. This is a protective default, not an authorization boundary against a privileged recovery operator who deliberately overrides it. No Orbit app or SMTP dispatcher is started.

The resulting default database/user are `orbit_restore`. Inspect them through Docker, not through a public listener:

```sh
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SHOW default_transaction_read_only'
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SELECT COUNT(*) FROM app_user'
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SELECT COUNT(*) FROM task'
docker exec orbit-restore-drill psql -U orbit_restore -d orbit_restore -c 'SELECT COUNT(*) FROM spring_session'
```

Check record counts, memberships, assignees, task versions, Unicode/multiline content, deadlines, comments, activity, migration history, and account/invitation/outbox state. Record restore time and archive age. An archive catalog check alone does not prove recoverability.

Only a deliberate forensic drill should preserve old sessions: PowerShell `-PreserveSessions`, or shell `ORBIT_RESTORE_PRESERVE_SESSIONS=true`. Keep that recovery copy isolated. Restored reset/verification/invitation tokens and pending mail can also revive old actions. Review and revoke them as appropriate before any activation. The script removes sessions only; it does not decide how to retain or invalidate these other records.

## Activation and rollback

These scripts stop at isolated inspection. They do not overwrite live data, reattach production networks, make the database writable for an application, enable email, or switch traffic. An approved recovery requires a separate plan: matching application release and encryption key, reviewed token/session/outbox handling, database privileges/TLS, restored migrations, a signed-in end-to-end workflow in isolation, and a deliberate traffic cutover. Keep external SMTP disabled throughout the drill to avoid sending old action links.

Recover the mail encryption key from protected secret storage if encrypted queued messages must be inspected or delivered. The key is not embedded in the database dump. Keep it separate from backups. Changing the key while old messages remain makes them undecryptable; there is no automatic multi-key rotation.

If restore fails, the recovery container/volume remain for investigation; the source database is unaffected. Inspect logs, archive validity, server compatibility, free disk space, and permissions. Do not retry by pointing recovery at an existing or live volume.

## Clean up a completed drill

Confirm **both** labels are `true` before removing the exact recovery resources:

```sh
docker inspect --format '{{index .Config.Labels "io.orbit.recovery"}}' orbit-restore-drill
docker volume inspect --format '{{index .Labels "io.orbit.recovery"}}' orbit-restore-drill-data
docker rm -f orbit-restore-drill
docker volume rm orbit-restore-drill-data
```

Do not copy removal commands with a live project name. Ordinary production updates use `docker compose up -d`; `docker compose down -v` destroys the live named volume and is not a backup or upgrade step.

## Verification scope

The PowerShell scripts have been executed against disposable Docker PostgreSQL 17 resources: real schema/data, Unicode, multiline quoted text, assignee/deadline/version preservation, comments/activity, default session removal, opt-in binary session restoration, read-only transactions, no network/ports, checksum failure, and target-collision refusal. Source data was checked after recovery. Shell scripts receive syntax checks and a Linux recovery drill in CI. These drills do not establish a production recovery time objective, point-in-time recovery, off-host encryption policy, or workload performance.
