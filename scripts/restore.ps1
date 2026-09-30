#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$Name = ('orbit-restore-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)),
    [string]$Database = 'orbit_restore',
    [string]$Username = 'orbit_restore',
    [string]$Image = 'postgres:17-alpine',
    [switch]$PreserveSessions
)
$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^orbit-restore-[a-z0-9][a-z0-9_-]{0,80}$') { throw 'Recovery names must start with orbit-restore-.' }
if ($Database -notmatch '^[a-z][a-z0-9_]{0,62}$' -or $Username -notmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'Use simple lowercase database/user identifiers.' }
if ($Image -notmatch '^postgres:17([-.a-z0-9]+)?(@sha256:[a-f0-9]{64})?$') { throw 'Use an explicit compatible official PostgreSQL 17 image.' }
$backup = (Resolve-Path -LiteralPath $BackupFile).Path
$checksum = $backup + '.sha256'
if (!(Test-Path -LiteralPath $checksum)) { throw 'The matching .sha256 sidecar is required.' }
$expected = (Get-Content -LiteralPath $checksum -Raw).Trim().Split(' ')[0]
if ($expected -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expected) { throw 'Backup checksum mismatch.' }
$volume = $Name + '-data'
$existing = @(& docker container ls -a --filter "name=^/$Name`$" -q)
if ($LASTEXITCODE -ne 0 -or $existing.Count -gt 0) { throw 'Cannot create recovery container; existing targets are never reused.' }
$existingVolume = @(& docker volume ls --filter "name=^$volume`$" --format '{{.Name}}')
if ($LASTEXITCODE -ne 0 -or $existingVolume.Count -gt 0) { throw 'Cannot create recovery volume; existing volumes are never reused.' }
& docker volume create --label io.orbit.recovery=true $volume > $null
if ($LASTEXITCODE -ne 0) { throw 'Recovery volume creation failed.' }
$previousPassword = $env:POSTGRES_PASSWORD
$hadPassword = Test-Path Env:POSTGRES_PASSWORD
try {
    # Pass the generated password through process environment, not printed command arguments.
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $env:POSTGRES_PASSWORD = [Convert]::ToBase64String($bytes)
    & docker run --detach --name $Name --label io.orbit.recovery=true --network none --read-only --tmpfs /tmp --tmpfs /var/run/postgresql --security-opt no-new-privileges:true --mount "type=volume,source=$volume,target=/var/lib/postgresql/data" --mount "type=bind,source=$backup,target=/restore/orbit.dump,readonly" --env POSTGRES_PASSWORD --env "POSTGRES_DB=$Database" --env "POSTGRES_USER=$Username" $Image > $null
    if ($LASTEXITCODE -ne 0) { throw 'Recovery container creation failed.' }
} finally {
    if ($hadPassword) { $env:POSTGRES_PASSWORD = $previousPassword } else { Remove-Item Env:POSTGRES_PASSWORD -ErrorAction SilentlyContinue }
}
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $logs = (& docker logs $Name 2>&1 | Out-String)
    if ($logs.Contains('PostgreSQL init process complete; ready for start up.')) {
        & docker exec $Name pg_isready -U $Username -d $Database > $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    }
    Start-Sleep -Seconds 1
}
if (!$ready) { throw "Recovery database not ready. Inspect $Name; source data was not touched." }
& docker exec $Name pg_restore --exit-on-error --no-owner --no-acl -U $Username -d $Database /restore/orbit.dump
if ($LASTEXITCODE -ne 0) { throw "Restore failed. Inspect $Name; source data was not touched." }
if (!$PreserveSessions) {
    $sessionTable = ([string](& docker exec $Name psql -U $Username -d $Database -Atc "SELECT to_regclass('public.spring_session')")).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Session-table verification failed.' }
    if ($sessionTable) {
        & docker exec $Name psql -v ON_ERROR_STOP=1 -U $Username -d $Database -c 'DELETE FROM spring_session' > $null
        if ($LASTEXITCODE -ne 0) { throw 'Recovered session revocation failed.' }
    }
}
& docker exec $Name psql -v ON_ERROR_STOP=1 -U $Username -d $Database -c "ALTER DATABASE $Database SET default_transaction_read_only = on" > $null
if ($LASTEXITCODE -ne 0) { throw 'Could not set recovery database read-only.' }
Write-Output "Recovery ready: container=$Name volume=$volume database=$Database user=$Username"
Write-Output 'Isolated network, no published ports, fresh volume, read-only transactions. Inspect data through docker exec; do not attach production traffic or SMTP.'
