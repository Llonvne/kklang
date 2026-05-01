# AGENTS.MD

本仓库实现 `kklang`：一门使用 Kotlin 编写的完整编程语言。
This repository implements `kklang`, a complete programming language written in
Kotlin.

每次修改都必须遵守以下规则。
Follow these rules for every change.

## Source Of Truth / 真相来源

- 不要因为某个行为“逻辑自洽”就猜测、发明或实现它。
  Do not guess, invent, or add behavior because it is internally consistent.
- 语言行为必须来自已维护的规范。
  Language behavior must come from the maintained spec.
- 面向人类的 Markdown 规范放在 `spec/` 下。
  Human-readable Markdown spec files live under `spec/`.
- 可执行的 Kotlin DSL 规范放在对应模块的测试源码集下，例如 `compiler/core/src/commonTest`；当项目需要跨模块共享时再拆出专门的 `spec-dsl` 模块。
  Executable Kotlin DSL spec definitions live under the owning module's test
  source set, such as `compiler/core/src/commonTest`; move them into a dedicated
  `spec-dsl` module once the project needs cross-module sharing.
- 如果行为尚未被规范定义，先停在测试/规范提案阶段，并在实现前请求确认。
  If behavior has not been specified yet, stop at tests/spec proposals and ask
  for confirmation before implementing it.
## Ownership Boundary / 职责边界

- 用户负责语言的高层方向，并审批指导原则。
  The user owns high-level language direction and approves guiding principles.
- 在相关指导原则获批后，Codex 负责语法机制、类型系统内部、编译阶段、运行/执行策略、诊断和测试架构的实现级设计。
  After the relevant guiding principles are approved, Codex owns
  implementation-level design for syntax mechanics, type-system internals,
  compiler phases, runtime/execution strategy, diagnostics, and test
  architecture.
- 用户不需要指定常规实现细节。
  The user should not need to specify routine implementation details.
- 实现细节仍然需要规范证据：先写 Markdown 规范和 Kotlin DSL 规范，再写测试，最后写实现。
  Implementation details still require spec evidence: write the Markdown spec
  and Kotlin DSL spec first, then tests, then implementation.
- 当某个 parser rule、AST 形状或 compiler helper 已符合获批指导原则并写入规范时，不要要求用户逐项审批。
  Do not ask the user to approve every parser rule, AST shape, or compiler
  helper when it follows an already approved guiding principle and is recorded
  in the spec.

## Documentation Language / 文档语言

- 所有面向仓库的文档必须保持中文/英文双语。
  All repository-facing documentation must remain bilingual in Chinese and
  English.
- 新增或修改 Markdown 文档时，同一段规则、说明、表格或示例必须同时提供中文和英文语义。
  When adding or changing Markdown documentation, provide both Chinese and
  English meaning for the same rule, description, table, or example.
- 如果文档中的中英文含义不一致，先修正文档，再继续实现。
  If the Chinese and English meanings diverge, fix the document before
  continuing implementation.

## Code Comments / 代码注释

- 所有类、接口、枚举、对象、方法、函数和测试都必须持有准确、不过时的中文/英文双语注释。
  Every class, interface, enum, object, method, function, and test must carry
  accurate, non-stale bilingual Chinese/English comments.
- 注释必须描述当前真实行为、约束和意图；当代码行为变化时，同一变更必须同步更新相关注释。
  Comments must describe the current real behavior, constraints, and intent;
  when code behavior changes, the same change must update the related comments.
- 如果发现注释缺失、过时或与实现不一致，先修备注释再继续依赖它做实现判断。
  If comments are missing, stale, or inconsistent with implementation, fix the
  comments before relying on them for implementation decisions.

## Kotlin Policy / Kotlin 策略

- 使用本项目可用的最新 Kotlin 版本。
  Use the latest available Kotlin version for this project.
- 当任务触及构建配置时，及时更新 Kotlin、Gradle 插件和相关编译器设置。
  Keep Kotlin, Gradle plugins, and relevant compiler settings current whenever a
  task touches build configuration.
- 优先使用最新 Kotlin 语法和语言特性；只要能让实现更清晰，即使是 experimental 或 unstable 特性也可以使用。
  Prefer the newest Kotlin syntax and language features, including experimental
  or unstable features, when they make the implementation clearer.
- experimental 用法必须在构建配置或源码注解中显式声明；不要用宽泛 suppressions 隐藏警告。
  Experimental usage must be explicit in build configuration or source
  annotations; do not hide warnings with broad suppressions.
- 除非用户明确要求，不要引入旧 Java/Kotlin 兼容性约束。
  Do not introduce older Java/Kotlin compatibility constraints unless the user
  explicitly requires them.

## TDD Rule / TDD 规则

- 先写测试，再写生产代码。
  Tests are written before production code.
- 修 bug 时，先新增或更新一个能在当前行为下失败的测试。
  For bug fixes, first add or update a test that fails for the current behavior.
- 新增语言能力时，先把预期行为写入 Markdown 规范、Kotlin DSL 规范和可执行测试。
  For new language features, first encode the expected behavior in the Markdown
  spec, Kotlin DSL spec, and executable tests.
- 只有失败的测试/规范存在之后，才可以修改生产实现。
  Only after the failing test/spec exists may production implementation change.
- 所有相关测试通过后，变更才算完成。
  A change is not complete until all relevant tests pass.

## Coverage Rule / 覆盖率规则

- 目标是 100% 行覆盖率和 100% 分支覆盖率。
  Target 100% line coverage and 100% branch coverage.
- 每个条件分支、parser 分支、类型检查分支、求值分支、诊断分支和错误路径都必须被测试覆盖。
  Every conditional branch, parser branch, type-checking branch, evaluation
  branch, diagnostic branch, and error path must be covered by tests.
- 不要把分支标记为不可达，除非该不变量写入规范并由测试强制保证。
  Do not mark branches as unreachable unless the invariant is documented in the
  spec and enforced by tests.
- 如果覆盖率工具尚未安装，在接受非平凡语言实现工作前先安装它。
  If coverage tooling is not installed yet, add it before accepting non-trivial
  language implementation work.
- 永远不要为了通过变更而降低覆盖率阈值。
  Never lower coverage thresholds to make a change pass.

## Spec Maintenance / 规范维护

- 规范是产品的一部分，不是注释。
  The spec is part of the product, not commentary.
- Markdown 规范面向人类解释语言：语法、语义、类型规则、诊断、示例和边界情况。
  Markdown spec files explain the language for humans: syntax, semantics, type
  rules, diagnostics, examples, and edge cases.
- Kotlin DSL 规范是面向机器和测试的可执行权威。
  Kotlin DSL spec definitions are executable authority for machines and tests.
- Markdown 和 Kotlin DSL 规范必须和对应实现处在同一个变更里。
  Markdown and Kotlin DSL specs must be updated in the same change as the
  implementation they describe.
- 如果 Markdown 和 Kotlin DSL 不一致，将其视为失败变更，并先修正规范差异。
  If Markdown and Kotlin DSL disagree, treat that as a failing change and fix
  the spec mismatch before implementation work continues.
- 不要实现规范缺失的语法、类型规则、求值行为、标准库行为、诊断或恢复行为。
  Do not implement syntax, type rules, evaluation behavior, standard-library
  behavior, diagnostics, or recovery behavior that is missing from the spec.

## Language Implementation Discipline / 语言实现纪律

- 除非规范要求耦合，否则 parser、AST、类型系统、诊断、evaluator/interpreter/compiler 和标准库职责必须分离。
  Keep parser, AST, type system, diagnostics, evaluator/interpreter/compiler,
  and standard-library concerns separated unless the spec requires coupling.
- 优先提供精确诊断，而不是静默恢复。
  Prefer precise diagnostics over silent recovery.
- 不要添加 fallback 值、隐式默认值、null/empty-value 行为或 best-effort 恢复，除非规范显式定义。
  Do not add fallback values, implicit defaults, null/empty-value behavior, or
  best-effort recovery unless they are explicitly specified.
- 为当前规范保留最小可观察行为面；未来语法和类型系统应通过增量规范扩展。
  Preserve the smallest observable behavior surface needed for the current
  spec. Future syntax and type-system work should be added incrementally.

## Tooling Synchronization / 工具链同步

- `.kk` 开发工具是语言产品的一部分，必须随着语言语法、token、诊断和规范一起迭代。
  `.kk` developer tooling is part of the language product and must evolve with
  language syntax, tokens, diagnostics, and specs.
- 修改 lexer token、parser surface、diagnostic code 或语义分类时，同一变更必须同步更新共享高亮分类、LSP 行为、IDEA 插件行为、Markdown 规范、Kotlin DSL 规范和测试。
  When changing lexer tokens, parser surface, diagnostic codes, or semantic
  categories, the same change must update shared highlighting classification,
  LSP behavior, IDEA plugin behavior, Markdown specs, Kotlin DSL specs, and
  tests.
- LSP 和 IDEA 插件不得各自发明语法规则；它们必须复用编译器前端或共享 tooling 模块。
  The LSP and IDEA plugin must not invent syntax rules independently; they must
  reuse the compiler frontend or shared tooling modules.
- IDEA 插件交付必须生成可在本地 IntelliJ IDEA 安装的插件 zip，并在最终简报中说明产物路径。
  IDEA plugin delivery must produce a plugin zip installable in the local
  IntelliJ IDEA, and the final brief must state the artifact path.

## Handoff Brief / 交付简报

- 完成代码修改后必须提供简报，说明改了什么、验证了什么、还剩什么风险或后续事项。
  After completing code changes, provide a brief that states what changed, what
  was verified, and what risks or follow-up items remain.
- 简报必须简洁、具体，并与实际执行过的命令和结果一致。
  The brief must be concise, concrete, and consistent with the commands and
  results that were actually executed.

## Required Verification / 必需验证

- 触及代码或规范后，交付前运行 `./gradlew test`。
  Run `./gradlew test` before handing off changes that touch code or specs.
- 覆盖率工具存在后，非平凡语言实现交付前还要运行 `./gradlew koverVerify`。
  Once coverage tooling exists, also run `./gradlew koverVerify` before handing
  off non-trivial language implementation work.
- formatter/linter/coverage 任务存在后也要运行；如果某个任务无法运行，在最终回复中说明。
  Run formatter/linter/coverage tasks once they exist, and document any task
  that cannot run in the final response.
- 修改 Kotlin 或 Gradle 版本时，使用 `./gradlew test` 验证，并在交付中说明有效版本证据。
  When changing Kotlin or Gradle versions, verify with `./gradlew test` and
  include the effective version evidence in the handoff.
