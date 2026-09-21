#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo "=== Setting up Actua cloud environment ==="

# --- JDK -----------------------------------------------------------------
# Actua pins its Gradle Daemon JVM via gradle/gradle-daemon-jvm.properties
# (toolchainVersion=25). If Gradle can't find a matching JDK through its own
# discovery (JAVA_HOME, PATH, SDKMAN, IntelliJ .jdks, etc.) it falls back to
# downloading one via api.foojay.io, which is blocked in this sandbox. We
# install the JDK ourselves and explicitly register it with Gradle so it
# never needs to reach foojay.

JDK_DIR="$HOME/.local/jdk-25"
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  mkdir -p "$JDK_DIR"
  echo "Downloading Temurin JDK 25..."
  curl -fL --retry 3 "$JDK_URL" -o /tmp/temurin25.tar.gz
  echo "Installing Temurin JDK 25..."
  tar -xzf /tmp/temurin25.tar.gz -C "$JDK_DIR" --strip-components=1
  rm -f /tmp/temurin25.tar.gz
else
  echo "Temurin JDK 25 already installed, skipping download."
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

# Persist for the rest of this session.
echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
echo "export PATH=\"$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"

# --- Register the JDK with Gradle -----------------------------------------
# org.gradle.java.installations.paths tells Gradle where to look for JDKs
# for toolchain / Daemon JVM resolution, bypassing auto-detection and the
# foojay download fallback entirely. auto-download=false makes Gradle fail
# fast with a clear error instead of hanging on the blocked foojay request
# if the pinned version ever drifts.
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
mkdir -p "$GRADLE_USER_HOME"
if ! grep -q "org.gradle.java.installations.paths" "$GRADLE_USER_HOME/gradle.properties" 2>/dev/null; then
  cat >> "$GRADLE_USER_HOME/gradle.properties" <<EOF
org.gradle.java.installations.paths=$JDK_DIR
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.auto-download=false
EOF
fi

echo
echo "=== Toolchain ==="
java -version
javac -version
git --version
echo
echo "JAVA_HOME=$JAVA_HOME"

echo
echo "=== Android SDK note ==="
echo "Actua resolves AGP/androidx from Google's Maven repo and fetches its"
echo "Android SDK platform from dl.google.com (see"
echo ".github/actions/setup-android-toolchain/action.yml). If dl.google.com"
echo "is blocked by this environment's network policy, a full"
echo "'./gradlew build' cannot succeed here no matter how the JDK is set"
echo "up -- allowlist dl.google.com in the environment's network settings"
echo "for real builds, and rely on CI to confirm otherwise."

echo
echo "=== Actua cloud environment ready ==="
