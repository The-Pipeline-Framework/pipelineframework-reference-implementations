#!/usr/bin/env python3
"""Enforce Search's HTTP/native packaging boundary before deployment."""
import argparse
from io import BytesIO
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
contract = "META-INF/pipeline/pipeline-contract.json"
adapters = {
    "org/pipelineframework/aws/durable/AwsDurableWorkItemHandler.class",
    "org/pipelineframework/aws/durable/AwsDurableDueWorkHandler.class",
}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ingress", type=Path, default=root / "orchestrator-svc/target/function.zip")
parser.add_argument("--worker", type=Path, default=root / "function-worker/target/function.zip")
args = parser.parse_args()

with (zipfile.ZipFile(args.ingress) as ingress,
      zipfile.ZipFile(args.worker) as worker):
    ingress_files = set(ingress.namelist())
    worker_files = set(worker.namelist())
    if adapters & ingress_files or not adapters <= worker_files:
        raise SystemExit("TPF's SQS entry points must be present only in the native worker ZIP")
    if not any("quarkus-amazon-lambda-http-" in name for name in ingress_files):
        raise SystemExit("The ingress ZIP must include the Quarkus Lambda HTTP adapter")
    if any("quarkus-amazon-lambda-http-" in name for name in worker_files):
        raise SystemExit("The worker ZIP must exclude the Quarkus Lambda HTTP adapter")
    apps = [name for name in worker_files if name.startswith("lib/org.pipelineframework.search.orchestrator-svc-")
            and name.endswith(".jar")]
    if len(apps) != 1:
        raise SystemExit("The worker ZIP must package exactly one compiled Search application")
    with zipfile.ZipFile(BytesIO(worker.read(apps[0]))) as app:
        if ingress.read(contract) != app.read(contract):
            raise SystemExit("Ingress and worker must carry the same generated Search contract")
