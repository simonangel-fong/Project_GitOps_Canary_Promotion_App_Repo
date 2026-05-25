# Project GitOps Demo — Backend

**Goal:** Build a containerized Spring Boot REST API that exposes application metadata, a liveness/readiness endpoint, and an env-var dump. The service simulates database connection failures and OOM crashes via env vars, making it the failure-injection backend for a GitOps deployment demo.

---

## Table of Contents

- [Project GitOps Demo — Backend](#project-gitops-demo--backend)
  - [Table of Contents](#table-of-contents)
  - [Module Overview](#module-overview)
  - [Requirements](#requirements)
    - [Functionalities](#functionalities)
    - [Out of Scope](#out-of-scope)
  - [Environment Variables](#environment-variables)
  - [API Endpoints](#api-endpoints)
  - [Failure Simulation](#failure-simulation)
    - [PGDB — Database Connection Error](#pgdb--database-connection-error)
    - [OOM — Out-Of-Memory Crash](#oom--out-of-memory-crash)
  - [Project Structure](#project-structure)
  - [Specification](#specification)
  - [Development Steps](#development-steps)
    - [Step 1 — Scaffold the Spring Boot Project](#step-1--scaffold-the-spring-boot-project)
    - [Step 2 — Bind Env Vars via `AppProperties`](#step-2--bind-env-vars-via-appproperties)
    - [Step 3 — Implement API Endpoints](#step-3--implement-api-endpoints)
    - [Step 4 — Implement Failure Simulators](#step-4--implement-failure-simulators)
    - [Step 5 — Create the Docker Image](#step-5--create-the-docker-image)
  - [Acceptance Criteria](#acceptance-criteria)
  - [Code Validation Check](#code-validation-check)
  - [Local Check before pushing](#local-check-before-pushing)

---

## Module Overview

A minimal Spring Boot REST API with three endpoints:

- `GET /api` — returns the backend version and the frontend background color
- `GET /api/healthz` — liveness/readiness probe; reflects DB connectivity when PGDB is enabled
- `GET /api/env` — returns all six configurable env vars (for inspection by the frontend)

All behavior is driven by environment variables — version, frontend theme color, DB connectivity, and OOM simulation are all flipped from K8s manifests without rebuilding the image.

---

## Requirements

### Functionalities

1. Return version and frontend bg color via `GET /api`
2. Return liveness/readiness via `GET /api/healthz`, reflecting DB state when PGDB is enabled
3. Return all six configured env vars via `GET /api/env`
4. Simulate DB connection failure when `PGDB_ENABLE=true` with an unreachable `PGDB_URL`
5. Simulate OOM crash when `OOM_ENABLE=true`, firing `OOM_TIME` minutes after startup

### Out of Scope

This module is intentionally minimal for GitOps demonstration purposes. The following are excluded from this stage:

- Real database reads/writes (only connection probing)
- Authentication and authorization
- CI/CD pipeline
- Kubernetes manifests, Helm charts, Argo CD configuration
- Monitoring, logging, and production security hardening

---

## Environment Variables

| Variable            | Default                                       | Purpose                                                                       |
| ------------------- | --------------------------------------------- | ----------------------------------------------------------------------------- |
| `FRONTEND_BG_COLOR` | `green`                                       | Background color the frontend reads from `GET /api` to theme its UI           |
| `BACKEND_VERSION`   | `0.1.0`                                       | Version string returned by `GET /api`; shown as the page title in the frontend |
| `PGDB_ENABLE`       | `false`                                       | When `true`, `/api/healthz` probes the database; failure → HTTP 503           |
| `PGDB_URL`          | `jdbc:postgresql://postgres:5432/demo_db`     | JDBC connection string used when `PGDB_ENABLE=true`                           |
| `OOM_ENABLE`        | `false`                                       | When `true`, schedules an OOM crash after startup                             |
| `OOM_TIME`          | `0`                                           | Minutes to wait before triggering OOM (`0` = immediately)                     |

---

## API Endpoints

- GET `/api`

Returns the backend version and the frontend bg color as JSON.

**Default response:**

```json
{
  "version": "0.1.0",
  "bg_color": "green"
}
```

**With `BACKEND_VERSION=1.2.3` and `FRONTEND_BG_COLOR=blue`:**

```json
{
  "version": "1.2.3",
  "bg_color": "blue"
}
```

---

- GET `/api/healthz`

Liveness/readiness probe. Returns HTTP 200 when healthy, HTTP 503 when DB is enabled but unreachable.

**Healthy:**

```json
{ "status": "ok" }
```

**DB unreachable (HTTP 503):**

```json
{ "status": "db_error" }
```

---

- GET `/api/env`

Returns all six configurable env vars as JSON — used by the frontend (and operators) to inspect the deployed config.

```json
{
  "FRONTEND_BG_COLOR": "green",
  "BACKEND_VERSION": "0.1.0",
  "PGDB_ENABLE": false,
  "PGDB_URL": "jdbc:postgresql://postgres:5432/demo_db",
  "OOM_ENABLE": false,
  "OOM_TIME": 0
}
```

---

## Failure Simulation

### PGDB — Database Connection Error

When `PGDB_ENABLE=true`, every call to `/api/healthz` attempts a real JDBC connection to `PGDB_URL`. If the connection fails, the endpoint returns HTTP 503 with `{"status":"db_error"}`, which causes K8s liveness/readiness probes to mark the pod unhealthy.

**Trigger from a K8s manifest:**

```yaml
env:
  - name: PGDB_ENABLE
    value: "true"
  - name: PGDB_URL
    value: "jdbc:postgresql://does-not-exist:5432/demo_db"
```

With `PGDB_ENABLE=false` (the default), the DB is never probed and `/api/healthz` always returns `ok`.

### OOM — Out-Of-Memory Crash

When `OOM_ENABLE=true`, a daemon thread is scheduled at startup to fire after `OOM_TIME` minutes. The thread allocates 10 MB `byte[]` arrays in a loop until the JVM throws `OutOfMemoryError` and the process dies — K8s then restarts the pod, demonstrating a crash-loop / OOMKilled scenario.

**Trigger from a K8s manifest:**

```yaml
env:
  - name: OOM_ENABLE
    value: "true"
  - name: OOM_TIME
    value: "2"   # crash 2 minutes after startup
```

Set `OOM_TIME=0` to crash immediately on startup. With `OOM_ENABLE=false` (the default), the scheduler is never created.

---

## Project Structure

```
backend/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gitops/app/
│   │   │   │   ├── AppApplication.java
│   │   │   │   ├── ApiController.java
│   │   │   │   ├── AppProperties.java
│   │   │   │   ├── PgdbHealthIndicator.java
│   │   │   │   └── OomTrigger.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   │       └── java/com/gitops/app/
│   │           ├── AppApplicationTests.java
│   │           └── ApiControllerTest.java
│   └── pom.xml
├── Dockerfile
└── README.md
```

---

## Specification

| Item              | Value        |
| ----------------- | ------------ |
| Java              | 25           |
| Spring Boot       | 4.0.6        |
| Build tool        | Maven        |
| Project directory | `backend/`   |
| Group ID          | `com.gitops` |
| Artifact ID       | `app`        |
| Package type      | JAR          |
| Default port      | `8080`       |

---

## Development Steps

### Step 1 — Scaffold the Spring Boot Project

Create the Maven project under `backend/app/` using [Spring Initializr](https://start.spring.io/) or the CLI.

| Setting      | Value            |
| ------------ | ---------------- |
| Language     | Java             |
| Build tool   | Maven            |
| Group        | `com.gitops`     |
| Artifact     | `app`            |
| Package name | `com.gitops.app` |
| Packaging    | JAR              |
| Dependency   | Spring Web       |

Add the PostgreSQL JDBC driver as a runtime dependency in `pom.xml` (required for the PGDB connection probe):

```xml
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
```

- [x] Project scaffolded under `backend/app/`
- [x] PostgreSQL JDBC driver added

---

### Step 2 — Bind Env Vars via `AppProperties`

Create `AppProperties.java` annotated with `@ConfigurationProperties(prefix = "app")` to centralize all six env vars in one typed bean. Map them in `application.properties`:

```properties
app.frontend-bg-color=${FRONTEND_BG_COLOR:green}
app.backend-version=${BACKEND_VERSION:0.1.0}
app.pgdb-enable=${PGDB_ENABLE:false}
app.pgdb-url=${PGDB_URL:jdbc:postgresql://postgres:5432/demo_db}
app.oom-enable=${OOM_ENABLE:false}
app.oom-time=${OOM_TIME:0}

# Don't auto-wire a DataSource — we open JDBC connections manually only when probing.
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

- [x] `AppProperties` bean holds all six fields with defaults
- [x] DataSource auto-configuration excluded so the app boots without a DB

---

### Step 3 — Implement API Endpoints

In `ApiController.java`, inject `AppProperties` and `PgdbHealthIndicator` via constructor and expose:

- `GET /api` → `{version, bg_color}`
- `GET /api/healthz` → `{status}` with HTTP 503 when DB probe fails
- `GET /api/env` → all six env-var values

- [x] All three endpoints implemented and covered by `ApiControllerTest`

---

### Step 4 — Implement Failure Simulators

**`PgdbHealthIndicator.java`** — when `PGDB_ENABLE=true`, opens a `DriverManager.getConnection(PGDB_URL)` on each healthz call and returns `false` on `SQLException`. When `PGDB_ENABLE=false`, always returns `true`.

**`OomTrigger.java`** — `@PostConstruct` schedules a daemon task via `ScheduledExecutorService` to fire after `OOM_TIME` minutes; the task allocates `byte[]` arrays in an infinite loop until `OutOfMemoryError` crashes the JVM. Disabled entirely when `OOM_ENABLE=false`.

- [x] `PgdbHealthIndicator` toggles `/api/healthz` between 200/503
- [x] `OomTrigger` crashes the JVM only when explicitly enabled

---

### Step 5 — Create the Docker Image

Create a multi-stage `Dockerfile` at `backend/Dockerfile`:

- **Stage 1 (builder):** Use Maven wrapper to build the JAR
- **Stage 2 (runtime):** Copy the JAR into a minimal JRE image

**Verify locally:**

```sh
cd backend
docker build -t simonangelfong/gitops-demo-backend .
docker run -d --name gitops-demo-backend \
  -e BACKEND_VERSION=1.2.3 \
  -e FRONTEND_BG_COLOR=blue \
  -p 8080:8080 \
  simonangelfong/gitops-demo-backend

curl http://localhost:8080/api
# {"version":"1.2.3","bg_color":"blue"}

curl http://localhost:8080/api/healthz
# {"status":"ok"}

curl http://localhost:8080/api/env
# {"FRONTEND_BG_COLOR":"blue","BACKEND_VERSION":"1.2.3","PGDB_ENABLE":false,...}

docker push simonangelfong/gitops-demo-backend
```

- [x] Image builds successfully
- [x] Container starts and responds on port 8080
- [x] `/api`, `/api/healthz`, `/api/env` return the expected JSON

---

## Acceptance Criteria

| #   | Criterion                                                                             | Status |
| --- | ------------------------------------------------------------------------------------- | ------ |
| 1   | `GET /api` returns `{"version":"0.1.0","bg_color":"green"}` by default                | Done   |
| 2   | `GET /api` reflects custom `BACKEND_VERSION` and `FRONTEND_BG_COLOR` at runtime       | Done   |
| 3   | `GET /api/healthz` returns `{"status":"ok"}` with HTTP 200 when PGDB is disabled      | Done   |
| 4   | `GET /api/healthz` returns HTTP 503 when `PGDB_ENABLE=true` and the DB is unreachable | Done   |
| 5   | `GET /api/env` returns all six configured env-var values                              | Done   |
| 6   | `OOM_ENABLE=true` with `OOM_TIME=N` crashes the JVM after N minutes                   | Done   |
| 7   | Application starts on port `8080`                                                     | Done   |
| 8   | Docker image builds and container runs successfully                                   | Done   |

---

## Code Validation Check

```sh
cd backend/app

# coding standards and style rules
mvn checkstyle:check
# Security Scanning
mvn org.owasp:dependency-check-maven:10.0.4:check
# unit test
mvn test

docker build -t simonangelfong/gitops-demo-backend .
docker run --rm -d --name gitops-test -p 8080:8080 simonangelfong/gitops-demo-backend
curl http://localhost:8080/api/healthz
# {"status":"ok"}
docker stop gitops-test

# image-scan
trivy image simonangelfong/gitops-demo-backend

docker compose -f ci-test/docker-compose.yaml up -d --build
docker compose -f ci-test/docker-compose.yaml down -v

cd backend/
docker build -t simonangelfong/gitops-demo-backend .
docker push simonangelfong/gitops-demo-backend
```

## Local Check before pushing

Run these from `backend/app/` in order. Stop at the first failure and fix it before pushing.

```sh
cd backend/app

# 1. Clean old build artifacts (target/).
mvn clean

# 2. Compile the source — fastest way to catch syntax/type errors.
mvn compile

# 3. Run unit tests.
mvn test

# 4. Full verify: compile + test + checkstyle + jacoco coverage gate (70%).
#    This is the closest local equivalent to what CI runs on PR.
mvn verify

# 5. Optional — security scan (slow, downloads NVD feed on first run).
#    CI runs this on main; only run locally if you've touched dependencies.
mvn -Psecurity dependency-check:check
```

Shortcuts:

```sh
# Skip tests when you only want to check the build compiles and packages.
mvn verify -DskipTests

# Parallel build across cores (faster on multi-module / large projects).
mvn -T 1C verify
```

Also worth checking before pushing:

```sh
# Confirm nothing is staged that shouldn't be (secrets, target/, IDE files).
git status
git diff --staged

# Rebase on latest main to catch merge conflicts locally, not in the PR.
git fetch origin
git rebase origin/main
```
