# Repository instructions

This repository owns long-lived reference implementations for The Pipeline Framework. They are executable,
operationally realistic compatibility surfaces, not framework implementations and not libraries for other
repositories to depend upon.

Keep every reference implementation dependent on released TPF compiler, runtime, contract, connector, Block,
and Expansion artifacts. Do not restore source dependencies on the framework monorepo. Implementation-specific
support modules may live beside their owner, but must not masquerade as public ecosystem artifacts.

Real applications, focused learning examples/proofs, ecosystem connectors, packaged Blocks, Expansion
distributions, and runtime-owned Spring smoke tests do not belong here.

Always use an isolated Maven local repository:

```sh
./mvnw <goals> -Dmaven.repo.local="$PWD/.m2/repository"
```

Do not introduce Maven profiles. These implementations are not published; `central-publishing` is therefore not needed.
There must be one canonical Maven reactor and lifecycle.

Before changing a reference implementation, preserve the compiler/runtime behavior it proves and keep its
focused integration tests green. Do not use one implementation as a shared library for another unless the
dependency is itself the architectural behavior under test.
