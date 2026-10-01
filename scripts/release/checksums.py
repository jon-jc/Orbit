#!/usr/bin/env python3
"""Create checksums without accidentally hashing a stale checksum file itself."""
import hashlib
from pathlib import Path

dist = Path("dist")
lines = []
for path in sorted(dist.iterdir()):
    if path.name == "SHA256SUMS":
        continue
    if not path.is_file() or path.is_symlink():
        raise ValueError("Release assets must be ordinary files.")
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    lines.append(f"{digest}  {path.name}\n")
if not lines:
    raise ValueError("No release assets to checksum.")
(dist / "SHA256SUMS").write_text("".join(lines), encoding="utf-8")
print("Final release assets and provenance bundles checksummed.")
