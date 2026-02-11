#!/bin/sh

# This is a generated gradle wrapper script
# It will download and use gradle to build the project

GRADLE_VERSION=8.10.2
GRADLE_ZIP="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/${GRADLE_ZIP}"
GRADLE_HOME="/tmp/gradle-${GRADLE_VERSION}"

# Download gradle if not already downloaded
if [ ! -d "$GRADLE_HOME" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -L "$GRADLE_URL" -o "/tmp/${GRADLE_ZIP}"
    unzip -q "/tmp/${GRADLE_ZIP}" -d /tmp
    rm "/tmp/${GRADLE_ZIP}"
fi

# Run gradle
"$GRADLE_HOME/bin/gradle" "$@"