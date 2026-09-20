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

# Verify POM exists
if [[ ! -f "$SEARCH_DIR/pom.xml" ]]; then
  echo "ERROR: POM file not found: $SEARCH_DIR/pom.xml" >&2
  exit 1
fi

PIPELINE_PLATFORM="${PIPELINE_PLATFORM:-FUNCTION}"
PIPELINE_TRANSPORT="${PIPELINE_TRANSPORT:-REST}"
PIPELINE_REST_NAMING_STRATEGY="${PIPELINE_REST_NAMING_STRATEGY:-RESOURCEFUL}"
PIPELINE_GCP_DEPENDENCY_SCOPE="${PIPELINE_GCP_DEPENDENCY_SCOPE:-provided}"

"$MVN_BIN" -f "$ROOT_DIR/pom.xml" \
  -pl search \
  -am \
  -Dtpf.build.platform="$PIPELINE_PLATFORM" \
  -Dtpf.build.transport="$PIPELINE_TRANSPORT" \
  -Dtpf.build.rest.naming.strategy="$PIPELINE_REST_NAMING_STRATEGY" \
  -Dtpf.build.gcp.scope="$PIPELINE_GCP_DEPENDENCY_SCOPE" \
  -Dpipeline.function.provider=gcp \
  -Dquarkus.profile=gcp-functions \
  clean install "$@"
