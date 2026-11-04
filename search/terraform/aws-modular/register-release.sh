#!/usr/bin/env bash
set -euo pipefail

terraform_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
search_dir="$(cd "$terraform_dir/../.." && pwd)"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT

python3 "$search_dir/verify-lambda-packages.py"
terraform -chdir="$terraform_dir" output "$@" -json search_release > "$workspace/deployment.json"
unzip -q "$search_dir/orchestrator-svc/target/function.zip" 'lib/*.jar' -d "$workspace"
cd "$workspace"
java --enable-preview --source 21 -cp "$workspace/lib/*" \
  "$terraform_dir/release/RegisterSearchRelease.java" "$search_dir" "$workspace/deployment.json"
