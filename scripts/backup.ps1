#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectName = 'orbit',
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env'),
    [string]$ComposeFile = (Join-Path $PSScriptRoot '../compose.yaml'),
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '../backups')
)
$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]{0,62}$') { throw 'Invalid Compose project name.' }
$envPath = (Resolve-Path -LiteralPath $EnvFile).Path
$composePath = (Resolve-Path -LiteralPath $ComposeFile).Path
$composeArgs = @('compose', '--project-name', $ProjectName, '--env-file', $envPath, '-f', $composePath)
$containers = @(& docker @composeArgs ps -q db)
if ($LASTEXITCODE -ne 0 -or $containers.Count -ne 1) { throw 'Expected exactly one running database service.' }
$container = $containers[0].Trim()
$owner = & docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' $container
if ($LASTEXITCODE -ne 0 -or $owner.Trim() -ne $ProjectName) { throw 'Database ownership verification failed.' }
$directory = [IO.Path]::GetFullPath($BackupDirectory)
$null = New-Item -ItemType Directory -Force -Path $directory
$suffix = [Guid]::NewGuid().ToString('N')
$filename = 'orbit-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + $suffix.Substring(0, 8) + '.dump'
$destination = Join-Path $directory $filename
$partial = $destination + '.partial'
$remote = '/tmp/orbit-backup-' + $suffix + '.dump'
try {
    # pg_dump reads a consistent snapshot. It does not change source records.
    & docker exec $container sh -c 'umask 077; PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --format=custom --file="$1" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' orbit-backup $remote
    if ($LASTEXITCODE -ne 0) { throw 'Database dump failed.' }
    & docker exec $container pg_restore --list $remote > $null
    if ($LASTEXITCODE -ne 0) { throw 'Backup archive validation failed.' }
    & docker cp "${container}:$remote" $partial
    if ($LASTEXITCODE -ne 0) { throw 'Binary-safe backup copy failed.' }
    if (Test-Path -LiteralPath $destination) { throw 'Refusing to overwrite a backup.' }
    Move-Item -LiteralPath $partial -Destination $destination
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText($destination + '.sha256', "$hash  $filename`n", [Text.Encoding]::ASCII)
    Write-Output "Backup created: $destination"
    Write-Output 'Archive catalog verified; restore into isolation to verify recovered data. Protect and encrypt both files.'
} finally {
    & docker exec $container rm -f -- $remote > $null
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial }
}
