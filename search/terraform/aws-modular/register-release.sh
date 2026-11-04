#!/usr/bin/env bash
set -euo pipefail

terraform_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
search_dir="$(cd "$terraform_dir/../.." && pwd)"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT

terraform -chdir="$terraform_dir" output "$@" -json search_release > "$workspace/deployment.json"
ingress_zip="$(jq -er '.artifacts["orchestrator-svc"].local_path' "$workspace/deployment.json")"
worker_zip="$(jq -er '.artifacts["function-worker"].local_path' "$workspace/deployment.json")"
python3 "$search_dir/verify-lambda-packages.py" --ingress "$ingress_zip" --worker "$worker_zip"
unzip -q "$ingress_zip" 'lib/*.jar' -d "$workspace"
cd "$workspace"
java --enable-preview --source 21 -cp "$workspace/lib/*" \
  "$terraform_dir/release/RegisterSearchRelease.java" "$workspace/deployment.json"
