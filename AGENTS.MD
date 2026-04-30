# AGENTS.MD

This repository implements `kklang`, a complete programming language written in
Kotlin. Follow these rules for every change.

## Source Of Truth

- Do not guess, invent, or add behavior because it is internally consistent.
- Language behavior must come from the maintained spec:
  - human-readable Markdown spec files under `spec/`;
  - executable Kotlin DSL spec definitions under `src/test` or a dedicated
    `spec-dsl` module once it exists.
- If behavior has not been specified yet, stop at tests/spec proposals and ask
  for confirmation before implementing it.
- When a task depends on external product behavior, use enjoy-ios or Feishu docs
  as the single source of truth and show the supporting evidence before editing.

## Kotlin Policy

- Use the latest available Kotlin version for this project.
- Keep Kotlin, Gradle plugins, and relevant compiler settings current whenever a
  task touches build configuration.
- Prefer the newest Kotlin syntax and language features, including experimental
  or unstable features, when they make the implementation clearer.
- Experimental usage must be explicit in build configuration or source
  annotations; do not hide warnings with broad suppressions.
- Do not introduce older Java/Kotlin compatibility constraints unless the user
  explicitly requires them.

## TDD Rule

- Tests are written before production code.
- For bug fixes, first add or update a test that fails for the current behavior.
- For new language features, first encode the expected behavior in:
  - the Markdown spec;
  - the Kotlin DSL spec;
  - executable tests.
- Only after the failing test/spec exists may production implementation change.
- A change is not complete until all relevant tests pass.

## Coverage Rule

- Target 100% line coverage and 100% branch coverage.
- Every conditional branch, parser branch, type-checking branch, evaluation
  branch, diagnostic branch, and error path must be covered by tests.
- Do not mark branches as unreachable unless the invariant is documented in the
  spec and enforced by tests.
- If coverage tooling is not installed yet, add it before accepting non-trivial
  language implementation work.
- Never lower coverage thresholds to make a change pass.

## Spec Maintenance

- The spec is part of the product, not commentary.
- Markdown spec files explain the language for humans: syntax, semantics, type
  rules, diagnostics, examples, and edge cases.
- Kotlin DSL spec definitions are executable authority for machines and tests.
- Markdown and Kotlin DSL specs must be updated in the same change as the
  implementation they describe.
- If Markdown and Kotlin DSL disagree, treat that as a failing change and fix
  the spec mismatch before implementation work continues.
- Do not implement syntax, type rules, evaluation behavior, standard-library
  behavior, diagnostics, or recovery behavior that is missing from the spec.

## Language Implementation Discipline

- Keep parser, AST, type system, diagnostics, evaluator/interpreter/compiler,
  and standard-library concerns separated unless the spec requires coupling.
- Prefer precise diagnostics over silent recovery.
- Do not add fallback values, implicit defaults, null/empty-value behavior, or
  best-effort recovery unless they are explicitly specified.
- Preserve the smallest observable behavior surface needed for the current
  spec. Future syntax and type-system work should be added incrementally.

## Required Verification

- Run `./gradlew test` before handing off changes that touch code or specs.
- Run formatter/linter/coverage tasks once they exist, and document any task that
  cannot run in the final response.
- When changing Kotlin or Gradle versions, verify with `./gradlew test` and
  include the effective version evidence in the handoff.

