#!/usr/bin/env python3
import json
import os
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
metadata = json.loads((root / "candidate-manifest.json").read_text())
metadata["sourceRepository"] = os.environ["SOURCE_REPOSITORY"]
metadata["provenance"] = {"build": {
    "repository": os.environ["GITHUB_REPOSITORY"],
    "runId": int(os.environ["GITHUB_RUN_ID"]),
    "runAttempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
    "workflowPath": ".github/workflows/tpf-candidate-build.yml",
    "event": os.environ["GITHUB_EVENT_NAME"],
}}
(root / "build-metadata.json").write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n")
