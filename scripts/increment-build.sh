#!/bin/bash
# Script to increment the build number in build.number file

BUILD_NUM_FILE="build.number"

# Read current build number
if [ ! -f "$BUILD_NUM_FILE" ]; then
    echo "0" > "$BUILD_NUM_FILE"
fi

CURRENT=$(cat "$BUILD_NUM_FILE" | tr -d '[:space:]')
NEW=$((CURRENT + 1))

# Write new build number
echo "$NEW" > "$BUILD_NUM_FILE"

echo "Build number incremented from $CURRENT to $NEW"
echo "New build number: $NEW"
