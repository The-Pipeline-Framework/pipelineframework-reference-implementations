# TPF Reference Implementations

Long-lived, operationally realistic applications that demonstrate how released TPF artifacts compose into
substantial systems. This repository consumes the compiler, runtime, contracts, Connectors, Blocks, and
Expansions; it does not own their semantics.

## Catalogue

- [`checkout`](checkout/) — TPFGo, a multi-pipeline checkout and fulfilment system with checkpoint handoff,
  compensation, durable coordination, and an operator-facing UI.
- [`search`](search/) — a modular document search pipeline spanning REST, gRPC, function, caching, persistence,
  replay, and cloud deployment paths.
- [`quickbooks-collections-briefing`](quickbooks-collections-briefing/) — a read-only QuickBooks briefing built
  from an imported MCP Query and a host-managed process boundary.

Focused teaching examples and architectural proofs live in
[`pipelineframework-examples`](https://github.com/The-Pipeline-Framework/pipelineframework-examples). Full
applications such as CSV Payments and RAG Turnkey have separate ownership and release concerns.

## Build

```sh
./mvnw clean verify -Dgpg.skip -Dmaven.repo.local="$PWD/.m2/repository"
```

The reactor is intentionally non-publishable. All TPF dependencies resolve as released Maven artifacts.

## Cross-repository system-test suites

The coordinator consumes the stable suite entrypoints in `.github/tpf-system-tests.json`. `core` runs the
Maven reactor; `checkout`, `search`, and `quickbooks` run the corresponding owner-controlled integration and
end-to-end checks. The `cloud` entrypoint fails closed: AWS Modular and Azure Functions deployments require
this repository's GitHub Actions OIDC identity and cloud role/subscription configuration, which the
coordinator's credential-free test jobs do not receive. Run those deployments through the existing owner
workflows, `e2e-search-aws-modular.yml` or `e2e-search-azure-functions.yml`.
