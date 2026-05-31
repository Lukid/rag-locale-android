# 🎯 Agentic Readiness Report — rag-locale-android

**Mode**: greenfield · **Agents**: claude · **Generated**: 2026-05-29
**Overall Score**: **43/100** 🟡 Partially Ready

> Freshly initialized greenfield baseline. Most gaps are *expected* — they fill in
> naturally as Kotlin source, Gradle modules, and tests are written. The baseline
> establishes portable instructions, secret hygiene, and a documented execution policy from day one.

## Executive Summary — Score Breakdown

```
Agent Instructions & Context      ██████████████░░  15.5/18
Navigability & Code Intelligence  ████████░░░░░░░░   8.7/18
Testing & Feedback                ███░░░░░░░░░░░░░   3.2/16
CI/CD, Automation & Governance    ███████░░░░░░░░░   6.0/14
Agent Tooling & Capabilities      ░░░░░░░░░░░░░░░░   0.0/12
Security & Sandbox                ███████░░░░░░░░░   5.25/12
Spec-Driven Workflow & Docs       ████████░░░░░░░░   4.9/10
```

### Top Gaps (by impact)
1. 🧪 **Testing & Feedback › test_suite_present** (+12.8) — no tests exist yet. Add unit tests for the pure pipeline logic (Chunker, cosine ranking, `NormalizedText` extraction, `PromptBuilder`) as code lands.
2. 🧰 **Agent Tooling › mcp_declaration / standard_skills** (+12.0) — no `.mcp.json`, Skills, or nav servers. Add a baseline `.mcp.json` and wire **Serena** once Kotlin sources exist.
3. 🗺️ **Navigability › repo_map / dependency graph / README** (+9.3) — nothing to map until source + `build.gradle` exist; add a `README.md`.
4. ⚙️ **CI/CD › governance / ci** (+8.1) — CI test/lint steps are placeholders; add `CODEOWNERS` + Dependabot (gradle), uncomment `./gradlew` steps once buildable.
5. 🔐 **Security › isolation / supply-chain** (+6.8) — add a `.devcontainer` egress allowlist; enable secret scanning + Gradle dependency locking.

## Layer Analysis

| Layer | Score | Max |
|---|---|---|
| **Portable** (any agent) | 40.8 | 94.3 |
| **Target-specific** (claude) | 2.7 | 5.7 |

Target layer = `cross_agent_bridge` (✅ 100, symlink), `custom_commands` (0), `agent_permission_policy` (0).
The `CLAUDE.md → AGENTS.md` symlink means there is **one source of truth, no drift** — the
`instruction_audit` "duplication" flag is the symlink reading identical content, not a contradictory copy.

## Per-Dimension Detail

### 1. Agent Instructions & Context — 15.5/18 (raw 86)
- ✅ `primary_instruction_file` (100) — `AGENTS.md` canonical + `CLAUDE.md` symlink.
- 🟡 `instruction_quality` (75) — project-specific (components, device, models, security); build/test/lint are Gradle placeholders until the project is scaffolded. *Fix (partial): verify real commands once `./gradlew` exists.*
- ✅ `instruction_conciseness` (100) — 79 lines, ~1041 tokens, no boilerplate.
- 🟡 `hierarchical_instructions` (50) — single root file; no subpackages yet. *Fix (skill): scaffold nested `AGENTS.md` when modules appear.*
- ✅ `cross_agent_bridge` (100) — proper symlink bridge for claude.

### 2. Navigability & Code Intelligence — 8.7/18 (raw 48)
- 🔴 `repo_map_availability` (25) — 0 source files to map. *Fix (partial): generate once Kotlin sources exist.*
- 🟡 `semantic_nav_amenability` (50) — Kotlin is LSP-friendly, but no code/config yet. *Manual, High.*
- 🔴 `dependency_structure_clarity` (25) — boundaries described in the spec, no `build.gradle` graph. *Manual, High.*
- 🟡 `readme_overview` (50) — overview in AGENTS.md/spec; no `README.md`. *Fix (partial): scaffold a README.*
- 🟡 `machine_readable_contracts` (50) — N/A for an offline single-app project.
- ✅ `file_size_sanity` (100) — no oversized files.

### 3. Testing & Feedback — 3.2/16 (raw 20)
- 🔴 `test_suite_present` (0) — no tests yet. *Manual, High: characterize the pipeline units first.*
- 🟡 `test_commands_documented` (50) — documented in AGENTS.md as TODO; runner not yet present. *Fix (skill).*
- 🔴 `fast_feedback_loop` (25) — no defined quick subset. *Fix (partial).*
- 🔴 `feedback_quality` (25) — Kotlin typing is inherent; no tests/assertions yet. *Fix (partial).*
- 🔴 `coverage_reasonable` (0) — no coverage config. *Fix (partial): scaffold JaCoCo + threshold.*

### 4. CI/CD, Automation & Governance — 6.0/14 (raw 43)
- 🟡 `ci_runs_tests_lint` (50) — workflow scaffolded (JDK 17 + Gradle cache); steps are commented TODO. *Fix (skill): uncomment once buildable.*
- 🟡 `lint_format_automated` (50) — ktlint referenced in pre-commit/CI, not yet wired into Gradle. *Fix (skill).*
- 🟢 `pre_commit_hooks` (75) — real config (whitespace, large-file guard, ktlint, detect-secrets). *Run `detect-secrets scan > .secrets.baseline` + `pre-commit install`.*
- 🔴 `governance` (0) — no CODEOWNERS / Dependabot. *Fix (skill).*

### 5. Agent Tooling & Capabilities — 0.0/12 (raw 0)
- 🔴 `standard_skills` (0), `bundled_helper_scripts` (0), `mcp_declaration` (0), `nav_comprehension_mcp_servers` (0), `custom_commands` (0).
- *Fix: add a baseline `.mcp.json` (skill) and wire Serena (manual) once code exists. Expected to be empty at init.*

### 6. Security & Sandbox — 5.25/12 (raw 44)
- 🔴 `committed_isolation_config` (0) — no `.devcontainer/`. *Fix (partial): scaffold egress allowlist.*
- ✅ `documented_execution_policy` (100) — `docs/agent-execution.md` + AGENTS.md security section.
- 🔴 `agent_permission_policy` (0) — no restrictive `.claude/settings.json`. *Manual.*
- 🟢 `secret_hygiene` (75) — `.gitignore` 100% secret coverage, `.env.example` (no real values), no committed secrets. *Enable host-side secret scanning + push protection.*
- 🔴 `supply_chain_pinning` (0) — no lockfiles/Dependabot (no deps yet). *Fix (partial): Gradle dependency locking once deps exist.*
- ✅ `injection_hygiene` (100) — instructions only in trusted files.

### 7. Spec-Driven Workflow & Docs — 4.9/10 (raw 49)
- 🟢 `spec_tasks_dir` (75) — `specs/TEMPLATE.md` + a real approved design spec.
- 🟡 `acceptance_criteria` (50) — template has the section; design spec uses scope/decisions. *Fix (partial).*
- 🔴 `issue_pr_templates` (0) — none. *Fix (skill).*
- 🟡 `adr_decisions` (50) — "Decisioni prese in brainstorming" log; no formal `docs/adr/`. *Fix (partial).*
- 🟡 `docs_comprehension_signals` (50) — strong architecture narrative; no changelog; no code annotations yet. *Fix (partial).*

## Remediation Roadmap (greenfield path)

**Now (skill-fixable, Low effort):**
- `.mcp.json` baseline, `CODEOWNERS` + Dependabot (gradle), issue/PR templates, `README.md`.
- `detect-secrets scan > .secrets.baseline` then `pre-commit install`.

**As code lands (the bulk of the score):**
- Scaffold the Gradle/Android project → uncomment CI `./gradlew test lint`, wire ktlint/detekt + JaCoCo.
- Write unit tests for the pure pipeline (Chunker, cosine ranking, extraction, PromptBuilder) → drives Testing (+12.8) and feedback quality.
- Generate the repo map + wire **Serena** for semantic nav → drives Navigability + Tooling.
- Commit the lockfile / enable Gradle dependency locking + a `.devcontainer` egress allowlist.

**Manual / human judgment:**
- Restrictive `.claude/settings.json` permission policy (threat-model dependent).
- Real architecture doc + ADRs capturing decisions as they're made.

---
*Generated by agent-ready-scan v2. Re-run `/agent-ready scan` to track progress; `/agent-ready fix` to auto-generate skill-fixable items.*
