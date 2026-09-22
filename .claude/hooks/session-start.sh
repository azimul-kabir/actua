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
# downloading one via api.foojay.io, which some environments block. We
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

echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
echo "export PATH=\"$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"

# --- Register the JDK with Gradle -----------------------------------------
# org.gradle.java.installations.paths tells Gradle where to look for JDKs
# for toolchain / Daemon JVM resolution, bypassing auto-detection and the
# foojay download fallback entirely. auto-download=false makes Gradle fail
# fast with a clear error instead of hanging on a blocked foojay request
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

# --- Android SDK -----------------------------------------------------------
# Requires dl.google.com reachable (Android SDK components and the
# google() Maven repo both come from there). We probe it first so the
# hook degrades gracefully -- and tells the truth -- in environments
# where that host is still blocked.
echo
echo "=== Android SDK ==="

DL_GOOGLE_OK=false
if curl -fsSL --max-time 10 -o /dev/null "https://dl.google.com/android/cli/latest/linux_x86_64/install.sh"; then
  DL_GOOGLE_OK=true
fi

if [ "$DL_GOOGLE_OK" = true ]; then
  ANDROID_CLI_BIN="$HOME/.local/bin/android"
  if [ ! -x "$ANDROID_CLI_BIN" ]; then
    echo "Installing Android CLI..."
    curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
  fi
  export PATH="$HOME/.local/bin:$PATH"

  ANDROID_HOME="$HOME/Android/Sdk"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"

  if [ ! -d "$ANDROID_HOME/platform-tools" ]; then
    echo "Installing Android platform-tools..."
    android sdk install platform-tools
  fi

  # Accept SDK licenses non-interactively so AGP can auto-download whatever
  # compile-SDK platform/build-tools versions the project needs at build
  # time (compileSdk 37 tracks a fast-moving platform release train, so we
  # deliberately don't hardcode a platform package name here -- AGP fetches
  # exactly what it needs from google() once licenses are accepted).
  yes | android sdk install --licenses >/dev/null 2>&1 || true

  echo "export PATH=\"$HOME/.local/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"

  if [ ! -f "$CLAUDE_PROJECT_DIR/local.properties" ] || ! grep -q "^sdk.dir=" "$CLAUDE_PROJECT_DIR/local.properties" 2>/dev/null; then
    echo "sdk.dir=$ANDROID_HOME" >> "$CLAUDE_PROJECT_DIR/local.properties"
  fi

  echo "ANDROID_HOME=$ANDROID_HOME"
  echo "Android SDK ready. './gradlew assembleDebug' etc. can run in this session."
else
  echo "dl.google.com is not reachable from this sandbox, so the Android SDK"
  echo "and AGP/androidx (both served from Google's Maven repo) can't be"
  echo "installed here. Allowlist dl.google.com in this environment's network"
  echo "policy to enable full './gradlew' builds in Claude Code web sessions;"
  echo "until then, rely on CI to confirm the build compiles."
fi

echo
echo "=== Actua cloud environment ready ==="
