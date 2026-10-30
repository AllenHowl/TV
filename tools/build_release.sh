#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK with platform 37.0 and build-tools 37.0.0}"
cache="$project_root/.release-cache"
mkdir -p "$cache"
if ! (cd app/libs && sha256sum --quiet -c "$project_root/tools/player-libs.sha256"); then
    archive="$cache/TV-Sources-player-libs.zip"
    if [[ -n "${TV_PLAYER_LIBS_ZIP:-}" ]]; then
        cp "$TV_PLAYER_LIBS_ZIP" "$archive"
    else
        curl -fsSL --connect-timeout 15 --max-time 300 'https://github.com/AllenHowl/TV/releases/download/v5.5.6-sources-compat.1/TV-Sources-player-libs.zip' -o "$archive"
    fi
    (cd "$cache" && sha256sum -c "$project_root/tools/player-libs-archive.sha256")
    python3 - "$archive" "$project_root/app/libs" <<'PY'
import zipfile,sys
from pathlib import Path
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in archive.namelist():
        if '/' in name or '\\' in name: raise RuntimeError('Invalid dependency archive path')
    archive.extractall(Path(sys.argv[2]))
PY
    (cd app/libs && sha256sum --quiet -c "$project_root/tools/player-libs.sha256")
fi
build_python="${TV_BUILD_PYTHON:-python3.10}"
if [[ ! -f local.properties ]]; then
    keytool -genkeypair -keystore "$cache/debug.jks" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
    printf 'sdk.dir=%s\nstoreFile=%s\nkeyAlias=androiddebugkey\nstorePassword=android\n' "$ANDROID_HOME" "$cache/debug.jks" > local.properties
fi
bash gradlew --no-configuration-cache --max-workers=2 "-PcompatBuildPython=$build_python" :app:assembleLeanbackArm64_v8aDebug :app:assembleMobileArm64_v8aDebug :app:testLeanbackArm64_v8aDebugUnitTest
