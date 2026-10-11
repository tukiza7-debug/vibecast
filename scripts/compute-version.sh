#!/usr/bin/env bash
# Computes the next semantic version for one app from Conventional Commits that touch
# its paths (or the shared modules). Usage: compute-version.sh <addonx|vibecast>
# Prints: app=<name> version=<x.y.z> code=<N> tag=<prefix>-vx.y.z  or "no-version-change".
set -euo pipefail

app="${1:?usage: compute-version.sh <addonx|vibecast>}"
case "$app" in
  addonx) prefix="addonx"; app_dir="addonx" ;;
  vibecast) prefix="vibecast"; app_dir="vibecast" ;;
  *) echo "unknown app: $app" >&2; exit 2 ;;
esac

shared_paths="core adblock scripts brand.properties gradle/libs.versions.toml build.gradle.kts settings.gradle.kts"
paths="$app_dir $shared_paths"

# First release is always 1.0.0, including repositories without any tags.
last_tag=$(git tag -l "${prefix}-v*" | sort -V | tail -1 || true)
if [ -z "$last_tag" ]; then
  echo "app=$app version=1.0.0 code=1000000 tag=${prefix}-v1.0.0"
  exit 0
fi

commits=$(git log "$last_tag"..HEAD --format=%s%n%b -- $paths 2>/dev/null || true)
if [ -z "$commits" ]; then
  echo "no-version-change"
  exit 0
fi

major=0
minor=0
patch=0
has_bump=0
while IFS= read -r line; do
  if printf '%s' "$line" | grep -qE '^[a-z]+(\([^)]*\))?!:' || printf '%s' "$line" | grep -q '^BREAKING CHANGE:'; then
    major=1; has_bump=1
  fi
  if printf '%s' "$line" | grep -qE '^feat(\([^)]*\))?:'; then
    minor=1; has_bump=1
  elif printf '%s' "$line" | grep -qE '^fix(\([^)]*\))?:'; then
    patch=1; has_bump=1
  fi
done <<EOF
$commits
EOF

if [ "$has_bump" -eq 0 ]; then
  echo "no-version-change"
  exit 0
fi

base="${last_tag#${prefix}-v}"
base_major=$(echo "$base" | cut -d. -f1 | tr -d v)
base_minor=$(echo "$base" | cut -d. -f2)
base_patch=$(echo "$base" | cut -d. -f3)

if [ "$major" -eq 1 ]; then
  new_major=$((base_major + 1)); new_minor=0; new_patch=0
elif [ "$minor" -eq 1 ]; then
  new_major=$base_major; new_minor=$((base_minor + 1)); new_patch=0
else
  new_major=$base_major; new_minor=$base_minor; new_patch=$((base_patch + 1))
fi

version="$new_major.$new_minor.$new_patch"
code=$((new_major * 1000000 + new_minor * 1000 + new_patch))
echo "app=$app version=$version code=$code tag=${prefix}-v$version"
