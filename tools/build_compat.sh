#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd "$(dirname "$0")/.." && pwd)"
cache_root="${TV_BUILD_CACHE:-/workspace/tooling}"
media_root="$cache_root/media-git"
compat_root="$cache_root/tv-compat-556"
media_commit=3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle}"
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
: "${JAVA_HOME:?Point JAVA_HOME to the installed JDK 21}"
# Preserve the cloud proxy and system certificate trust. Local machines can omit these JVM flags.
if [[ -n "${HTTPS_PROXY:-}" && "$HTTPS_PROXY" == http://proxy:8080 ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Duser.home=$cache_root"
fi
mkdir -p "$cache_root"
if [[ ! -d "$media_root/.git" ]]; then
    git clone --filter=blob:none --no-checkout --depth=1 --single-branch --branch release-1.11.0-fongmi https://github.com/FongMi/media.git "$media_root"
fi
if [[ "$(git -C "$media_root" rev-parse HEAD)" != "$media_commit" ]]; then
    git -C "$media_root" fetch --depth=1 origin "$media_commit"
fi
git -C "$media_root" sparse-checkout init --cone
modules=(common container database datasource datasource_okhttp datasource_rtmp decoder exoplayer exoplayer_dash exoplayer_hls exoplayer_rtsp exoplayer_smoothstreaming extractor ui session decoder_ffmpeg mpvplayer ui_danmaku)
paths=(build-logic build-logic-settings buildSrc gradle)
for module in "${modules[@]}"; do paths+=("libraries/$module/src/main"); done
git -C "$media_root" sparse-checkout set "${paths[@]}"
git -C "$media_root" checkout --detach "$media_commit"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$media_root/local.properties"
python3 - "$media_root" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1])
text=(root/'build-logic-settings/src/main/kotlin/androidx/media3/buildlogic/Media3Modules.kt').read_text()
for path in re.findall(r'Media3Module\(\s*"([^"]+)"',text):(root/path).mkdir(parents=True,exist_ok=True)
PY
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;29.0.14206865' 'cmake;3.31.6'
UV_PYTHON_INSTALL_DIR="$cache_root/python" uv python install 3.10.22
build_python="$cache_root/python/cpython-3.10.22-linux-x86_64-gnu/bin/python3.10"
tasks=()
for module in "${modules[@]}"; do tasks+=(":lib-${module//_/-}:assembleRelease"); done
(cd "$media_root" && bash gradlew --no-configuration-cache --max-workers=2 "${tasks[@]}")
python3 "$source_root/tools/prepare_compat.py" --output "$compat_root"
python3 - "$media_root" "$compat_root" <<'PY'
from pathlib import Path
import shutil,sys
root=Path(sys.argv[1]); target=Path(sys.argv[2])/'app/libs'
artifacts=list(root.rglob('outputs/aar/*-release.aar'))
assert len(artifacts)>=18,'Expected public player AARs were not produced'
for path in artifacts:shutil.copy2(path,target/path.name)
PY
(cd "$compat_root" && bash gradlew --no-configuration-cache --max-workers=2 "-PcompatBuildPython=$build_python" :app:assembleLeanbackArm64_v8aDebug :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleMobileArmeabi_v7aDebug :app:testLeanbackArmeabi_v7aDebugUnitTest :app:testMobileArmeabi_v7aDebugUnitTest)
python3 "$source_root/tools/verify_compat.py" --project "$compat_root" --output "$source_root/artifacts"
