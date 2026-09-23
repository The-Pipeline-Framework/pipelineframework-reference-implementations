#!/usr/bin/env bash
set -euo pipefail

suite=${1:?usage: system-tests.sh core|checkout|search|quickbooks|cloud}
read -r -a maven_args <<< "${MAVEN_ARGS:-}" || true

run_search() {
  ./mvnw "${maven_args[@]}" -f search/pom.xml \
    -pl crawl-source-svc,parse-document-svc,tokenize-content-svc,index-document-svc -am \
    -DskipUnitTests=true -DskipNative=true -Dquarkus.container-image.build=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    -Dit.test=RawDocumentResourceIT,ParsedDocumentResourceIT,TokenBatchResourceIT,IndexAckResourceIT verify

  ./search/build-lambda.sh -DskipTests -Dquarkus.container-image.build=false
  ./mvnw "${maven_args[@]}" -f search/pom.xml -pl orchestrator-svc \
    -Dtpf.build.platform=FUNCTION -Dtpf.build.transport=REST \
    -Dtpf.build.rest.naming.strategy=RESOURCEFUL -Dtpf.build.lambda.scope=compile \
    -Dquarkus.profile=lambda -Dtest=LambdaMockEventServerSmokeTest \
    -Dquarkus.container-image.build=false test

  docker_env
  ./mvnw "${maven_args[@]}" -f search/pom.xml -DskipTests -DskipNative=true \
    -Dquarkus.container-image.build=true clean install
  ./mvnw "${maven_args[@]}" -f search/pom.xml -pl orchestrator-svc -am \
    -DskipUnitTests=true -DskipNative=true -Dquarkus.container-image.build=false \
    -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=SearchPipelineEndToEndIT verify
}

docker_env() {
  export DOCKER_HOST=unix:///var/run/docker.sock
  if ! docker info >/dev/null 2>&1; then sudo systemctl start docker || true; fi
  for attempt in {1..10}; do
    docker info >/dev/null 2>&1 && break
    sleep 3
  done
  local server_api
  server_api=$(docker version --format '{{.Server.APIVersion}}')
  test -n "$server_api" || { echo "Docker daemon is unavailable for Search E2E." >&2; return 1; }
  export DOCKER_API_VERSION="$server_api"
  export TESTCONTAINERS_DOCKER_CLIENT_STRATEGY=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  export TPF_CI_QUIET=true
  mkdir -p "$HOME/.cache/google-cloud-tools-java/jib"
  printf '%s\n' 'docker.client.strategy=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy' \
    'docker.host=unix:///var/run/docker.sock' > "$HOME/.testcontainers.properties"
}

case "$suite" in
  core)
    ./mvnw "${maven_args[@]}" -f pom.xml clean verify
    ;;
  checkout)
    ./mvnw "${maven_args[@]}" -f checkout/pom.xml -pl tpfgo-e2e-tests -am \
      -Dtest=NoMatchingUnitTest -Dsurefire.failIfNoSpecifiedTests=false \
      -Dit.test=TpfgoCheckpointFlowIT#executesFullCanonicalTpfgoFlowOverGrpcCheckpointHandoff \
      -Dfailsafe.failIfNoSpecifiedTests=false -Dquarkus.container-image.build=false verify
    ;;
  search)
    run_search
    ;;
  quickbooks)
    ./mvnw "${maven_args[@]}" -f quickbooks-collections-briefing/pom.xml clean verify
    ;;
  cloud)
    cat >&2 <<'MESSAGE'
Cloud suite cannot run from the central credential-free test job. The existing AWS Modular
and Azure Functions workflows require their repository's GitHub Actions OIDC identity and
cloud-specific role/subscription configuration. Run one of these owner workflows with its
configured credentials: e2e-search-aws-modular.yml or e2e-search-azure-functions.yml.
No cloud deployment was attempted.
MESSAGE
    exit 2
    ;;
  *)
    echo "Unknown system-test suite: $suite" >&2
    exit 2
    ;;
esac
