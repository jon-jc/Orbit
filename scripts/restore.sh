#!/usr/bin/env sh
set -eu
umask 077
backup=${1:?Usage: restore.sh BACKUP.dump [orbit-restore-NAME]}
backup=$(CDPATH= cd -- "$(dirname -- "$backup")" && pwd)/$(basename -- "$backup")
suffix=$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
name=${2:-"orbit-restore-$(date -u +%Y%m%d%H%M%S)-$suffix"}
database=orbit_restore
username=orbit_restore
image=${ORBIT_RESTORE_IMAGE:-postgres:17-alpine}
case "$name" in orbit-restore-*) ;; *) echo 'Recovery names must start with orbit-restore-.' >&2; exit 1;; esac
case "$name" in *[!a-z0-9_-]*) echo 'Invalid recovery name.' >&2; exit 1;; esac
case "$image" in postgres:17-alpine) ;; *) echo 'Use the tested PostgreSQL 17 alpine image.' >&2; exit 1;; esac
[ -f "$backup" ] && [ -f "$backup.sha256" ] || { echo 'Backup and .sha256 sidecar are required.' >&2; exit 1; }
expected=$(cut -d ' ' -f1 "$backup.sha256")
if command -v sha256sum >/dev/null 2>&1; then actual=$(sha256sum "$backup" | cut -d ' ' -f1); else actual=$(shasum -a 256 "$backup" | cut -d ' ' -f1); fi
[ "$expected" = "$actual" ] || { echo 'Backup checksum mismatch.' >&2; exit 1; }
volume="$name-data"
[ -z "$(docker container ls -a --filter "name=^/$name$" -q)" ] || { echo 'Existing containers are never reused.' >&2; exit 1; }
[ -z "$(docker volume ls --filter "name=^$volume$" --format '{{.Name}}')" ] || { echo 'Existing volumes are never reused.' >&2; exit 1; }
docker volume create --label io.orbit.recovery=true "$volume" >/dev/null
password=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
POSTGRES_PASSWORD="$password" docker run --detach --name "$name" --label io.orbit.recovery=true --network none --read-only --tmpfs /tmp --tmpfs /var/run/postgresql --security-opt no-new-privileges:true --mount "type=volume,source=$volume,target=/var/lib/postgresql/data" --mount "type=bind,source=$backup,target=/restore/orbit.dump,readonly" --env POSTGRES_PASSWORD --env "POSTGRES_DB=$database" --env "POSTGRES_USER=$username" "$image" >/dev/null
ready=false
attempt=0
while [ "$attempt" -lt 60 ]; do
    if docker logs "$name" 2>&1 | grep -q 'PostgreSQL init process complete; ready for start up.'; then
        if docker exec "$name" pg_isready -U "$username" -d "$database" >/dev/null 2>&1; then ready=true; break; fi
    fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$ready" = true ] || { echo "Recovery database not ready; inspect $name." >&2; exit 1; }
docker exec "$name" pg_restore --exit-on-error --no-owner --no-acl -U "$username" -d "$database" /restore/orbit.dump
if [ "${ORBIT_RESTORE_PRESERVE_SESSIONS:-false}" != true ]; then
    session_table=$(docker exec "$name" psql -U "$username" -d "$database" -Atc "SELECT to_regclass('public.spring_session')")
    if [ -n "$session_table" ]; then docker exec "$name" psql -v ON_ERROR_STOP=1 -U "$username" -d "$database" -c 'DELETE FROM spring_session' >/dev/null; fi
fi
docker exec "$name" psql -v ON_ERROR_STOP=1 -U "$username" -d "$database" -c "ALTER DATABASE $database SET default_transaction_read_only = on" >/dev/null
printf 'Recovery ready: container=%s volume=%s database=%s user=%s\n' "$name" "$volume" "$database" "$username"
printf 'Isolated network, no published ports, fresh volume, read-only transactions. Do not attach production traffic or SMTP.\n'
