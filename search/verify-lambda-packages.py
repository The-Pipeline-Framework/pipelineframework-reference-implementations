#!/usr/bin/env python3
"""Enforce Search's HTTP/native packaging boundary before deployment."""
import argparse
from io import BytesIO
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
contract = "META-INF/pipeline/pipeline-contract.json"
handlers = {
    "org/pipelineframework/aws/durable/AwsDurableWorkItemHandler.class",
    "org/pipelineframework/aws/durable/AwsDurableDueWorkHandler.class",
}
worker_entries = handlers | {
    "org/pipelineframework/aws/durable/AwsDurableSqsBatchAdapter.class",
    "org/pipelineframework/aws/durable/AwsDurableReleaseRouter.class",
}
host_artifact = "org.pipelineframework.pipelineframework-aws-durable-coordination-host-"
host_prefixes = (f"lib/{host_artifact}", f"lib/modified-{host_artifact}")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ingress", type=Path, default=root / "orchestrator-svc/target/function.zip")
parser.add_argument("--worker", type=Path, default=root / "function-worker/target/function.zip")
args = parser.parse_args()

with (zipfile.ZipFile(args.ingress) as ingress,
      zipfile.ZipFile(args.worker) as worker):
    ingress_files = set(ingress.namelist())
    worker_files = set(worker.namelist())
    if handlers & ingress_files or any(name.startswith(host_prefixes) for name in ingress_files):
        raise SystemExit("TPF's SQS handlers must be absent from the HTTP ingress ZIP")
    host_jars = [name for name in worker_files if name.startswith(host_prefixes) and name.endswith(".jar")]
    if len(host_jars) != 1:
        raise SystemExit("Native worker ZIP must contain exactly one TPF coordination host JAR")
    with zipfile.ZipFile(BytesIO(worker.read(host_jars[0]))) as host:
        missing_worker_entries = worker_entries - (worker_files | set(host.namelist()))
    if missing_worker_entries:
        raise SystemExit(f"Native worker ZIP is missing TPF SQS classes: {sorted(missing_worker_entries)}")
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
