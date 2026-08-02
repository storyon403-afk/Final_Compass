#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${FINALS_COMPASS_SOURCE_DIR:-/opt/finals-compass/source}"

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
  echo "Git repository not found: $SOURCE_DIR" >&2
  exit 1
fi

cd "$SOURCE_DIR"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to update a dirty production source tree:" >&2
  git status --short >&2
  exit 1
fi

git fetch --prune origin
git switch main
git merge --ff-only origin/main

echo "Finals Compass source is now at:"
git show -s --format='%H %s' HEAD
