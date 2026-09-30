#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f target/orbit-1.0.0.jar ]; then
  sh ./mvnw -B -ntp verify
fi
if [ -n "${JAVA_HOME:-}" ]; then
  ORBIT_JAVA="$JAVA_HOME/bin/java"
else
  ORBIT_JAVA=java
fi
exec "$ORBIT_JAVA" -jar target/orbit-1.0.0.jar --spring.profiles.active=local --server.address=127.0.0.1
