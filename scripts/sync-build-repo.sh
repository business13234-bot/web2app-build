#!/usr/bin/env bash
# Copies the build engine (workflows, generators, app templates, tests) to the public build
# repository, where GitHub runs Android and iOS builds for free.
# The Worker, the builder app and everything else stay in this private repository.
#
# Usage: scripts/sync-build-repo.sh [owner/name]   (default: business13234-bot/web2app-build)
set -euo pipefail
REPO="${1:-business13234-bot/web2app-build}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone -q "https://github.com/$REPO.git" "$WORK/repo"
cd "$WORK/repo"
git rm -rq --ignore-unmatch . >/dev/null
mkdir -p .github/workflows
cp "$ROOT"/.github/workflows/{android,ios,ios-selftest}.yml .github/workflows/
cp -R "$ROOT"/scripts "$ROOT"/templates "$ROOT"/shared "$ROOT"/tests .
cat > README.md <<'EOF'
# Web2App build runner

This repository only runs builds for the Web2App service. Each build is started by the
service, reads its settings from the service with a secret, and uploads its files
encrypted with a key made for that one build, so build files here cannot be opened.
EOF
git add -A
if git diff --cached --quiet; then echo "Build repository is up to date"; exit 0; fi
git -c user.name="business13234-bot" -c user.email="business13234-bot@users.noreply.github.com" \
  commit -qm "Sync build engine from the private repository"
git push -q origin HEAD:main
echo "Build repository updated: $REPO"
