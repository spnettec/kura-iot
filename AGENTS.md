# Repository Guidelines

## Project Structure & Module Organization

This is a multi-module Maven project for YOFC IoT OSGi bundles. The root `pom.xml` aggregates three main module groups:

- `com.yofc.iot.api/`: shared API and Vert.x/OSGi integration code.
- `com.yofc.iot.apps/`: application bundles such as `abplc`, `opcuaserver`, `influxdb`, `visa`, and `ot3001`.
- `com.yofc.iot.repack/`: repackaged dependencies and bundle support modules.

Most modules follow Maven layout: `src/main/java` for Java code, `src/test/java` for tests, and `src/main/resources` or `src/test/resources` for resources. OSGi Declarative Services metadata lives in module-level `OSGI-INF/` directories.

## Build, Test, and Development Commands

- `mvn clean install`: build all modules and install artifacts locally. Tests are skipped by default through the root `skipTests` property.
- `mvn -DskipTests=false test`: run the full test suite.
- `mvn -pl com.yofc.iot.apps/com.yofc.iot.apps.abplc -am clean install`: build one module and its required dependencies.
- `mvn -pl com.yofc.iot.api/com.yofc.iot.api.comp -DskipTests=false test`: run tests for a single module.

Use module-scoped builds while iterating; use a root build before submitting broad dependency or bundle changes.

## Coding Style & Naming Conventions

Java is compiled for source/target 17. Keep the existing Java style: 4-space indentation, package names in lowercase, classes in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Preserve OSGi naming conventions in component XML files under `OSGI-INF/`. Avoid unrelated formatting churn because this repository has no enforced formatter configuration.

## Testing Guidelines

Tests use JUnit 5 and, in several modules, Vert.x JUnit 5 support. Place tests under the matching module’s `src/test/java` tree and resources under `src/test/resources`. Existing names include both `*Test` and `Test*`; prefer `*Test` for new unit tests. Because tests are skipped by default, explicitly pass `-DskipTests=false` when validating behavior.

## Commit & Pull Request Guidelines

Recent commit history uses short, imperative summaries such as `update log4j`, `update groovy`, and dependency-focused descriptions. Keep commits concise and scoped to one change. For pull requests, include the affected module paths, a summary of behavior or dependency changes, test commands run, and any OSGi packaging or runtime compatibility notes.

## Security & Configuration Tips

Many dependencies are `provided` because they are supplied by the Kura/OSGi runtime. Before adding or changing dependency scopes, verify the target runtime classloader behavior and bundle manifest imports. Do not commit local REDACTED_TEST_PASSWORDs, device credentials, or environment-specific certificates.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **yofc-iot** (5967 symbols, 14670 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/yofc-iot/context` | Codebase overview, check index freshness |
| `gitnexus://repo/yofc-iot/clusters` | All functional areas |
| `gitnexus://repo/yofc-iot/processes` | All execution flows |
| `gitnexus://repo/yofc-iot/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
