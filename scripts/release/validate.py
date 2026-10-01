#!/usr/bin/env python3
"""Fail closed before release builds or publishing; no Maven execution required."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def validate(tag, resolve=False):
    if not re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag):
        raise ValueError("Use an exact stable tag such as v1.1.0; ranges and prereleases are not accepted.")
    commit = git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")
    # Default GitHub OIDC provenance uses the triggering workflow SHA, not a
    # later checkout. Manual dispatch must therefore tag the current main tip.
    workflow_commit = os.environ.get("GITHUB_SHA")
    if workflow_commit and workflow_commit != commit:
        raise ValueError("Release commit must equal the triggering workflow SHA. Manual dispatch requires a tag at the current main tip.")
    if not resolve and git("rev-parse", "HEAD") != commit:
        raise ValueError("The checked-out commit does not match the requested tag.")
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"], check=True)
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = ET.fromstring(git("show", f"{commit}:pom.xml")).findtext("m:version", namespaces=ns)
    if version != tag[1:]:
        raise ValueError("The exact release tag must match the project POM version.")
    package = json.loads(git("show", f"{commit}:package.json"))
    lock = json.loads(git("show", f"{commit}:package-lock.json"))
    if package["version"] != version or lock["version"] != version or lock["packages"][""]["version"] != version:
        raise ValueError("POM, npm metadata, and release tag versions must agree.")
    for launcher in ("start.cmd", "start.sh"):
        references = set(re.findall(r"orbit-[0-9]+\.[0-9]+\.[0-9]+\.jar", git("show", f"{commit}:{launcher}")))
        if references != {f"orbit-{version}.jar"}:
            raise ValueError("Launchers must reference the release-versioned executable.")
    # git archive includes tracked files only. Refuse known local-data paths too.
    forbidden = {"data", "work", "backups", "target", "node_modules", "verification", "dist", ".codex", ".agents"}
    for name in git("ls-tree", "-r", "--name-only", commit).splitlines():
        path = PurePosixPath(name)
        if path.parts[0] in forbidden or path.name in {"AGENTS.md", "CONTRACT.md"}:
            raise ValueError(f"Release source contains a forbidden local-data path: {name}")
        if (path.name == ".env" or path.name.startswith(".env.")) and path.name != ".env.example":
            raise ValueError("Release source contains a non-example environment file.")
        if path.suffix in {".dump", ".log", ".jar"}:
            raise ValueError("Release source contains generated or private runtime artifacts.")
    return {"tag": tag, "version": version, "commit": commit}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("tag")
    parser.add_argument("--resolve", action="store_true", help="Resolve a tag from the trusted main checkout before checking it out.")
    args = parser.parse_args()
    values = validate(args.tag, args.resolve)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8") as stream:
            for key, value in values.items():
                stream.write(f"{key}={value}\n")
    print(f"Release identity validated: {values['tag']} at {values['commit']} on main ancestry.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"Release identity check failed: {error}", file=sys.stderr)
        sys.exit(1)
