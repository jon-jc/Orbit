#!/usr/bin/env python3
"""Authenticated GHCR checks; only a definite HTTP 404 means a tag is absent."""
import argparse
import base64
import json
import os
import re
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def manifest(repository, tag):
    if not re.fullmatch(r"[a-z0-9_.-]+/[a-z0-9_.-]+", repository):
        raise ValueError("Expected the lower-case owner/repository GHCR path.")
    if not re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}", tag):
        raise ValueError("Invalid registry tag.")
    credential = base64.b64encode(f"{os.environ['GITHUB_ACTOR']}:{os.environ['GH_TOKEN']}".encode()).decode()
    token_url = "https://ghcr.io/token?" + urlencode({"service": "ghcr.io", "scope": f"repository:{repository}:pull,push"})
    request = Request(token_url, headers={"Authorization": f"Basic {credential}"})
    with urlopen(request, timeout=30) as response:
        token = json.load(response)["token"]
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.oci.image.manifest.v1+json, application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.list.v2+json"}
    try:
        with urlopen(Request(f"https://ghcr.io/v2/{repository}/manifests/{tag}", headers=headers, method="HEAD"), timeout=30) as response:
            digest = response.headers.get("Docker-Content-Digest", "")
            if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
                raise ValueError("Registry returned no valid manifest digest.")
            return digest
    except HTTPError as error:
        if error.code == 404:
            return None
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["absent", "digest"])
    parser.add_argument("repository")
    parser.add_argument("tags", nargs="+")
    args = parser.parse_args()
    digests = []
    for tag in args.tags:
        digest = manifest(args.repository, tag)
        if args.mode == "absent" and digest:
            raise ValueError(f"Refusing to overwrite existing image tag {tag}.")
        if args.mode == "digest" and not digest:
            raise ValueError(f"Published image tag {tag} is missing.")
        digests.append(digest)
    if args.mode == "digest":
        if len(set(digests)) != 1:
            raise ValueError("Published version and commit tags do not identify the same image.")
        print(digests[0])
    else:
        print("All intended image tags are absent; publication may proceed.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, HTTPError, KeyError) as error:
        # Do not print request headers, tokens, or HTTP response bodies.
        print(f"Registry identity check failed: {error}", file=sys.stderr)
        sys.exit(1)
