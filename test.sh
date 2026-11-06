#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
: "${MC_DIR:?Set MC_DIR to the Minecraft game directory}"
CP="build/classes:$MC_DIR/versions/26.3-Fabric/26.3-Fabric.jar:$(find "$MC_DIR/libraries" build/deps/META-INF/jars -name '*.jar' -printf '%p:' | tr -d '\n')"
# Stubs share Minecraft packages; strip JAR signatures from the test-only copy.
python3 - "$MC_DIR/versions/26.3-Fabric/26.3-Fabric.jar" build/test-minecraft.jar <<'PYTEST'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'w') as target:
    for entry in source.infolist():
        upper=entry.filename.upper()
        if upper.startswith('META-INF/') and upper.endswith(('.SF','.RSA','.DSA','.EC')):
            continue
        target.writestr(entry, source.read(entry))
PYTEST
mkdir -p build/test-classes
javac --release 25 -encoding UTF-8 -cp "$CP" -d build/test-classes $(find tests -name '*.java')
java -cp "build/test-classes:build/classes:build/test-minecraft.jar:$(find "$MC_DIR/libraries" build/deps/META-INF/jars -name '*.jar' -printf '%p:' | tr -d '\n')" dev.faketbm.SchedulerTest
