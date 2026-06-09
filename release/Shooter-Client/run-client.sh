#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
JFX_MODS=""
for m in "$DIR"/javafx-sdk/*.jar; do
    JFX_MODS="$JFX_MODS:$m"
done
CLASSPATH=""
for jar in "$DIR"/lib/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done
java \
    --module-path "${JFX_MODS:1}" \
    --add-modules javafx.controls,javafx.graphics,javafx.media \
    -cp "${CLASSPATH:1}" \
    com.aa.client.Main "$@"
