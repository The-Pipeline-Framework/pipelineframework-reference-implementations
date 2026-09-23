#!/usr/bin/env bash
set -euo pipefail

mode=${1:?usage: prepare-candidate.sh pull_request|push PR_NUMBER SHA}
pr_number=${2:--}
sha=${3:?}
root=$(cd "$(dirname "$0")/.." && pwd)
base_version=$(python3 - "$root/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
version = ET.parse(sys.argv[1]).getroot().findtext("{http://maven.apache.org/POM/4.0.0}version", "")
if not version.endswith("-SNAPSHOT"):
    raise SystemExit("root project version must end in -SNAPSHOT")
print(version.removesuffix("-SNAPSHOT"))
PY
)
case "$mode" in
  pull_request) version_mode=pr ;;
  push) version_mode=main ;;
  *) echo "mode must be pull_request or push" >&2; exit 2 ;;
esac
candidate=$(bash "$root/scripts/candidate-version.sh" "$version_mode" "$base_version" "$pr_number" "$sha")
python3 - "$root" "$candidate" "$mode" "$pr_number" "$sha" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
candidate, event, pr_number, sha = sys.argv[2:]
metadata = {
    "schemaVersion": 1,
    "repository": "The-Pipeline-Framework/pipelineframework-reference-implementations",
    "sourceRepository": "",
    "component": "references",
    "sourceSha": sha.lower(),
    "pullRequestNumber": int(pr_number) if event == "pull_request" else None,
    "candidateVersion": candidate,
    "provenance": {"build": {}},
    "mavenArtifacts": [],
    "images": [],
}
(root / ".candidate-version.json").write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n")
PY
echo "candidate=$candidate" >> "${GITHUB_OUTPUT:-/dev/null}"
if [[ -n "${RUNNER_TEMP:-}" ]]; then cp "$root/.candidate-version.json" "$RUNNER_TEMP/tpf-candidate-version.json"; fi
