#!/usr/bin/env python3
"""Create public release files from verified inputs and the exact tracked source."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from datetime import datetime, timezone


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


version, tag, commit = (os.environ[key] for key in ("RELEASE_VERSION", "RELEASE_TAG", "RELEASE_COMMIT"))
repository, image, image_digest = (os.environ[key] for key in ("GITHUB_REPOSITORY", "IMAGE_NAME", "IMAGE_DIGEST"))
if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version) or tag != "v" + version:
    raise ValueError("Release version/tag mismatch.")
if not re.fullmatch(r"[0-9a-f]{40}", commit) or not re.fullmatch(r"sha256:[0-9a-f]{64}", image_digest):
    raise ValueError("Missing valid source commit or registry digest.")
if Path("verification/version.txt").read_text().strip() != version:
    raise ValueError("Verified build version differs from the release.")
checks = json.loads(Path("verification/checks.json").read_text())
report = json.loads(Path("verification/runtime-vulnerabilities.json").read_text())
findings = [finding for result in report.get("Results", []) for finding in result.get("Vulnerabilities", []) if finding["Severity"] in {"HIGH", "CRITICAL"}]
if findings:
    raise ValueError("Verified runtime report contains selected HIGH/CRITICAL findings.")
sbom = json.loads(Path("verification/runtime.sbom.json").read_text())
dist = Path("dist")
dist.mkdir(exist_ok=False)
prefix = "orbit-" + version
shutil.copyfile("verification/image-context/orbit.jar", dist / (prefix + ".jar"))
shutil.copyfile("verification/runtime.sbom.json", dist / (prefix + ".sbom.json"))
shutil.copyfile("verification/runtime-vulnerabilities.json", dist / (prefix + ".vulnerabilities.json"))
source = dist / (prefix + "-source.zip")
subprocess.run(["git", "archive", "--format=zip", "--prefix=" + prefix + "/", "--output=" + str(source), commit], check=True)
files = {path.name: {"sha256": digest(path), "bytes": path.stat().st_size} for path in sorted(dist.iterdir())}
manifest = {
    "schemaVersion": 1,
    "product": "Orbit", "version": version, "tag": tag, "commit": commit,
    "sourceRepository": "https://github.com/" + repository,
    "builtAt": datetime.now(timezone.utc).isoformat(),
    "buildRun": f"https://github.com/{repository}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "image": {"repository": image, "tags": [version, "sha-" + commit], "digest": image_digest, "platform": "linux/amd64", "configDigest": Path("verification/image-id.txt").read_text().strip()},
    "security": {"scanner": "Trivy", "version": "0.74.0", "gateSeverities": ["HIGH", "CRITICAL"], "ignoreUnfixed": False, "selectedFindings": 0, "sbomFormat": "CycloneDX", "components": len(sbom.get("components", [])), "severityPolicy": "Distribution/vendor severity where available. Passing the gate does not mean zero CVEs."},
    "checks": checks,
    "assets": files,
}
(dist / "release.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
notes = f"""Orbit {version} is a versioned release of the collaborative Java 17/Spring Boot product.

This release adds private saved views, atomic bulk task updates, and improvements to task workflow/navigation. HTTP content negotiation now returns 415 for unsupported request media, 406 for unacceptable responses, and 405 with Allow for unsupported methods.

Verified commit: `{commit}`
Production image: `{image}@{image_digest}` (`linux/amd64`)
Version tag: `{image}:{version}`
Commit tag: `{image}:sha-{commit}`

The release ran {checks['h2']['tests'] + checks['postgresql']['tests']} backend executions and {checks['browser']['expected']} browser scenarios without failures, errors, skips, or retries, plus OpenAPI validation, isolated PostgreSQL recovery, and production-profile SMTP/session/persistence/container checks. Trivy's selected HIGH/CRITICAL gate passed without ignoring unfixed findings; lower severity and alternate-source ratings may remain. The attached SBOM/report describe this exact image.

Download the executable JAR, tracked source archive, SBOM, vulnerability report, release.json, provenance bundles, and SHA256SUMS below. Verify checksums and GitHub OIDC attestations before use; see docs/RELEASE.md in the source archive. No production credentials or data are included. Publishing assets and an image does not deploy a public application. Production needs PostgreSQL, HTTPS, SMTP configuration, and protected secrets as described in README.md and docs/RUNBOOK.md.
"""
Path("verification/release-notes.md").write_text(notes, encoding="utf-8")
print("Versioned public release assets and manifest prepared from verified inputs only.")
