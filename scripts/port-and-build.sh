#!/usr/bin/env bash
set -euo pipefail

# Ports Sugarcane's own commits onto a Paper version branch (e.g. ver/1.21.11,
# ver/26.1.2) and, once clean, builds + deploys a jar via deploy-to-site.sh.
#
# This exists because "diff Sugarcane's patches against 26.2 and copy them onto
# an older version branch" is NOT safe - patch files are diffs against that
# version's own decompiled Minecraft source, and copying another version's
# patch wholesale produces a branch that silently fails to build (this is
# literally how ver/1.21.11 broke before this script existed: commit
# 7cbfa93e34 did a wholesale copy from main and left the branch at 2382/3074
# failing hunks). The only safe unit of work is a real commit, cherry-picked
# and reconciled against that branch's own patches.
#
# Usage:
#   scripts/port-and-build.sh port <target-branch> <commit>...
#       Cherry-pick each commit onto <target-branch> (already checked out or
#       will be checked out). Auto-resolves the conflicts that are genuinely
#       mechanical; stops and hands control back for anything touching a
#       vanilla source patch, since those need hand-adaptation, not a guess.
#
#   scripts/port-and-build.sh verify
#       cleanCache + applyPatches on the current branch. Run this before
#       trusting a port is done - "the cherry-pick applied" is not the same
#       as "the branch builds."
#
#   scripts/port-and-build.sh build <build-number>
#       Build + deploy the current branch at the given build number. Detects
#       whether this branch's paperweight exposes the unified
#       createPaperclipJar task or the older split
#       createMojmapPaperclipJar/createReobfPaperclipJar pair (ver/1.21.11 was
#       still on the split form as of this writing) and calls the right one,
#       then does what deploy-to-site.sh does: copy into
#       dl/<mcVersion>/<build>/ and register it in builds.json.
#
# What this script deliberately does NOT do: guess its way through a conflict
# in paper-server/patches/sources/**/*.patch or paper-server/patches/features/**.
# Those are diffs against decompiled vanilla source, so the correct fix is:
#   1. git checkout --ours <the conflicted .patch file>   (defer it)
#   2. finish the cherry-pick / commit
#   3. ./gradlew cleanCache && ./gradlew applyPatches      (get real decompiled source)
#   4. hand-edit paper-server/src/minecraft/java/... directly
#   5. ./gradlew fixupSourcePatches && ./gradlew rebuildPatches
#   6. git status - if rebuildFeaturePatches deleted files instead of
#      rewriting them (seen once this session), `git checkout HEAD -- paper-server/patches/features`
#      to restore, then re-stage only the files that actually diff.
#   7. ./gradlew cleanCache && ./gradlew applyPatches again to confirm.
# This script prints exactly these steps when it hits that case.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
cd "$REPO_DIR"

CI_ONLY_PATTERN='^\.github/workflows/'

usage() {
    grep '^#' "$0" | sed 's/^# \{0,1\}//' | sed -n '/^Usage:/,/^What this/p'
    exit 1
}

require_clean_tree() {
    if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
        echo "!! Working tree is dirty. Commit, stash, or discard before running this." >&2
        git status --short >&2
        exit 1
    fi
}

resolve_mechanical_conflicts() {
    # Called after a `git cherry-pick` that exited non-zero. Auto-resolves the
    # conflict classes we've actually seen be safe; leaves anything else for
    # the human/agent to sort out and exits non-zero so the caller stops.
    local unresolved=()
    while IFS= read -r -d '' f; do
        case "$f" in
            build.number)
                # Every branch builds the same numbered release; there's no
                # meaningful "merge" of this value, just take the target value.
                if [ -n "${BUILD_NUMBER_OVERRIDE:-}" ]; then
                    echo -n "$BUILD_NUMBER_OVERRIDE" > build.number
                else
                    echo -n "$(cat build.number 2>/dev/null || echo 6)" > build.number
                fi
                git add build.number
                ;;
            $CI_ONLY_PATTERN*)
                # Workflow YAML isn't version-source-sensitive the way a
                # decompiled-Minecraft patch is; take the incoming (newer) side.
                git checkout --theirs "$f"
                git add "$f"
                ;;
            paper-server/patches/sources/*|paper-server/patches/features/*|paper-server/patches/resources/*)
                # These are diffs against THIS branch's own decompiled source.
                # A textual 3-way merge of two different versions' diffs is a
                # coin flip at best - defer to the current branch's version and
                # let a human/agent re-derive the change from real source below.
                git checkout --ours "$f"
                git add "$f"
                unresolved+=("$f")
                ;;
            *)
                unresolved+=("$f")
                ;;
        esac
    done < <(git diff --name-only --diff-filter=U -z)

    if [ ${#unresolved[@]} -gt 0 ]; then
        echo ""
        echo "!! Deferred (kept this branch's existing content) for hand-adaptation:"
        printf '   %s\n' "${unresolved[@]}"
        echo ""
        echo "   These are vanilla-source patches. Do NOT try to merge the diff text."
        echo "   Instead, after this cherry-pick is committed:"
        echo "     1. ./gradlew cleanCache && ./gradlew applyPatches"
        echo "     2. Edit the real file under paper-server/src/minecraft/java/... to"
        echo "        make the same behavioral change (see the original commit's diff"
        echo "        for intent - variable/method names will differ per version)."
        echo "     3. ./gradlew fixupSourcePatches && ./gradlew rebuildPatches"
        echo "     4. git status - if rebuildFeaturePatches deleted files instead of"
        echo "        rewriting them, run: git checkout HEAD -- paper-server/patches/features"
        echo "        then re-check what actually diffs before staging."
        echo "     5. ./gradlew cleanCache && ./gradlew applyPatches to confirm clean."
        return 1
    fi
    return 0
}

cmd_port() {
    local target_branch="$1"; shift
    local commits=("$@")
    [ ${#commits[@]} -gt 0 ] || usage

    require_clean_tree
    git checkout "$target_branch"

    for c in "${commits[@]}"; do
        echo ":: Cherry-picking $(git log -1 --format='%h %s' "$c")"
        if git cherry-pick "$c" 2>&1; then
            continue
        fi
        if resolve_mechanical_conflicts; then
            git cherry-pick --continue --no-edit
            echo "   -> resolved mechanically"
        else
            echo "!! Stopped mid cherry-pick of $c on $target_branch."
            echo "   Finish resolving, then: git cherry-pick --continue"
            echo "   Then re-run: $0 verify"
            exit 1
        fi
    done

    echo ":: All commits ported. Run '$0 verify' before building."
}

cmd_verify() {
    echo ":: cleanCache (avoids stale decompiled source from a different branch/version)"
    ./gradlew cleanCache --console=plain
    echo ":: applyPatches (this is the real test - a clean cherry-pick is not proof of a working patch)"
    ./gradlew applyPatches --console=plain
    echo ":: Clean. Branch builds."
}

cmd_build() {
    local build_num="${1:?usage: $0 build <build-number>}"

    local paperclip_task
    if ./gradlew tasks --all --console=plain 2>/dev/null | grep -q '^paper-server:createPaperclipJar\b'; then
        paperclip_task="createPaperclipJar"
    elif ./gradlew tasks --all --console=plain 2>/dev/null | grep -q '^paper-server:createMojmapPaperclipJar\b'; then
        paperclip_task="createMojmapPaperclipJar"
        echo ":: This branch's paperweight predates the unified createPaperclipJar task; using createMojmapPaperclipJar."
    else
        echo "!! Neither createPaperclipJar nor createMojmapPaperclipJar found. Run '$0 verify' first, or check paperweight version." >&2
        exit 1
    fi

    echo -n "$build_num" > build.number
    BUILD_NUMBER="$build_num" ./gradlew "$paperclip_task" --console=plain

    local mc_version
    mc_version=$(grep -E '^mcVersion=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')

    local jar
    if [ "$paperclip_task" = "createPaperclipJar" ]; then
        jar=$(find paper-server/build/libs -maxdepth 1 -name "paper-paperclip-${mc_version}.build.${build_num}-*.jar" | head -1)
    else
        jar=$(find paper-server/build/libs -maxdepth 1 -name "paper-paperclip-${mc_version}-*-mojmap.jar" | head -1)
    fi
    [ -n "$jar" ] || { echo "!! Could not find built paperclip jar in paper-server/build/libs" >&2; exit 1; }

    local site_dir="${SUGARCANE_SITE_DIR:-/mnt/storage/repos/CASTLEDCODEX/castled.codes}"
    local target_dir="${site_dir}/sugarcane/dl/${mc_version}/${build_num}"
    local jar_name="sugarcane-${mc_version}-${build_num}.jar"
    mkdir -p "$target_dir"
    cp "$jar" "${target_dir}/${jar_name}"

    local size sha
    size=$(du -m "${target_dir}/${jar_name}" | cut -f1)
    sha=$(sha256sum "${target_dir}/${jar_name}" | cut -d' ' -f1)

    MC_VERSION="$mc_version" BUILD_NUM="$build_num" JAR_NAME="$jar_name" SIZE="$size" SHA="$sha" \
        MANIFEST="${site_dir}/sugarcane/dl/builds.json" python3 - <<'PY'
import json, os
from datetime import date

manifest = os.environ["MANIFEST"]
version, build = os.environ["MC_VERSION"], int(os.environ["BUILD_NUM"])

with open(manifest) as fh:
    data = json.load(fh)

entry = data.setdefault("versions", {}).setdefault(version, {"latest": build, "builds": []})
entry["builds"] = [b for b in entry["builds"] if b.get("number") != build]
entry["builds"].append({
    "number": build,
    "file": os.environ["JAR_NAME"],
    "date": date.today().isoformat(),
    "size": f"{os.environ['SIZE']} MB",
    "sha256": os.environ["SHA"],
})
entry["builds"].sort(key=lambda b: b["number"], reverse=True)
entry["latest"] = max(b["number"] for b in entry["builds"])

with open(manifest, "w") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")

print(f":: builds.json updated - {version} latest is now build {entry['latest']}")
PY

    echo ":: Done: ${target_dir}/${jar_name}"
    echo "   sha256 ${sha}"
}

case "${1:-}" in
    port)  shift; cmd_port "$@" ;;
    verify) cmd_verify ;;
    build) shift; cmd_build "$@" ;;
    *) usage ;;
esac
