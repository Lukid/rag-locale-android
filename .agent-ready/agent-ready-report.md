# 🎯 Agentic Readiness Report — rag-locale-android

**Mode**: brownfield · **Agents**: claude · **Generated**: 2026-05-31
**Overall Score**: **63/100** 🟢 Ready
**Δ dal precedente**: 43 → 63 (+20) — il precedente scan era greenfield (solo docs); ora c'è l'app M1 buildabile.

## Score Breakdown

```
Agent Instructions & Context      ██████████████░░  15.5/18
Navigability & Code Intelligence  ███████████░░░░░  12.7/18
Testing & Feedback                ███████████░░░░░  11.4/16
CI/CD, Automation & Governance    ████████░░░░░░░░   7.4/14
Agent Tooling & Capabilities      ██████░░░░░░░░░░   4.4/12
Security & Sandbox                ████████░░░░░░░░   5.7/12
Spec-Driven Workflow & Docs       ██████████░░░░░░   6.0/10
```

## Layer Analysis

| Layer | Score | Max | Note |
|---|---|---|---|
| **Portable** (vale per qualsiasi agente) | 58.7 | 94.3 | la quasi totalità del punteggio |
| **Target-specific** (claude) | 4.35 | 5.7 | bridge CLAUDE.md→AGENTS.md ✓, commands ✓; manca una permission policy committata |

Le 3 sub-criteria *target* valutate per `claude`: `cross_agent_bridge` (100), `custom_commands` (100), `agent_permission_policy` (25).

## Top Gaps (per impatto)

| # | Dimensione › sub-criterio | +pti | Perché / Fix |
|---|---|---|---|
| 1 | 🟡 Tooling › `mcp_declaration` (0) | ~1.9 | Nessun `.mcp.json` di progetto → ogni utente cabla i server a mano. **Fix (skill):** genera un `.mcp.json` baseline. |
| 2 | 🟡 CI/CD › `ci_runs_tests_lint` (25) | ~2.0 | Il workflow è uno **stub TODO** (test/lint commentati) mentre `./gradlew test`/`ktlintCheck`/`lintDebug` ora sono **verdi**. **Fix (skill):** cabla i comandi reali. |
| 3 | 🟡 CI/CD › `governance` (0) | ~3.5 | Nessun CODEOWNERS/Dependabot. **Fix (skill):** scaffold entrambi. |
| 4 | 🟡 Security › `supply_chain_pinning` (25) | ~1.9 | Nessun lockfile/dependency-locking Gradle. **Fix (partial):** abilita il locking + Dependabot. |
| 5 | 🟡 Tooling › `nav_comprehension_mcp_servers` (0) | ~2.4 | Serena/Context7 non dichiarati nel repo. **Fix (manual):** registrali nella config MCP. |
| 6 | 🟡 Security › `committed_isolation_config` (0) | ~2.4 | Nessun devcontainer. **Fix (partial):** scaffold `.devcontainer/` con egress allowlist. |
| 7 | 🟡 Spec/Docs › `issue_pr_templates` (0) | ~1.5 | Nessun template issue/PR. **Fix (skill):** scaffold. |

---

## Dettaglio per dimensione

### 1. Agent Instructions & Context — 15.5/18 (raw 86) 🟢
- ✅ `primary_instruction_file` (100): AGENTS.md project-specific.
- ✅ `instruction_conciseness` (100): 83 righe, ~1120 token, 0 boilerplate.
- ✅ `cross_agent_bridge` (100, target): `CLAUDE.md` symlink → `AGENTS.md`, nessuna contraddizione.
- 🟡 `instruction_quality` (75): comandi reali presenti, ma la sezione **Struttura** elenca ancora i componenti RAG futuri e "app/ da creare" → leggermente stale. *Fix (partial): allinea Struttura ai package reali.* Effort: Med.
- 🟡 `hierarchical_instructions` (50): modulo unico, nessun AGENTS.md per-package (adeguato ora). *Fix (skill) quando nasceranno moduli.* Effort: Med.

### 2. Navigability & Code Intelligence — 12.7/18 (raw 71) 🟢
- ✅ `semantic_nav_amenability` (100): Kotlin tipizzato, LSP-friendly.
- ✅ `file_size_sanity` (100): 0 file >500 LOC (31 file Kotlin / 1673 LOC).
- 🟡 `dependency_structure_clarity` (75): modulo `:app` unico, package netti.
- 🟡 `repo_map_availability` (50): nessuna mappa committata; `repo_map.py` non indicizza Kotlin. *Fix (partial): genera/committa una repo-map o ARCHITECTURE.* Effort: Low.
- 🟡 `readme_overview` (50): nessun `README.md`; l'overview vive in AGENTS.md. *Fix (partial): aggiungi un README.* Effort: Low.
- 🟡 `machine_readable_contracts` (50): **N/A** — app on-device senza confini di servizio. Non è un difetto reale.

### 3. Testing & Feedback — 11.4/16 (raw 71) 🟢
- ✅ `test_commands_documented` (100): tabella Comandi in AGENTS.md (`./gradlew test`, ecc.).
- 🟡 `test_suite_present` (75): 24 unit test JVM sulla logica pura; niente test strumentati. *Fix (manual): test instrumented su device.* Effort: High.
- 🟡 `feedback_quality` (75): nomi descrittivi + assert con valori; nessun type-checker extra oltre al compilatore Kotlin.
- 🟡 `coverage_reasonable` (50): nessun jacoco/threshold. *Fix (partial): aggiungi coverage.* Effort: Med.
- 🟡 `fast_feedback_loop` (50): variant `testDebugUnitTest` documentato, nessun subset 'fast' esplicito.

### 4. CI/CD, Automation & Governance — 7.4/14 (raw 53) 🟡
- ✅ `lint_format_automated` (100): ktlint (plugin + `.editorconfig`), verificato verde.
- ✅ `pre_commit_hooks` (100): `.pre-commit-config.yaml` (ktlint-gradle, detect-secrets).
- 🟡 `ci_runs_tests_lint` (25): workflow **stub** (passi test/lint commentati come TODO) mentre i comandi reali ora funzionano. *Fix (skill): cabla `./gradlew test`/`ktlintCheck`/`lintDebug`.* Effort: Low.
- 🔴 `governance` (0): nessun CODEOWNERS/Dependabot. *Fix (skill): scaffold entrambi.* Effort: Low.

### 5. Agent Tooling & Capabilities — 4.4/12 (raw 36) 🟡
- ✅ `custom_commands` (100, target): `.claude/commands/opsx/*`.
- 🟡 `standard_skills` (75): 4 SKILL.md OpenSpec validi.
- 🔴 `mcp_declaration` (0): nessun `.mcp.json`. *Fix (skill).* Effort: Low.
- 🔴 `nav_comprehension_mcp_servers` (0): Serena/Context7 non dichiarati nel repo. *Fix (manual).* Effort: Med.
- 🟡 `bundled_helper_scripts` (25): le skill committate non portano `scripts/`.

### 6. Security & Sandbox — 5.7/12 (raw 48) 🟡
- ✅ `injection_hygiene` (100): istruzioni solo in file fidati.
- 🟡 `documented_execution_policy` (75): `docs/agent-execution.md` + sezione sicurezza in AGENTS.md.
- 🟡 `secret_hygiene` (75): gitignore 100% + `.env.example` + detect-secrets; manca push-protection host.
- 🟡 `supply_chain_pinning` (25): nessun lockfile/locking Gradle, nessun Dependabot. *Fix (partial).* Effort: Low.
- 🟡 `agent_permission_policy` (25, target): nessun `.claude/settings.json` restrittivo committato.
- 🔴 `committed_isolation_config` (0): nessun devcontainer. *Fix (partial).* Effort: Med.

### 7. Spec-Driven Workflow & Docs — 6.0/10 (raw 60) 🟡
- ✅ `spec_tasks_dir` (100): `specs/` + change OpenSpec completo (proposal/design/specs/tasks).
- 🟡 `acceptance_criteria` (75): scenari WHEN/THEN nelle spec.
- 🟡 `adr_decisions` (50): Decisioni D1–D8 nel design (ADR-like), niente `docs/adr/` dedicato.
- 🟡 `docs_comprehension_signals` (50): KDoc + design doc; manca ARCHITECTURE.md/CHANGELOG.
- 🔴 `issue_pr_templates` (0): nessun template. *Fix (skill).* Effort: Low.

---

## Remediation Roadmap

### Quick wins (skill-fixable, Low effort) — alto ritorno
1. **CI reale** — sostituisci lo stub con i comandi già verdi (`./gradlew test`, `ktlintCheck`, `lintDebug`). Il guadagno è quasi gratis: il lavoro è fatto, manca solo cablarlo.
2. **`.mcp.json` di progetto** — dichiara i server attesi (es. Serena/Context7) per riproducibilità.
3. **Governance** — `CODEOWNERS` + `dependabot.yml` (ecosistema Gradle).
4. **Template issue/PR** — `.github/ISSUE_TEMPLATE/` + `pull_request_template.md`.
5. **README.md** — overview + setup + i comandi (JDK 21, SDK 36).

### Medium effort (partial/manual)
6. **Supply-chain**: abilita Gradle dependency locking (`dependencyLocking`) e committa i lockfile; aggiungi Dependabot.
7. **Isolamento**: `.devcontainer/` con egress allowlist default-deny.
8. **Coverage**: jacoco + soglia; estendi i test oltre la logica pura (incl. instrumented su device).

> Esegui `/agent-ready fix` per generare automaticamente gli item skill-fixable (CI, MCP, governance, template).
