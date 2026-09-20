#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEARCH_DIR="$ROOT_DIR/search"
MVN_BIN="${MVN_BIN:-$ROOT_DIR/mvnw}"

if [[ ! -e "$MVN_BIN" ]]; then
  echo "Maven launcher not found: $MVN_BIN" >&2
  exit 1
fi

if [[ ! -x "$MVN_BIN" ]]; then
  echo "Maven launcher is not executable: $MVN_BIN" >&2
  exit 1
fi

PIPELINE_PLATFORM="${PIPELINE_PLATFORM:-FUNCTION}"
PIPELINE_TRANSPORT="${PIPELINE_TRANSPORT:-REST}"
PIPELINE_REST_NAMING_STRATEGY="${PIPELINE_REST_NAMING_STRATEGY:-RESOURCEFUL}"
PIPELINE_CONFIG_PATH="${PIPELINE_CONFIG_PATH:-$SEARCH_DIR/config/pipeline.modular-lambda.yaml}"
MODULES="${MODULES:-search/common,search/crawl-source-svc,search/parse-document-svc,search/tokenize-content-svc,search/embed-content-svc,search/index-document-svc,search/orchestrator-svc}"

"$MVN_BIN" -f "$ROOT_DIR/pom.xml" \
  -pl "$MODULES" \
  -am \
  -Dtpf.build.platform="$PIPELINE_PLATFORM" \
  -Dtpf.build.transport="$PIPELINE_TRANSPORT" \
  -Dtpf.build.rest.naming.strategy="$PIPELINE_REST_NAMING_STRATEGY" \
  -Dtpf.build.lambda.scope=test \
  -Dtpf.build.lambda.http.scope=compile \
  -Dpipeline.config.arg="-Apipeline.config=$PIPELINE_CONFIG_PATH" \
  -Dpipeline.function.httpBridge.arg="-Apipeline.function.httpBridge=true" \
  -Dquarkus.profile=lambda-modular \
  clean package "$@"
