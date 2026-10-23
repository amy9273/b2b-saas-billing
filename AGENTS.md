# Application Building Context — B2B SaaS Billing Engine

Read the following files in order before implementing or making any architectural decision:

1. `context/project-overview.md` — Product definition, core business problems, personas, and success criteria.
2. `context/architecture.md` — System structure, multi-tenancy model, security boundaries, and architectural invariants.
3. `context/engineering-best-practices.md` — Master coding, financial math (BigDecimal), concurrency, idempotency, and database standards.
4. `context/ui-context.md` — Enterprise FinTech theme, semantic tokens, ergonomics, and anti-slop rules.
5. `context/code-standards.md` — Java 21 LTS standards, Spring Boot 3 layer responsibilities, RFC 7807 error formats, and quality gates.
6. `context/ai-workflow-rules.md` — Upstream Git discipline, branch-protection (never push to main), spec adherence, and verification loops.
7. `context/progress-tracker.md` — Current phase, roadmap, completed units, open questions, and next steps.

---

### Operating Rules

- **Sync Upstream First (Mandatory Baseline)**: Before starting any new unit or creating a feature branch, ALWAYS switch to `main`, run `git fetch origin main && git pull origin main`, and verify local `main` matches `origin/main`. Always branch directly from updated `main` (`git checkout -b feat/unit-NN-... origin/main`). NEVER branch off an unmerged or pre-squash feature branch.
- **Never Commit or Push to Main**: AI agents must **NEVER** commit or push directly to `main` or `master`. All work must be conducted on isolated feature branches (`feat/unit-NN-...`).
- **Pre-Push Upstream Sync & Rebase**: Before pushing ANY branch, always run `git fetch origin main`. If `origin/main` has advanced, rebase your feature branch cleanly onto `origin/main` (`git rebase origin/main`), verify `git diff --stat origin/main` reflects only current unit changes, and ensure 0 merge conflicts exist before pushing.
- **Work Strictly Against Active Spec**: Work strictly against the current unit spec in `context/specs/`.
- **Update Progress Tracker**: Update `context/progress-tracker.md` after each meaningful implementation change.
- **Preserve Invariants**: Never violate the architectural invariants documented in `context/architecture.md` or best practices in `context/engineering-best-practices.md`.
- **Mandatory Quality Check Loop**: Run compilation (`mvn clean compile`) and automated tests (`mvn test`) before creating commits.
- **Explicit Consent for Next Unit**: Never automatically proceed to the next unit without explicit user consent. Always complete and verify the current unit, present the deliverables, and wait for the user's confirmation before starting the next unit.
- **Process & Task Lifecycle**: Never leave background tasks hanging or accumulating; immediately terminate/kill finished or orphaned background tasks (`manage_task(Action='kill')`).
- **Build Artifact Hygiene**: Never commit `target/`, `*.class`, `*.jar`, or IDE metadata (`.idea/`, `.vscode/`). Always ensure `.gitignore` is strictly enforced.
