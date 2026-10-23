# Application Building Context — B2B SaaS Billing Engine

Read the following files in order before implementing or making any architectural decision:

1. `context/project-overview.md` — Product definition, core business problems, personas, and success criteria.
2. `context/architecture.md` — System structure, multi-tenancy model, security boundaries, and invariants.
3. `context/engineering-best-practices.md` — Master coding, financial math (BigDecimal), concurrency, idempotency, and database standards.
4. `context/ui-context.md` — Enterprise FinTech theme, semantic tokens, ergonomics, and anti-slop rules.
5. `context/code-standards.md` — Java 21 LTS standards, Spring Boot 3 layer responsibilities, RFC 7807 error formats, and quality gates.
6. `context/ai-workflow-rules.md` — Upstream Git discipline, branch-protection (never push to main), spec adherence, and verification loops.
7. `context/progress-tracker.md` — Current phase, roadmap, completed units, open questions, and next steps.

---

### Operating Rules

- Update `context/progress-tracker.md` after each meaningful implementation change.
- Work strictly against the current unit spec in `context/specs/`.
- If implementation changes architecture, scope, or standards, update the relevant context file before continuing.
- Never violate the architectural invariants documented in `context/architecture.md` or best practices in `context/engineering-best-practices.md`.
- Never commit or push directly to `main` or `master`. Always work in `feat/unit-NN-...` branches.
