#!/bin/bash
# Offline build used for TopClient releases: javac against the 26.2 game + Fabric jars, then resources.
# Usage: tools/build.sh <folder with minecraft-26.2.jar, fabric jars, libraries and lombok.jar> <output jar> <version>
set -e
P=$(cd "$(dirname "$0")/.." && pwd); CPDIR=$1; OUT=$2; VER=$3
W=$(mktemp -d); mkdir -p $W/classes
CP="$(ls $CPDIR/*.jar | tr '\n' ':')"
find $P/src/main/java -name '*.java' > $W/sources.txt
javac -nowarn -encoding UTF-8 --release 21 -proc:full -cp "$CP" -processorpath "$CP" -d $W/classes @$W/sources.txt
cp -r $P/src/main/resources/. $W/classes/
sed -i "s/\${version}/$VER/; s/\${loader_version}/0.20.1/; s/\${minecraft_version}/26.2/" $W/classes/fabric.mod.json
cp $P/LICENSE $W/classes/LICENSE_TopClient
rm -f $OUT; (cd $W/classes && zip -q -r -X $OUT .); rm -rf $W; echo "built $OUT"
