#!/usr/bin/env python3
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SHA = "0123456789abcdef0123456789abcdef01234567"
BASE = "26.9.4"
REPOSITORY = "The-Pipeline-Framework/pipelineframework-reference-implementations"


def run(command, *, cwd=ROOT, env=None, success=True):
    result = subprocess.run(command, cwd=cwd, env=env, text=True, capture_output=True)
    if success and result.returncode != 0:
        raise AssertionError(f"command failed: {command}\n{result.stdout}\n{result.stderr}")
    if not success and result.returncode == 0:
        raise AssertionError(f"command unexpectedly passed: {command}")
    return result


for event, pr_number in (("pull_request", 42), ("push", None)):
    candidate = f"{BASE}-pr.{pr_number}.{SHA[:12]}" if pr_number else f"{BASE}-main.{SHA[:12]}"
    mode = "pr" if pr_number else "main"
    assert run(["bash", "scripts/candidate-version.sh", mode, BASE, str(pr_number or "-"), SHA]).stdout.strip() == candidate
    with tempfile.TemporaryDirectory(prefix="tpf-reference-candidate-") as directory:
        root = pathlib.Path(directory)
        metadata = {
            "schemaVersion": 1,
            "repository": REPOSITORY,
            "sourceRepository": "Contributor/pipelineframework-reference-implementations" if pr_number else REPOSITORY,
            "component": "references",
            "sourceSha": SHA,
            "pullRequestNumber": pr_number,
            "candidateVersion": candidate,
            "provenance": {"build": {
                "repository": REPOSITORY, "runId": 1234, "runAttempt": 2,
                "workflowPath": ".github/workflows/tpf-candidate-build.yml", "event": event,
            }},
            "mavenArtifacts": [],
            "images": [],
        }
        (root / "build-metadata.json").write_text(json.dumps(metadata))
        run(["bash", "-c", "sha256sum build-metadata.json > build-metadata.sha256 && sha256sum --check build-metadata.sha256"], cwd=root)
        (root / "build-metadata.sha256").write_text("0" * 64 + "  build-metadata.json\n")
        run(["sha256sum", "--check", "build-metadata.sha256"], cwd=root, success=False)
        (root / "build-metadata.sha256").unlink()
        final_env = os.environ | {
            "GITHUB_REPOSITORY": REPOSITORY, "GITHUB_RUN_ID": "5678", "GITHUB_RUN_ATTEMPT": "1",
        }
        run(["python3", "scripts/finalize-candidate-manifest.py", str(root)], cwd=ROOT, env=final_env)
        manifest_bytes = (root / "candidate-manifest/candidate-manifest.json").read_bytes()
        manifest = json.loads(manifest_bytes)
        event_doc = json.loads((root / "candidate-event/event.json").read_text())
        assert manifest["provenance"]["build"]["event"] == event
        assert manifest["provenance"]["publication"]["event"] == "workflow_run"
        assert manifest["mavenArtifacts"] == [] and manifest["images"] == []
        assert event_doc["manifest_sha256"] == hashlib.sha256(manifest_bytes).hexdigest()
        assert event_doc["publication_run_id"] == 5678

        pr = root / "current-pr.json"
        pr.write_text(json.dumps({
            "state": "open", "number": pr_number,
            "head": {"sha": SHA, "ref": "candidate-branch", "repo": {"full_name": metadata["sourceRepository"]}},
            "labels": [{"name": "safe-to-system-test"}],
        }))
        validate_env = os.environ | {
            "BUILD_RUN_ID": "1234", "BUILD_RUN_ATTEMPT": "2", "BUILD_RUN_EVENT": event,
            "BUILD_RUN_HEAD_SHA": SHA, "BUILD_RUN_HEAD_BRANCH": "candidate-branch" if pr_number else "main",
            "BUILD_RUN_PATH": ".github/workflows/tpf-candidate-build.yml",
            "BUILD_RUN_REPOSITORY": REPOSITORY,
            "BUILD_ASSOCIATED_PR_NUMBERS": "[42]" if pr_number else "[]",
        }
        validate = ["bash", "scripts/validate-candidate-files.sh", str(root), str(pr)]
        run(validate, cwd=ROOT, env=validate_env)
        if pr_number:
            run(validate, cwd=ROOT, env=validate_env | {"BUILD_ASSOCIATED_PR_NUMBERS": "[]"}, success=False)
            stale_pr = json.loads(pr.read_text())
            stale_pr["head"]["sha"] = "f" * 40
            pr.write_text(json.dumps(stale_pr))
            run(validate, cwd=ROOT, env=validate_env, success=False)
            stale_pr["head"]["sha"] = SHA
            stale_pr["labels"] = []
            pr.write_text(json.dumps(stale_pr))
            run(validate, cwd=ROOT, env=validate_env, success=False)

print("source candidate provenance and rejection fixtures passed")
