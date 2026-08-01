#!/usr/bin/env bash
set -euo pipefail

# Builds Sugarcane and deploys the jar to castled.codes/sugarcane/downloads
# Usage: ./scripts/deploy-to-site.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
SITE_DIR="/mnt/storage/repos/CASTLEDCODEX/castled.codes"

cd "$REPO_DIR"

git config user.name "Sugarcane Builder" 2>/dev/null || true
git config user.email "builder@sugarcane.internal" 2>/dev/null || true

echo ":: Applying patches..."
./gradlew applyPatches

echo ":: Building paperclip jar..."
./gradlew createPaperclipJar

BUILD_NUM=$(cat build.number | tr -d '[:space:]')
JAR_NAME="sugarcane-26.1.2-${BUILD_NUM}.jar"
JAR_NAME_MC="sugarcane-1.21.11-${BUILD_NUM}.jar"

mv paper-server/build/libs/paper-paperclip-*.jar "paper-server/build/libs/${JAR_NAME}"
cp "paper-server/build/libs/${JAR_NAME}" "paper-server/build/libs/${JAR_NAME_MC}"

mkdir -p "${SITE_DIR}/sugarcane/dl"
cp "paper-server/build/libs/${JAR_NAME}" "${SITE_DIR}/sugarcane/dl/${JAR_NAME}"
cp "paper-server/build/libs/${JAR_NAME}" "${SITE_DIR}/sugarcane/dl/${JAR_NAME_MC}"

echo ":: Done! Deployed to ${SITE_DIR}/sugarcane/dl/"
echo "  - ${JAR_NAME}"
echo "  - ${JAR_NAME_MC}"
