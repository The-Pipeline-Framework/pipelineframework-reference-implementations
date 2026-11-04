#!/usr/bin/env python3
"""Enforce Search's HTTP/native packaging boundary before deployment."""
from io import BytesIO
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
contract = "META-INF/pipeline/pipeline-contract.json"
adapter = "org/pipelineframework/aws/durable/AwsDurableWorkItemHandler.class"

with (zipfile.ZipFile(root / "orchestrator-svc/target/function.zip") as ingress,
      zipfile.ZipFile(root / "function-worker/target/function.zip") as worker):
    ingress_files = set(ingress.namelist())
    worker_files = set(worker.namelist())
    if adapter in ingress_files or adapter not in worker_files:
        raise SystemExit("TPF's SQS entry point must be present only in the native worker ZIP")
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
