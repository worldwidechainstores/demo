# Workflow profile — Demo module
**Inherits**: ../platform/.claude/fleet-profile.md

A CSnx-native demo module on the CSnx module platform (Spring Boot backend + React metadata-driven UI engine, Postgres), used as a **reference template** for scaffolding new modules.

This file is the per-project half of the `team-workflow` plugin skills. The skills hold the
process; this file holds this project's facts. What is the same across the fleet (the ticket
folder and artifact paths, reusable helpers, constants homes, fleet-wide learnings) is
inherited from `../platform/.claude/fleet-profile.md`; change it there, not here.

> **Onboarding status: partial.** The build, source-control and local-run sections below are
> filled in from the repo. The Jira-workflow, environment, UAT and Confluence sections are
> marked `TODO (not onboarded)` — this project does not yet run the full ticket lifecycle.
> Run `/setup-workflow-profile` to complete them when it does.

## ⛔ Do NOT run `/uat-handover` on this module (decision, 2026-09-19)

**The developer lifecycle for the new modules currently ends at `/merge-ticket`.** Stop
there: the ticket is merged, CI is green, and that is the finished state for now. Three
things the skill depends on are deliberately absent — none is a gap to work around:

1. **No Azure deploy.** We are not deploying to Azure at the moment, so CI's `deploy` job
   is **skipped, not failed**. A green pipeline is expected and does not mean a deployed
   build; do not treat the skipped job as a problem to fix.
2. **No UAT testing.** Nobody tests these modules through UAT yet, so there is no
   environment to point a tester at and no guide worth writing.
3. **No *handed to QA* status.** The CSNX Task workflow is `Open → In Progress → Done`,
   where *Done* means finished — there is nothing to hand a ticket over *to*.

**What still happens:** `/implement-plan` writes the developer guide and attaches it, and
`/merge-ticket` refreshes it if the code moved. That guide IS the handover document and it
is enough; only the tester-facing guide is skipped.

**Revisit when the Azure environments for UAT are sorted.** That is the gating decision —
the Jira QA status and the tester guides follow from it. Then re-run
`/setup-workflow-profile` and delete this section.

*(Fleet-wide decision; originates in `platform/.claude/workflow-profile.md`.)*

## Identity

- **Issue tracker**: `TODO (not onboarded)` — confirm whether this project's work is tracked in
  Jira (project key `CSNX`, site `worldwidechainstores.atlassian.net`) or elsewhere.
- **Customer ticket keys**: none — customer reports for this module arrive as `CSNX` tickets.
  WCSHD, the helpdesk, carried only CSnx incidents over the past year.
- **Epics**: `TODO (not onboarded)`.
- **Workflow statuses**: `TODO (not onboarded)`.
- **Transitions that require a worklog**: `TODO (not onboarded)`.

## Source control

- **Host**: GitHub — `github.com/worldwidechainstores/demo.git`. Use the `mcp__github__*` MCP tools for pull requests.
- **Trunk branch**: `main`.
- **Feature branch**: cut from trunk, named after the ticket or a short slug:

  ```bash
  git fetch origin main && git checkout -b <BRANCH> origin/main && git push -u origin <BRANCH>
  ```

- **Epic branches**: `TODO (not onboarded)`.
- **Merge style**: none — follows the shared rule (squash a ticket; `--no-ff` only for an epic
  branch into `main`).
- **Conflict-prone shared files**: `TODO (record them as they are found)`.

## Build & test

| Purpose | Command |
|---|---|
| Compile / fast build (backend) | `cd backend && ./mvnw compile` |
| Package deployable artifact | `cd backend && ./mvnw clean package` |
| Full build gate | `cd backend && ./mvnw clean package` (plus `cd frontend && npm run lint && npm run build` if the UI changed) |
| Run the whole test suite (backend) | `cd backend && ./mvnw test` (unit + Testcontainers integration) |
| Run a single test class | `cd backend && ./mvnw test -Dtest=<ClassName>` |
| Frontend unit tests | none — `package.json` has no unit-test runner; `cd frontend && npm run build` (`tsc -b`) is the type check |
| End-to-end / browser suite | `cd frontend && npx playwright test` |

- **Code-generation rules**: no code generation; **no Lombok** — hand-write getters/setters on entities, DTOs are `record`s.
- **Test conventions**: JUnit 5 + Testcontainers for backend integration tests, one shared Postgres server per run ([platform convention](../platform/docs/module-conventions.md#backend), Tests).
- **Regression suite**: `frontend/tests/e2e/specs/` (Playwright; `auth.setup.ts` signs in as the
  seeded `wcstest` e2e admin) — a ticket adds one spec. Needs the platform + demo compose
  stacks up.

## Local run

```bash
cd backend  && ./mvnw spring-boot:run   # :8092, DB default localhost:5435/demo
cd frontend && npm run dev              # http://localhost:5175

# Full stack, closer to prod
docker compose up --build               # frontend :8085, backend :8092, db :5435
```

- **URL**: `http://localhost:5175` (dev) or `http://localhost:8085` (compose).
- **Java 21** is required for the backend; **Node 20.19+ / 22+** for the frontend.
- **Database**: Postgres — compose publishes `:5435`; the dev datasource defaults to `localhost:5435/demo` (db/user/password `demo`).
- **Login**: sign-in goes through the central platform IdP (OIDC) — the platform stack must be running alongside this module.

## Environments

| Name | Config | App URL | Database | Deployed by | Constraints |
|---|---|---|---|---|---|
| local | see **Local run** | `http://localhost:5175` | local | developer | — |

- **Default environment for UAT test execution**: `TODO (not onboarded)`.
- **Who deploys the trunk**: `TODO (not onboarded)`.
- **Shared-environment hard constraints**: `TODO (not onboarded)`.
- **How to prove a change is deployed**: `TODO (not onboarded)`.
- **Logs** — local (dev): the `./mvnw spring-boot:run` terminal. local (compose): `docker
  compose logs backend` (container `demo-backend-1`).

## Data access

- **Database**: PostgreSQL (see `docker-compose.yml`).
- **Read access**: `TODO (register an MCP server or document the client to use)`.
- **Write/DML access**: `TODO (not onboarded)`.
- **Per-machine config**: `.claude/uat/uat-config.local.json` — create and gitignore it when UAT
  execution starts.
- **Table/naming conventions and query traps**: `TODO (record them as they are found)`.

## Docs & deliverables

### Exemplar documents

None in this repo yet — model new documents on the CSnx repo's exemplars
(`C:\software\projects\csnx\docs\` on this machine), listed in that repo's workflow profile.

## Standards

Shared rulebooks live in the plugin (`${CLAUDE_PLUGIN_ROOT}/skills/workflow-shared/standards/<name>.md`).
Precedence: repo guide > stack file > universal — see *Standards library* in `conventions.md`.

- **Stack files**:
  - `universal: always`
  - `springboot: backend/**`
  - `react: frontend/**`
- **Repo guides** — change kind → guide:

  | Change kind | Guide |
  |---|---|
  | Backend conventions | `AGENTS.md` §Conventions, then `../platform/docs/module-conventions.md` |
  | Frontend conventions | `AGENTS.md` §Conventions (tightens RCT-06/11) |
  | Entity / repository | `../platform/docs/dao-patterns.md` (demo's entities are its worked examples) |
  | Activity service (incl. the self-invoked @Transactional trap) | `../platform/docs/activity-services.md` (tightens SB-06) |
  | DB migration + undo scripts | `docs/migrations.md` (demo values), then `../platform/docs/migrations.md` |
  | Architecture / decisions | `docs/architecture.md`, `docs/decisions.md` |

- *Reusable helpers* and *Constants homes* are inherited from `fleet-profile.md`.
- **Enforced by** — rules the build fails on here; a review still flags a suppression of them:

  | Rule | Enforced by | Proven | Frozen breaches |
  |---|---|---|---|
  | SB-22, one expression only: a literal `.findAll(…)`, `.getResultList()`, `.queryForList(…)` or `.getContent()` (no `{`, `}` or `;` in its arguments) then `.stream()` and, before the next `;`, `filter`/`limit`/`skip`/`findFirst`/`findAny`/`anyMatch`/`noneMatch`/`allMatch`/`count`/`distinct`; or `.findAll(…).subList(…)`. Derived finders (`findAllBy…`, `findBy…`), split statements, loops, `sorted` and `searchForGrid` paging are still reviewed | `backend/src/test/java/za/co/csnx/demo/DataAccessTest.java` `noRowsAreLoadedAndThenReducedInJava` | 2026-10-16 pass/fail/pass | 0 |
  | SB-23, single-class imports only: `org.springframework.jdbc.<Class>`, `javax.sql.DataSource`, or `java.sql` `Connection`/`Statement`/`PreparedStatement`/`CallableStatement`/`ResultSet`/`DatabaseMetaData`, in a file with no `JDBC:` text. Wildcard imports, other `java.sql` types, fully-qualified use, where the tag sits and whether its reason is on the closed list are still reviewed | same, `everyJdbcUseStatesItsReason` | 2026-10-16 pass/fail/pass | 0 |
  | RCT-05, rules-of-hooks only (a hook called conditionally, in a loop or outside a component). `exhaustive-deps` is a warning, so missing deps are still reviewed | `frontend/eslint.config.js` `react-hooks/rules-of-hooks` (`npm run lint`, in CI) | 2026-10-16 pass/fail/pass | 0 |
  | RCT-09, explicit `any`, `@ts-ignore`, `@ts-nocheck` and `@ts-expect-error` without a description only. Whether a description is a real reason, `!` assertions and `as unknown as` casts are still reviewed | same, `@typescript-eslint/no-explicit-any` and `ban-ts-comment` | 2026-10-16 pass/fail/pass | 0 |

## Confluence

- `TODO (not onboarded)` — record the spec space and any customer → space mapping when this
  project starts taking specced work.

## Domain notes & learnings

- This module is a **reference template**: changes here get copied into new modules. See `docs/demo-ops.md`.
- **Existing ops skill** `.claude/skills/deploy` drives the local stack — project ops tooling, not part of the `team-workflow` lifecycle. Leave it alone.

- **Architecture archetypes** (for `estimate` task breakdowns): `TODO — record the recurring
  kinds of change in this codebase as they emerge.`
- **UI automation quirks**: `TODO (record them the first time a ticket is browser-verified)`.
