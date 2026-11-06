#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
: "${MC_DIR:?Set MC_DIR to the Minecraft game directory}"
CP="build/classes:$MC_DIR/versions/26.3-Fabric/26.3-Fabric.jar:$(find "$MC_DIR/libraries" build/deps/META-INF/jars -name '*.jar' -printf '%p:' | tr -d '\n')"
mkdir -p build/test-classes
javac --release 25 -encoding UTF-8 -cp "$CP" -d build/test-classes $(find tests -name '*.java')
java -cp "build/test-classes:$CP" dev.faketbm.SchedulerTest
