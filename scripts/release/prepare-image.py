#!/usr/bin/env python3
"""Use precisely the POM-versioned executable, never an arbitrary target glob."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
pom = ET.parse("pom.xml").getroot()
version = pom.findtext("m:version", namespaces=ns)
artifact = pom.findtext("m:artifactId", namespaces=ns)
jar = Path("target") / f"{artifact}-{version}.jar"
with ZipFile(jar) as archive:
    if "BOOT-INF/classes/com/orbit/OrbitApplication.class" not in archive.namelist():
        raise ValueError("Expected a repackaged Orbit executable JAR.")
context = Path("verification/image-context")
context.mkdir(parents=True, exist_ok=True)
shutil.copyfile(jar, context / "orbit.jar")
Path("verification/version.txt").write_text(version + "\n", encoding="utf-8")
print("The tested POM-versioned executable is ready for runtime packaging.")
