# Versioned releases

Orbit's release workflow packages and publishes a tested executable and container image. It does not deploy an application to a public host or configure an operator's database, SMTP provider, TLS, backups, or monitoring. Those remain deployment responsibilities described in [RUNBOOK.md](RUNBOOK.md).

The current product version is **1.1.0**. The [Releases page](https://github.com/jon-jc/Orbit/releases) is the authoritative list of published versions. A merged change or successful pull-request check is not itself a published release. Use the selected release's `release.json` for its exact source commit, registry digest, platform, test totals, scanner configuration, and asset hashes.

## What the pipeline verifies

The `verify` and `dependency-review` jobs remain the protected pull-request checks. Full verification runs on pull requests, weekly schedules, and manual requests. Ordinary branch and main pushes do not duplicate that expensive pipeline. A version release runs fresh complete verification through the same local composite action, including a current vulnerability database.

One Maven invocation, `./mvnw -B -ntp -Ppostgres-tests verify`, runs both the H2/security/mail checks and PostgreSQL integration scenarios and creates the executable JAR. CI does not run H2 separately a second time. Browser tests exercise that JAR, including the local-only Mailpit SMTP workflows; retries and skipped scenarios are rejected by the release summary. OpenAPI validation, source formatting, release identity/integrity guard tests, and an isolated database backup/recovery drill also run.

`Dockerfile.runtime` packages precisely the POM-versioned, already-tested executable with production defaults. Its build context contains that executable alone. The ordinary `Dockerfile` remains a self-contained source build for operators; using it locally still runs its own verification. Both images use Java 17, UID/GID 10001, a private management health endpoint, and updated runtime OS packages.

Trivy produces a CycloneDX SBOM and fails selected HIGH/CRITICAL packaged-Java or OS findings, including unfixed findings. Distribution/vendor severity takes precedence where available; this gate does not establish that no CVEs exist or replace a security assessment. Optional OWASP coverage exists only when `NVD_API_KEY` is configured; it is not implied by passing Trivy or by the absence of that secret.

The image then runs an isolated production-profile probe with fresh PostgreSQL and local Mailpit. It verifies required email verification, password reset/session revocation, task/comment/bulk changes, exact saved-view filters/versions, and exact session persistence across an app-only restart. It also checks Secure/HttpOnly/SameSite=Lax production cookie defaults, UID 10001, read-only root filesystem, private management, and healthy readiness/liveness. Functional HTTP loopback requests disable Secure only inside that disposable fixture after its default has been checked. No external mail is sent. Cleanup checks unique ownership labels before deleting fixture resources.

## Publish an exact version

Publish only after the relevant protected pull requests are merged and the resulting code/version is ready for distribution. Keep `pom.xml`, `package.json`, both package-lock root versions, the launchers, OpenAPI version, and user-facing examples consistent. The release identity gate enforces the build/package/launcher versions. Flyway migrations are immutable; never rewrite an already distributed migration to make a release pass.

From a clean checkout of the reference repository:

```sh
git switch main
git pull --ff-only
git status --short
git tag -a v1.1.0 -m "Orbit 1.1.0"
git push origin v1.1.0
```

The commands are the same in PowerShell. Confirm a clean worktree before tagging; the tag publishes only committed source. The release workflow triggers on `v*` tags but accepts only an exact stable `vMAJOR.MINOR.PATCH` matching the project version. It resolves the tag from a trusted main checkout, requires main ancestry, and validates again immediately before publishing. Local-data paths, generated runtime artifacts, and non-example environment files are forbidden in the source archive.

Manual release dispatch is available for an **existing tag at the current main tip**:

```sh
gh workflow run release.yml --ref main -f tag=v1.1.0
gh run list --workflow release.yml
```

The tag commit must equal `GITHUB_SHA`. This keeps GitHub's default signed OIDC build provenance aligned with the actual checked-out source; dispatching an older ancestor from a newer main would sign the wrong source identity and is rejected. When main has moved, inspect or rerun the original tag-triggered workflow instead. Exact-tag validation is never bypassed.

Verification has read-only repository permissions. The separate publisher gains repository contents, package, attestation, and OIDC write permissions only after all verification gates succeed. Official actions are pinned to verified release commits and maintained through Dependabot. Publication uses the workflow's short-lived `GITHUB_TOKEN`; no long-lived signing key or registry password is stored in the repository.

## Published files and image identity

The workflow loads the exact tested image artifact, checks its config digest, and promotes it without rebuilding. Intended GHCR tags are:

```text
ghcr.io/jon-jc/orbit:1.1.0
ghcr.io/jon-jc/orbit:sha-<full-40-character-source-commit>
```

It refuses existing version/commit tags. Authenticated registry checks treat only a definite 404 as absence; authorization and network errors fail closed. Both tags must resolve to the same registry digest after publication. Tags are treated as immutable by this workflow, but a registry administrator can still change them; deploy by the digest recorded in the manifest. The published image targets `linux/amd64`, not a claimed multi-architecture matrix.

The release is created as a draft with all assets attached before publication:

| Asset | Purpose |
| --- | --- |
| `orbit-1.1.0.jar` | Executable browser/API application; requires Java 17 or later. |
| `orbit-1.1.0-source.zip` | `git archive` of the exact tracked source, with a versioned root directory. |
| `orbit-1.1.0.sbom.json` | CycloneDX runtime inventory for the tested image. |
| `orbit-1.1.0.vulnerabilities.json` | Runtime Trivy gate report for that image. |
| `release.json` | Machine-readable version, commit, build URL, image digest/platform, gate/test totals, and primary asset hashes. |
| `*.provenance.json` | GitHub OIDC/Sigstore bundles for files, image build provenance, and image SBOM. |
| `SHA256SUMS` | SHA-256 of every final downloadable asset and provenance bundle; excludes itself. |

Only selected public artifacts enter the release. Container archives used for promotion, synthetic database rows, test traces, mail messages, cookie files, environment files, and internal work directories are not release assets. The source ZIP contains tracked repository files only; `.env.example` is an intentional configuration template.

Enable immutable GitHub releases and appropriate release-tag rules in repository settings when operating this workflow. With immutable releases enabled, publication also locks release assets and the tag; the workflow's draft-first sequence supports that behavior. It does not silently enable organization or repository policies. See [GitHub's immutable release guidance](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases).

## Download and verify

Download all assets into a new directory:

```sh
mkdir orbit-1.1.0-release
gh release download v1.1.0 --repo jon-jc/Orbit --dir orbit-1.1.0-release
cd orbit-1.1.0-release
sha256sum -c SHA256SUMS
gh attestation verify release.json --repo jon-jc/Orbit --signer-workflow jon-jc/Orbit/.github/workflows/release.yml
COMMIT=$(python3 -c 'import json; print(json.load(open("release.json"))["commit"])')
gh attestation verify orbit-1.1.0.jar --repo jon-jc/Orbit --signer-workflow jon-jc/Orbit/.github/workflows/release.yml --source-digest "$COMMIT"
```

PowerShell checksum verification:

```powershell
Get-Content SHA256SUMS | ForEach-Object {
  $hash, $name = $_ -split '  ', 2
  if ((Get-FileHash -Algorithm SHA256 -LiteralPath $name).Hash.ToLowerInvariant() -ne $hash) {
    throw "Checksum mismatch: $name"
  }
}
gh attestation verify release.json --repo jon-jc/Orbit --signer-workflow jon-jc/Orbit/.github/workflows/release.yml
$commit = (Get-Content -Raw release.json | ConvertFrom-Json).commit
gh attestation verify orbit-1.1.0.jar --repo jon-jc/Orbit --signer-workflow jon-jc/Orbit/.github/workflows/release.yml --source-digest $commit
```

Compare `release.json` with the expected repository/version/source commit and intended release workflow. Checksums detect changed bytes; signed attestations establish which workflow produced them. Neither proves that the application is free of defects. GitHub provides [attestation verification instructions](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).

For the image, read the actual digest instead of copying a placeholder:

```sh
IMAGE=$(python3 -c 'import json; d=json.load(open("release.json")); print(d["image"]["repository"]+"@"+d["image"]["digest"])')
COMMIT=$(python3 -c 'import json; print(json.load(open("release.json"))["commit"])')
docker pull "$IMAGE"
gh attestation verify "oci://$IMAGE" --repo jon-jc/Orbit --signer-workflow jon-jc/Orbit/.github/workflows/release.yml --source-digest "$COMMIT"
```

New GHCR packages may be private. Do not assume anonymous image access because the repository or GitHub Release is public. An operator with package read access can authenticate with `gh auth token | docker login ghcr.io -u YOUR_ACCOUNT --password-stdin`, subject to the token's package permissions. The package owner must deliberately configure visibility and verify an anonymous pull before advertising public registry access.

## Consume the release in production

The JAR is runnable directly with the environment described in the main README. With no profile override it starts local development behavior; use `SPRING_PROFILES_ACTIVE=prod` and all mandatory PostgreSQL/HTTPS/SMTP/key settings for production. Java being able to start the JAR is not a deployment readiness check.

To use the released image with the reference Compose deployment, add its verified digest to your protected `.env`:

```text
ORBIT_IMAGE=ghcr.io/jon-jc/orbit@sha256:<digest-from-release.json>
```

Then pull and replace only the application, keeping the database volume:

```sh
docker compose -f compose.yaml -f compose.release.yaml pull app
docker compose -f compose.yaml -f compose.release.yaml up -d --no-build
```

Take and verify a backup before applying migrations. Confirm readiness, public TLS, SMTP delivery, account sign-in, and real workspace changes after replacement. Do not use `down -v` for an update. The override supplies an image; `--no-build` prevents the base Compose source-build setting from building a different artifact. Protect the SMTP encryption key independently from the database backup.

## Failures and rollback

The workflow refuses release/tag/image replacement. A failed verification publishes no image or release. A failure after registry promotion can leave versioned image tags or an incomplete draft; publication across registry, attestations, and GitHub Release is not atomic. Inspect the failed run and the actual digest/assets before taking corrective action. This workflow deliberately does not delete or overwrite an incomplete distribution automatically. Use a new patch version for corrected binaries; do not move a released tag. A draft-only cleanup is an explicit operator action, not a hidden retry behavior.

For operational rollback, select a previously verified image digest only if its code can read the current database schema. A Flyway migration is not automatically undone by changing images. If backward compatibility is absent, use the separately tested restore procedure into new isolated resources and plan the promotion; account sessions are purged by default by the recovery scripts. Record the incident, recovery point, responsible operator, and follow-up migration or patch version.
