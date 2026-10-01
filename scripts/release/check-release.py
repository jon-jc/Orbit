#!/usr/bin/env python3
"""Refuse duplicate releases; API authorization/transient failures never mean absent."""
import json
import os
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

tag = sys.argv[1]
url = f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/releases/tags/{quote(tag, safe='')}"
headers = {"Authorization": "Bearer " + os.environ["GH_TOKEN"], "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
try:
    with urlopen(Request(url, headers=headers), timeout=30) as response:
        json.load(response)
    sys.exit("Refusing to overwrite an existing release, including an incomplete draft; investigate or use a new version.")
except HTTPError as error:
    if error.code != 404:
        sys.exit(f"Release existence check failed with HTTP {error.code}; no publication attempted.")
print("No existing release uses this exact tag.")
