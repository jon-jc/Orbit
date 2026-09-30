#!/usr/bin/env sh
# Disposable Linux integration drill: never selects an existing Compose project.
set -eu
umask 077
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
scratch=$(mktemp -d "${TMPDIR:-/tmp}/orbit-recovery-check-XXXXXX")
suffix=$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n')
project="orbit-recovery-check-$suffix"
recovery="orbit-restore-check-$suffix"
preserved="orbit-restore-preserved-$suffix"
env_file="$scratch/test.env"
compose_file="$root/compose.yaml"
cleanup() {
    for target in "$recovery" "$preserved"; do
        if [ "$(docker inspect --format '{{index .Config.Labels "io.orbit.recovery"}}' "$target" 2>/dev/null || true)" = true ]; then
            docker rm -f "$target" >/dev/null
        fi
        if [ "$(docker volume inspect --format '{{index .Labels "io.orbit.recovery"}}' "$target-data" 2>/dev/null || true)" = true ]; then
            docker volume rm "$target-data" >/dev/null
        fi
    done
    docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file" down --volumes >/dev/null 2>&1 || true
    case "$scratch" in */orbit-recovery-check-*) rm -rf -- "$scratch";; esac
}
trap cleanup EXIT HUP INT TERM
password=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
cat > "$env_file" <<EOF
POSTGRES_DB=orbit_recovery_check
POSTGRES_USER=orbit_recovery_check
POSTGRES_PASSWORD=$password
ORBIT_PUBLIC_BASE_URL=https://recovery.invalid
ORBIT_MAIL_ENABLED=false
ORBIT_EMAIL_VERIFICATION_REQUIRED=false
EOF
docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file" up -d db
container=$(docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file" ps -q db)
ready=false
attempt=0
while [ "$attempt" -lt 60 ]; do
    if [ "$(docker inspect --format '{{.State.Health.Status}}' "$container")" = healthy ]; then ready=true; break; fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$ready" = true ] || { echo 'Disposable source did not become healthy.' >&2; exit 1; }
for migration in "$root"/src/main/resources/db/migration/V*.sql; do
    docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U orbit_recovery_check -d orbit_recovery_check < "$migration" >/dev/null
done
docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U orbit_recovery_check -d orbit_recovery_check <<'SQL' >/dev/null
INSERT INTO app_user(id,email,display_name,password_hash,created_at) VALUES ('10000000-0000-4000-8000-000000000001','restore-probe@example.test','Recovery üser','fixture-only-hash',CURRENT_TIMESTAMP);
INSERT INTO workspace(id,name) VALUES ('20000000-0000-4000-8000-000000000001','Recovery workspace');
INSERT INTO workspace_member(workspace_id,user_id,role) VALUES ('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','OWNER');
INSERT INTO project(id,workspace_id,name,description,color) VALUES ('30000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','Recovery project','Restored project','#7367f0');
INSERT INTO task(id,workspace_id,project_id,title,description,status,priority,assignee_id,due_date,version) VALUES ('40000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','Unicode café, 漢字','Line one'||chr(10)||'Line two, "quoted"','IN_PROGRESS','URGENT','10000000-0000-4000-8000-000000000001','2026-10-10',7);
INSERT INTO task_comment(id,workspace_id,task_id,author_id,body) VALUES ('50000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','Preserved comment');
INSERT INTO activity_event(id,workspace_id,actor_id,action,entity_type,entity_name) VALUES ('60000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','created','task','Unicode café, 漢字');
INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES ('70000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000001',1,1,28800,9999999999999,'restore-probe@example.test');
INSERT INTO spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) VALUES ('70000000-0000-4000-8000-000000000001','restore-byte-probe',decode('00017fff','hex'));
SQL
ORBIT_COMPOSE_PROJECT="$project" ORBIT_ENV_FILE="$env_file" ORBIT_COMPOSE_FILE="$compose_file" sh "$root/scripts/backup.sh" "$scratch/backups"
set -- "$scratch"/backups/*.dump
backup=$1
sh "$root/scripts/restore.sh" "$backup" "$recovery"
actual=$(docker exec "$recovery" psql -At -U orbit_restore -d orbit_restore -c "SELECT title||'|'||version||'|'||due_date||'|'||assignee_id FROM task")
[ "$actual" = 'Unicode café, 漢字|7|2026-10-10|10000000-0000-4000-8000-000000000001' ]
actual=$(docker exec "$recovery" psql -At -U orbit_restore -d orbit_restore -c "SELECT (SELECT COUNT(*) FROM task_comment)||'|'||(SELECT COUNT(*) FROM activity_event)||'|'||(SELECT COUNT(*) FROM spring_session)")
[ "$actual" = '1|1|0' ]
[ "$(docker exec "$recovery" psql -At -U orbit_restore -d orbit_restore -c 'SHOW default_transaction_read_only')" = on ]
[ "$(docker inspect --format '{{.HostConfig.NetworkMode}}/{{.HostConfig.ReadonlyRootfs}}/{{len .HostConfig.PortBindings}}' "$recovery")" = 'none/true/0' ]
if docker exec "$recovery" psql -v ON_ERROR_STOP=1 -U orbit_restore -d orbit_restore -c "UPDATE task SET title='unexpected write'" >/dev/null 2>&1; then
    echo 'Recovered default session allowed a write.' >&2; exit 1
fi
if sh "$root/scripts/restore.sh" "$backup" "$recovery" >/dev/null 2>&1; then
    echo 'An existing recovery target was reused.' >&2; exit 1
fi
cp "$backup" "$scratch/damaged.dump"
cp "$backup.sha256" "$scratch/damaged.dump.sha256"
printf 'damage' >> "$scratch/damaged.dump"
if sh "$root/scripts/restore.sh" "$scratch/damaged.dump" "orbit-restore-damaged-$suffix" >/dev/null 2>&1; then
    echo 'Checksum mismatch was accepted.' >&2; exit 1
fi
[ -z "$(docker container ls -a --filter "name=^/orbit-restore-damaged-$suffix$" -q)" ]
ORBIT_RESTORE_PRESERVE_SESSIONS=true sh "$root/scripts/restore.sh" "$backup" "$preserved"
[ "$(docker exec "$preserved" psql -At -U orbit_restore -d orbit_restore -c "SELECT encode(attribute_bytes,'hex') FROM spring_session_attributes")" = '00017fff' ]
actual=$(docker exec "$preserved" psql -At -U orbit_restore -d orbit_restore -c "SELECT description=E'Line one\nLine two, \"quoted\"' FROM task")
[ "$actual" = t ]
[ "$(docker exec "$container" psql -At -U orbit_recovery_check -d orbit_recovery_check -c "SELECT (SELECT COUNT(*) FROM task)||'|'||(SELECT COUNT(*) FROM spring_session)")" = '1|1' ]
echo 'PostgreSQL recovery drill passed: isolated/new volume, Unicode, relations/version, binary sessions, revocation, read-only defaults, checksum/collision rejection, source unchanged.'
