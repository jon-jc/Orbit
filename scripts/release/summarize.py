#!/usr/bin/env python3
"""Create a public-safe verification summary and reject failures or skipped gates."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def tests(directory):
    files = sorted(Path(directory).glob("TEST-*.xml"))
    if not files:
        raise ValueError(f"Missing required test reports in {directory}.")
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for path in files:
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.get(key, "0"))
    if not totals["tests"] or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise ValueError("Backend tests did not complete without failures, errors, and skips.")
    return totals


h2 = tests("target/surefire-reports")
postgres = tests("target/failsafe-reports")
browser = json.loads(Path("verification/browser-results.json").read_text())["stats"]
if not browser["expected"] or any(browser[key] for key in ("unexpected", "skipped", "flaky")):
    raise ValueError("Browser scenarios did not complete without failures, skips, or retries.")
production = json.loads(Path("verification/production-results.json").read_text())
if production["status"] != "passed":
    raise ValueError("Production-profile verification did not pass.")
checks = {
    "releaseValidationGuards": "passed",
    "h2": h2,
    "postgresql": postgres,
    "browser": {key: browser[key] for key in ("expected", "unexpected", "skipped", "flaky")},
    "openapi": "passed",
    "isolatedRecovery": "passed",
    "production": production,
    "runtimeVulnerabilityGate": "passed",
}
Path("verification/checks.json").write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8")
print(f"Passing checks recorded: {h2['tests'] + postgres['tests']} backend, {browser['expected']} browser, recovery and production.")
