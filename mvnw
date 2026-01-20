#!/bin/sh
# Maven Wrapper script for F1 Bets project

set -e

MAVEN_HOME="${MAVEN_HOME:-$HOME/.m2}"
MAVEN_VERSION="3.9.6"
MAVEN_DIR="$MAVEN_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_DIR/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    mkdir -p "$MAVEN_HOME/wrapper/dists"
    curl -sL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz" | tar xz -C "$MAVEN_HOME/wrapper/dists"
fi

exec "$MAVEN_BIN" "$@"
