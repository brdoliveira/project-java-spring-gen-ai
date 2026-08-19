#!/usr/bin/env sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES_FILE="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -r "$PROPERTIES_FILE" ]; then
  echo "Cannot read $PROPERTIES_FILE" >&2
  exit 1
fi

DISTRIBUTION_URL=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | head -n 1)
if [ -z "$DISTRIBUTION_URL" ]; then
  echo "distributionUrl is required in $PROPERTIES_FILE" >&2
  exit 1
fi

MAVEN_USER_HOME=${MAVEN_USER_HOME:-"$HOME/.m2"}
DIST_NAME=$(basename "${DISTRIBUTION_URL%.zip}")
MAVEN_DIR_NAME=${DIST_NAME%-bin}
DIST_DIR="$MAVEN_USER_HOME/wrapper/dists/$DIST_NAME"
MAVEN_HOME="$DIST_DIR/$MAVEN_DIR_NAME"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$DIST_DIR"
  ARCHIVE="$DIST_DIR/$DIST_NAME-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$DISTRIBUTION_URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$DISTRIBUTION_URL" -O "$ARCHIVE"
    else
      echo "Maven Wrapper requires curl or wget to download Maven." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ARCHIVE" -d "$DIST_DIR"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
