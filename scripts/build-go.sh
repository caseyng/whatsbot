#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/go"
gomobile bind -target android -o "$REPO_ROOT/android/app/libs/whatsbot-go.aar" .
echo "Built whatsbot-go.aar"
