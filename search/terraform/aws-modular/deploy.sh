#!/usr/bin/env bash
set -euo pipefail

terraform_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
terraform -chdir="$terraform_dir" apply -input=false -auto-approve
bash "$terraform_dir/register-release.sh"
