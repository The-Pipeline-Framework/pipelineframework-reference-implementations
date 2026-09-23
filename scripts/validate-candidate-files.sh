#!/usr/bin/env bash
set -euo pipefail

root=${1:?usage: validate-candidate-files.sh BUILD_ROOT CURRENT_PR_JSON}
pr_path=${2:?}
python3 - "$root" "$pr_path" <<'PY'
import json, os, pathlib, re, sys, xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
metadata = json.loads((root / "build-metadata.json").read_text())
pr_path = pathlib.Path(sys.argv[2])
ns = "{http://maven.apache.org/POM/4.0.0}"
base_version = ET.parse("pom.xml").getroot().findtext(ns + "version", "")
assert re.fullmatch(r"\d+\.\d+\.\d+-SNAPSHOT", base_version), "invalid root snapshot version"
base_version = base_version.removesuffix("-SNAPSHOT")
base_repo = "The-Pipeline-Framework/pipelineframework-reference-implementations"
required = {"schemaVersion", "repository", "sourceRepository", "component", "sourceSha", "pullRequestNumber", "candidateVersion", "provenance", "mavenArtifacts", "images"}
assert set(metadata) == required, "unexpected or missing source build metadata fields"
assert metadata["schemaVersion"] == 1 and metadata["repository"] == base_repo
assert metadata["component"] == "references"
assert metadata["mavenArtifacts"] == [] and metadata["images"] == [], "source candidate cannot contain Maven artifacts or images"
source_sha = metadata["sourceSha"]
assert re.fullmatch(r"[0-9a-f]{40}", source_sha), "invalid source SHA"
assert metadata["provenance"]["build"] == {
    "repository": base_repo, "runId": int(os.environ["BUILD_RUN_ID"]),
    "runAttempt": int(os.environ["BUILD_RUN_ATTEMPT"]),
    "workflowPath": ".github/workflows/tpf-candidate-build.yml", "event": os.environ["BUILD_RUN_EVENT"]
}, "build provenance does not match workflow_run"
assert os.environ["BUILD_RUN_PATH"] == ".github/workflows/tpf-candidate-build.yml"
assert os.environ["BUILD_RUN_REPOSITORY"] == base_repo
event = os.environ["BUILD_RUN_EVENT"]
version = metadata["candidateVersion"]
if event == "pull_request":
    number = metadata["pullRequestNumber"]
    assert isinstance(number, int) and number > 0
    associated = json.loads(os.environ["BUILD_ASSOCIATED_PR_NUMBERS"])
    assert associated and number in associated, "PR not associated with triggering workflow run"
    assert version == f"{base_version}-pr.{number}.{source_sha[:12]}", "candidate version does not match PR/SHA"
    pr = json.loads(pr_path.read_text())
    assert pr["state"] == "open" and pr["number"] == number
    assert pr["head"]["sha"] == source_sha, "PR head changed since candidate build"
    assert pr["head"]["repo"]["full_name"].lower() == metadata["sourceRepository"].lower()
    assert os.environ["BUILD_RUN_HEAD_BRANCH"] == pr["head"]["ref"]
    if metadata["sourceRepository"].lower() != base_repo.lower():
        assert "safe-to-system-test" in {label["name"] for label in pr.get("labels", [])}, "fork PR lacks safe-to-system-test label"
elif event == "push":
    assert metadata["pullRequestNumber"] is None
    assert metadata["sourceRepository"] == base_repo
    assert os.environ["BUILD_RUN_HEAD_BRANCH"] == "main"
    assert source_sha == os.environ["BUILD_RUN_HEAD_SHA"]
    assert version == f"{base_version}-main.{source_sha[:12]}", "candidate version does not match main SHA"
else:
    raise AssertionError(f"unsupported build event: {event}")
PY
