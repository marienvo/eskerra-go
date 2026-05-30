# Docs

Specifications and step documentation live under [`specs/`](../specs/):

- [`specs/architecture/poc-contract.md`](../specs/architecture/poc-contract.md)
- [`specs/architecture/git-spike-findings.md`](../specs/architecture/git-spike-findings.md)
- [`specs/plans/workspace-setup.md`](../specs/plans/workspace-setup.md)

Manual verification checklists:

- [`verification/step-09-disposable-repo-manual-test.md`](verification/step-09-disposable-repo-manual-test.md) — run after `JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk ./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest` passes locally.
