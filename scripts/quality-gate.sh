#!/usr/bin/env bash
set -eu

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

./gradlew build -q
cd frontend
if [[ ! -d node_modules ]]; then
    npm ci
fi
npm run lint
npm run typecheck
npm run test
npm run build
