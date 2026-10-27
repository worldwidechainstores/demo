# demo — reference Spring Boot module

Reference implementation of the CSnx lightweight module stack (CSNX-13935).
End-to-end webapp: customer registration → login → landing page showing the
authenticated user's info.

## Stack

**Backend** — Java 21, Spring Boot 3.5, Maven, Spring Data JPA + Hibernate,
Spring Security + JWT, Flyway, Log4j 2, springdoc-openapi, MapStruct,
PostgreSQL, Testcontainers, JUnit 5 + Mockito + AssertJ.

**Frontend** — React 18 + TypeScript, Vite, shadcn-style UI primitives
(Tailwind), React Router, TanStack Query, react-hook-form + zod.

**Container** — backend Dockerfile (or `./mvnw spring-boot:build-image`);
frontend multi-stage → nginx; `docker-compose.yml` boots Postgres + backend +
frontend together.

## Layout

```
backend/        Maven Spring Boot project, Java 21
  src/main/java/za/co/csnx/demo/...
  src/main/resources/application.yml + db/migration/
  pom.xml, mvnw, Dockerfile
frontend/       Vite + React + TypeScript
  src/{pages,components/ui,lib}/
  package.json, vite.config.ts, Dockerfile, nginx.conf
docker-compose.yml
```

## Prerequisites

- **Local dev**: Java 21 (Temurin recommended), Node 20.19+/22+ (for Vite 5),
  Docker (for Postgres or full compose).
- **Containerized**: only Docker.

## Run locally (dev mode)

### 1. Start Postgres

```bash
docker run --rm -d --name demo-pg \
  -e POSTGRES_DB=demo -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo \
  -p 5435:5432 postgres:16-alpine   # the dev datasource default is localhost:5435/demo
```

### 2. Backend

```bash
cd backend
JAVA_HOME=<path-to-jdk21> ./mvnw spring-boot:run
```

- API: <http://localhost:8092/api/...>
- Swagger UI: <http://localhost:8092/swagger-ui.html>
- Actuator health: <http://localhost:8092/actuator/health>

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

- App: <http://localhost:5175>
- Vite proxies `/api` → `http://localhost:8092`.

## Run end-to-end (Docker)

```bash
docker compose up --build
```

- Frontend: <http://localhost:8085>
- Backend: <http://localhost:8092>
- Postgres: localhost:5435 (demo / demo)

## Tests

```bash
cd backend
JAVA_HOME=<path-to-jdk21> ./mvnw test
```

Includes unit tests (`JwtServiceTest`, `AuthServiceTest`) and an integration
test (`DemoBackendApplicationTests`) that boots the full Spring context against
one shared Testcontainers Postgres server (shape: [`module-conventions.md`](../platform/docs/module-conventions.md#backend), Tests).

## API

| Method | Path                  | Description                         | Auth |
|--------|-----------------------|-------------------------------------|------|
| POST   | `/api/auth/register`  | Create a customer                   | none |
| POST   | `/api/auth/login`     | Get access JWT + refresh cookie     | none |
| POST   | `/api/auth/refresh`   | Exchange refresh cookie for new JWT | none |
| POST   | `/api/auth/logout`    | Clear refresh cookie                | none |
| GET    | `/api/me`             | Authenticated customer's profile    | JWT  |

## Scaffolding a new module from this template

1. Copy this directory to `C:\software\projects\modules\<name>` and `git init` a
   new repo there.
2. Rename the Maven `artifactId` and Java package `za.co.csnx.demo` →
   `za.co.csnx.<name>`.
3. Update `application.yml` (database name, JWT issuer) and the Flyway baseline
   migration to your domain's tables.
4. Update `docker-compose.yml` service names and the frontend's `nginx.conf`
   `proxy_pass` target.
5. Replace the demo screens with module-specific ones — the auth foundation,
   `BaseEntity`, `BaseRepository`, `GlobalExceptionHandler` and shadcn primitives
   carry over unchanged.

## Engine + screen authoring

The frontend is a metadata-driven UI engine — workflows live as JSON under
`backend/src/main/resources/screens/<workflow>.json` and the shadcn
primitives are leaf renderers behind the engine's `FieldRenderer`. Two
screen families ship today: **RPTM** (single-grid CRUD) and **COSF + CSFD**
(master-detail with a Trader picker). Add a new screen by writing the JSON
+ matching activity service — no Flyway migration unless you're also
shipping a new fastpath/menu entry.

## Docs

- [CLAUDE.md](CLAUDE.md) — project rules and conventions.
- [docs/architecture.md](docs/architecture.md) — repo shape, packages, ports, env vars, CDC + event flows.
- [docs/migrations.md](docs/migrations.md) — this module's migration values and invariants.
- [docs/decisions.md](docs/decisions.md) — rationale behind every stack choice.
- Fleet-canonical (platform repo, cloned alongside): [engine.md](../platform/docs/engine.md), [activity-services.md](../platform/docs/activity-services.md), [dao-patterns.md](../platform/docs/dao-patterns.md), [migrations.md](../platform/docs/migrations.md), [platform-architecture.md](../platform/docs/platform-architecture.md), [fleet.md](../platform/docs/fleet.md) (ports).
- [docs/demo-ops.md](docs/demo-ops.md) — GCP deployment runbook.
