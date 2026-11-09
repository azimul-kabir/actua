#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo "=== Setting up Actua cloud environment ==="

# Append a line to the session env file when Claude Code provides one.
persist_env() {
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    printf '%s\n' "$1" >> "$CLAUDE_ENV_FILE"
  fi
}

# Set key=value in a properties file, replacing any existing value.
set_property() {
  local file="$1" key="$2" value="$3"
  touch "$file"
  if grep -qE "^[[:space:]]*${key//./\\.}[[:space:]]*=" "$file"; then
    sed -i "s|^[[:space:]]*${key//./\\.}[[:space:]]*=.*|${key}=${value}|" "$file"
  else
    # Start on a fresh line if the file lacks a trailing newline.
    if [ -s "$file" ] && [ -n "$(tail -c1 "$file")" ]; then
      printf '\n' >> "$file"
    fi
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

# --- Git Identity --------------------------------------------------------
# Ensure commits authored in Claude Code cloud sessions match the repository
# owner's Git identity so GitHub does not inject an unwanted Co-authored-by
# trailer on squash merge.
GIT_NAME="Azimul Kabir Apu"
GIT_EMAIL="azimul.kabir123@gmail.com"
git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"

persist_env "export GIT_AUTHOR_NAME=\"$GIT_NAME\""
persist_env "export GIT_AUTHOR_EMAIL=\"$GIT_EMAIL\""
persist_env "export GIT_COMMITTER_NAME=\"$GIT_NAME\""
persist_env "export GIT_COMMITTER_EMAIL=\"$GIT_EMAIL\""

# --- JDK -----------------------------------------------------------------
# Actua pins its Gradle Daemon JVM via gradle/gradle-daemon-jvm.properties
# (toolchainVersion=25). If Gradle can't find a matching JDK through its own
# discovery it falls back to downloading one via api.foojay.io, which some
# environments block. We install the JDK ourselves and explicitly register it
# with Gradle so it never needs to reach foojay.

JDK_DIR="$HOME/.local/jdk-25"
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
# From the release's matching .sha256.txt. Update together with JDK_URL.
JDK_SHA256="dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e"

if [ -x "$JDK_DIR/bin/java" ] && [ -x "$JDK_DIR/bin/javac" ]; then
  echo "Temurin JDK 25 already installed, skipping download."
else
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  echo "Downloading Temurin JDK 25..."
  curl -fL --retry 3 "$JDK_URL" -o "$TMP_DIR/jdk.tar.gz"
  echo "$JDK_SHA256  $TMP_DIR/jdk.tar.gz" | sha256sum -c --quiet -
  echo "Installing Temurin JDK 25..."
  rm -rf "$JDK_DIR"
  mkdir -p "$JDK_DIR"
  tar -xzf "$TMP_DIR/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

if ! "$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '^javac 25\.'; then
  echo "Expected JDK 25 at $JDK_DIR but found: $("$JAVA_HOME/bin/javac" -version 2>&1)" >&2
  exit 1
fi

persist_env "export JAVA_HOME=\"$JDK_DIR\""
persist_env "export PATH=\"$JDK_DIR/bin:\$PATH\""

# --- Register the JDK with Gradle -----------------------------------------
# org.gradle.java.installations.paths tells Gradle where to look for JDKs
# for toolchain / Daemon JVM resolution. auto-detect=false and
# auto-download=false make Gradle use only that JDK and fail fast with a clear
# error instead of hanging on a blocked foojay request if the pinned version
# ever drifts.
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
mkdir -p "$GRADLE_USER_HOME"
GRADLE_PROPS="$GRADLE_USER_HOME/gradle.properties"
set_property "$GRADLE_PROPS" org.gradle.java.installations.paths "$JDK_DIR"
set_property "$GRADLE_PROPS" org.gradle.java.installations.auto-detect false
set_property "$GRADLE_PROPS" org.gradle.java.installations.auto-download false

echo
echo "=== Toolchain ==="
javac -version
git --version
echo
echo "JAVA_HOME=$JAVA_HOME"

# --- Android SDK -----------------------------------------------------------
# Requires dl.google.com reachable (Android SDK components and the google()
# Maven repo are both served from that host). We probe it first so the hook
# degrades gracefully -- and tells the truth -- where that host is blocked.
echo
echo "=== Android SDK ==="

if curl -fsSL --max-time 10 -o /dev/null "https://dl.google.com/android/cli/latest/linux_x86_64/install.sh"; then
  export PATH="$HOME/.local/bin:$PATH"
  if ! command -v android >/dev/null 2>&1; then
    echo "Installing Android CLI..."
    curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
    command -v android >/dev/null 2>&1 || {
      echo "Android CLI install finished but 'android' is not on PATH." >&2
      exit 1
    }
  fi

  ANDROID_HOME="$HOME/Android/Sdk"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"

  # Installing a package also writes the SDK license files, which lets AGP
  # download the compile SDK and build tools it needs at build time.
  if [ ! -x "$ANDROID_HOME/platform-tools/adb" ]; then
    echo "Installing Android platform-tools..."
    android sdk install platform-tools
  fi

  persist_env "export PATH=\"$HOME/.local/bin:\$PATH\""
  persist_env "export ANDROID_HOME=\"$ANDROID_HOME\""
  persist_env "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""

  LOCAL_PROPS="$CLAUDE_PROJECT_DIR/local.properties"
  if ! grep -qE '^[[:space:]]*sdk\.dir[[:space:]]*=' "$LOCAL_PROPS" 2>/dev/null; then
    set_property "$LOCAL_PROPS" sdk.dir "$ANDROID_HOME"
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
