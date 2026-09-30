#!/usr/bin/env sh
set -eu
umask 077
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project=${ORBIT_COMPOSE_PROJECT:-orbit}
env_file=${ORBIT_ENV_FILE:-"$script_dir/../.env"}
compose_file=${ORBIT_COMPOSE_FILE:-"$script_dir/../compose.yaml"}
backup_dir=${1:-"$script_dir/../backups"}
case "$project" in ''|*[!a-z0-9_-]*) echo 'Invalid Compose project name.' >&2; exit 1;; esac
container=$(docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file" ps -q db)
[ -n "$container" ] || { echo 'Database is not running.' >&2; exit 1; }
owner=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$container")
[ "$owner" = "$project" ] || { echo 'Database ownership verification failed.' >&2; exit 1; }
mkdir -p "$backup_dir"
suffix=$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')
filename="orbit-$(date -u +%Y%m%dT%H%M%SZ)-$suffix.dump"
destination="$backup_dir/$filename"
remote="/tmp/orbit-backup-$suffix.dump"
cleanup() { docker exec "$container" rm -f -- "$remote" >/dev/null 2>&1 || true; rm -f -- "$destination.partial"; }
trap cleanup EXIT HUP INT TERM
docker exec "$container" sh -c 'umask 077; PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --format=custom --file="$1" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' orbit-backup "$remote"
docker exec "$container" pg_restore --list "$remote" >/dev/null
docker cp "$container:$remote" "$destination.partial"
[ ! -e "$destination" ] || { echo 'Refusing to overwrite a backup.' >&2; exit 1; }
mv -- "$destination.partial" "$destination"
if command -v sha256sum >/dev/null 2>&1; then hash=$(sha256sum "$destination" | cut -d ' ' -f1); else hash=$(shasum -a 256 "$destination" | cut -d ' ' -f1); fi
printf '%s  %s\n' "$hash" "$filename" > "$destination.sha256"
printf 'Backup created: %s\nArchive catalog verified; protect/encrypt the dump and checksum.\n' "$destination"
