#!/usr/bin/env bash
set -euo pipefail

# Builds Sugarcane and deploys the jar to castled.codes/sugarcane/dl
# Usage: ./scripts/deploy-to-site.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
SITE_DIR="${SUGARCANE_SITE_DIR:-/mnt/storage/repos/CASTLEDCODEX/castled.codes}"
DL_DIR="${SITE_DIR}/sugarcane/dl"

cd "$REPO_DIR"

# Single source of truth, so the deployed jar never drifts from what we build
MC_VERSION=$(grep -E '^mcVersion=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$MC_VERSION" ]; then
    echo "!! Could not read mcVersion from gradle.properties" >&2
    exit 1
fi

BUILD_NUM=$(tr -d '[:space:]' < build.number)
if [ -z "$BUILD_NUM" ]; then
    echo "!! Could not read build.number" >&2
    exit 1
fi

JAR_NAME="sugarcane-${MC_VERSION}-${BUILD_NUM}.jar"
TARGET_DIR="${DL_DIR}/${MC_VERSION}/${BUILD_NUM}"

echo ":: Building Sugarcane ${MC_VERSION} build ${BUILD_NUM}"

git config user.name "Sugarcane Builder" 2>/dev/null || true
git config user.email "builder@sugarcane.internal" 2>/dev/null || true

echo ":: Applying patches..."
./gradlew applyPatches

echo ":: Building paperclip jar..."
BUILD_NUMBER="${BUILD_NUM}" ./gradlew createPaperclipJar

# settings.gradle.kts names the artifact "<mcVersion>.build.<num>-<channel>" whenever
# BUILD_NUMBER is set. Match that exactly rather than globbing: build/libs keeps stale
# jars from other versions and from BUILD_NUMBER-less runs (".local-SNAPSHOT") around,
# and picking the wrong one ships a mislabelled jar. Channel is wildcarded.
shopt -s nullglob
PAPERCLIP_JARS=(paper-server/build/libs/paper-paperclip-"${MC_VERSION}.build.${BUILD_NUM}"-*.jar)
shopt -u nullglob
if [ ${#PAPERCLIP_JARS[@]} -ne 1 ]; then
    echo "!! Expected exactly one paperclip jar for ${MC_VERSION} build ${BUILD_NUM}, found ${#PAPERCLIP_JARS[@]}:" >&2
    printf '   %s\n' "${PAPERCLIP_JARS[@]}" >&2
    exit 1
fi

echo ":: Deploying to ${TARGET_DIR}"
mkdir -p "${TARGET_DIR}"
cp "${PAPERCLIP_JARS[0]}" "${TARGET_DIR}/${JAR_NAME}"

SIZE=$(du -m "${TARGET_DIR}/${JAR_NAME}" | cut -f1)
SHA=$(sha256sum "${TARGET_DIR}/${JAR_NAME}" | cut -d' ' -f1)

# Insert/update this build in builds.json, which downloads.html reads to render
# the download table. Path layout must stay dl/<version>/<build>/<file>.
MANIFEST="${DL_DIR}/builds.json"
[ -f "${MANIFEST}" ] || echo '{"versions":{}}' > "${MANIFEST}"

MC_VERSION="${MC_VERSION}" BUILD_NUM="${BUILD_NUM}" JAR_NAME="${JAR_NAME}" \
SIZE="${SIZE}" SHA="${SHA}" MANIFEST="${MANIFEST}" python3 - <<'PY'
import json, os
from datetime import date

manifest = os.environ["MANIFEST"]
version, build = os.environ["MC_VERSION"], int(os.environ["BUILD_NUM"])

with open(manifest) as fh:
    data = json.load(fh)

versions = data.setdefault("versions", {})
entry = versions.setdefault(version, {"latest": build, "builds": []})

record = {
    "number": build,
    "file": os.environ["JAR_NAME"],
    "date": date.today().isoformat(),
    "size": f"{os.environ['SIZE']} MB",
    "sha256": os.environ["SHA"],
}

entry["builds"] = [b for b in entry["builds"] if b.get("number") != build]
entry["builds"].append(record)
entry["builds"].sort(key=lambda b: b["number"], reverse=True)
entry["latest"] = max(b["number"] for b in entry["builds"])

with open(manifest, "w") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")

print(f":: builds.json updated - {version} latest is now build {entry['latest']}")
PY

echo ":: Done"
echo "   ${TARGET_DIR}/${JAR_NAME}"
echo "   sha256 ${SHA}"
echo "   URL    /sugarcane/dl/${MC_VERSION}/${BUILD_NUM}/${JAR_NAME}"
