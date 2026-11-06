#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${MC_DIR:?Set MC_DIR to the Minecraft game directory}"
MC_JAR="$MC_DIR/versions/26.3-Fabric/26.3-Fabric.jar"
API_JAR="$MC_DIR/versions/26.3-Fabric/mods/fabric-api-0.160.5+26.3.jar"
rm -rf build
mkdir -p build/deps build/classes build/libs
(cd build/deps && jar xf "$API_JAR" META-INF/jars)
CP="$MC_JAR:$(find "$MC_DIR/libraries" build/deps/META-INF/jars -name '*.jar' -printf '%p:' | tr -d '\n')"
javac --release 25 -encoding UTF-8 -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
cp LICENSE build/classes/LICENSE_fake-player-tbm
jar --create --file build/libs/fake-player-tbm-1.5.0.jar -C build/classes .
