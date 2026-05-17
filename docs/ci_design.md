# CI Pipeline Design

[Back](../README.md)

## CICD Design

- Key Actions

| Actions              | Tools                                      | Description                   | Custom |
| -------------------- | ------------------------------------------ | ----------------------------- | ------ |
| backend-lint-check   | mvn checkstyle:check                       | coding standards              | \*     |
| backend-build-maven  | mvn package                                | Build Spring Boot artifact    | \*     |
| backend-unit-test    | mvn test                                   | unit test                     | \*     |
| backend-build-image  | docker build                               | Build backend image           |        |
| frontend-lint-check  | npm run lint                               | lint check                    | \*     |
| frontend-unit-test   | npm run test                               | unit test                     | \*     |
| frontend-build-npm   | npm run build                              | Build React production assets | \*     |
| frontend-build-image | docker build                               | Build frontend image          |        |
| dependency-scan      | mvn org.owasp:dependency-check-maven:check | dependency check              | \*     |
| smoke-test           | docker compose                             | smoke-test                    |        |
| image-scan           | trivy image scan                           | image scan                    |        |
| image-push           | docker push                                | image push                    |        |
| notify-slack         | slack                                      | slack notification            | \*     |

---

### Pipelines

- name: 10-ci-backend
- Goal: Ensure non-main branch code quality for backend
- concurrency
  - cancel-in-progress=true
- Trigger
  - push from non-main branchs + change at backend/ path
  - pull request to main branch + change at backend/ path
- Key Jobs
  - parallel
    - backend-lint-check
    - backend-build-maven
    - backend-unit-test
    - backend-build-image
  - notify-slack [ always ]
- Note:
  - no scan for flexibility

---

- name: 20-ci-frontend
- Goal: Ensure non-main branch code quality for frontend
- concurrency
  - cancel-in-progress=true
- Trigger
  - push from non-main branchs + change at frontend/ path
  - pull request to main branch + change at frontend/ path
- Key Jobs
  - parallel
    - frontend-lint-check
    - frontend-unit-test
    - frontend-build-npm
    - frontend-build-image
  - notify-slack [ always ]
- Note:
  - no scan for flexibility

---

- name: 30-cd-build-test
- Goal: Ensure main branch code quality for both ends, security, integrated functions
- Trigger
  - push to `main` + change at frontend/ or backend/ paths
- concurrency
  - group: cd-build-test
  - cancel-in-progress=false
- Key Jobs
  - parallel
    - backend-lint-check
    - backend-build-maven
    - backend-unit-test
    - backend-build-image
    - frontend-lint-check
    - frontend-unit-test
    - frontend-build-npm
    - frontend-build-image
  - parallel
    - dependency-scan: skip now
    - ci-smoke-test: ci-test/docker-compose.yaml
    - backend-image-scan: use image built in preivous job
    - frontend-image-scan: use image built in preivous job
  - parallel
    - backend-image-push(sha)
    - frontend-image-push(sha)
  - notify-slack [ always ]

---

- name: 40-release
- Goal: Release images, request for image update in config repo
- Trigger
  - manual + input(image_name, image_sha, image_tag)
- concurrency
  - cancel-in-progress=false
- Key Jobs
  - verify-image-exists
  - image-retag(image_name, image_sha, image_tag)
  - pr-config-repo
  - notify-slack

```sh
cd app/backend/overlays/dev
kustomize edit set image gitops-demo-backend=docker.io/simonangelfong/gitops-demo-backend:dev-${GIT_SHA}@sha256:${DIGEST}


cd app/frontend/overlays/dev
kustomize edit set image gitops-demo-frontend=docker.io/simonangelfong/gitops-demo-frontend:dev-${GIT_SHA}@sha256:${DIGEST}

```

---

archived

<!--
## Group 1: PR Validation

### pr-check-backend

**Features**

- All jobs run in parallel — no sequential bottleneck
- Stale runs cancelled on new push (`cancel-in-progress: true`)
- Image built and scanned locally — never pushed to registry
- Slack notified on failure

**Jobs**

- `lint-check` — Checkstyle (Maven)
- `dependency-scan` — OWASP Dependency Check, fail on HIGH/CRITICAL
- `unit-test` — JUnit with JaCoCo coverage
- `image-build-scan` — Docker build + Trivy scan, fail on HIGH/CRITICAL (fixable only)
- `notify` — Slack on failure

**Workflow**

```txt
                      pull request (feature-* -> main; path: backend/)
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│  lint-check  │  dependency-scan  │  unit-test  │  image-build-scan  │
└─────────────────────────────────────────────────────────────────────┘
                                   │ any failure
                                   ▼
                              notify (Slack)
```

---

### pr-check-frontend

**Features**

- All jobs run in parallel
- Stale runs cancelled on new push
- Image built and scanned locally — never pushed to registry
- Slack notified on failure

**Jobs**

- `lint-check` — ESLint
- `unit-test` — Vitest with coverage
- `image-build-scan` — Docker build + Trivy scan, fail on HIGH/CRITICAL (fixable only)
- `notify` — Slack on failure

**Workflow**

```txt
                pull request (feature-* -> main; path: frontbend/)
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│  lint-check  │  unit-test  │  image-build-scan  │
└─────────────────────────────────────────────────┘
                        │ any failure
                        ▼
                     notify (Slack)
```

---

## Group 2: Release

### ci-pipeline-build

**Features**

- Detects which component changed — only pushes affected images
- 7 validation jobs run in parallel before smoke test
- Smoke test runs full stack locally via `docker compose` — no registry involved
- Images only pushed to DockerHub after smoke test passes
- Release blocked if any validation or smoke test fails
- Concurrent releases not cancelled — each release runs to completion
- Slack notified on both success and failure

**Jobs**

- `detect-changes` — outputs `backend_changed`, `frontend_changed` from `git diff`
- `backend-lint` — Checkstyle
- `backend-dependency-scan` — OWASP Dependency Check
- `backend-unit-test` — JUnit with JaCoCo coverage
- `backend-image-scan` — Docker build + Trivy scan
- `frontend-lint` — ESLint
- `frontend-unit-test` — Vitest with coverage
- `frontend-image-scan` — Docker build + Trivy scan
- `smoke-test` — `docker compose up --wait` → `curl` → `docker compose down`
- `backend-push` — build + push `backend:<sha>` and `backend:latest` (if backend changed)
- `frontend-push` — build + push `frontend:<sha>` and `frontend:latest` (if frontend changed)
- `notify` — Slack on success and failure

**Workflow**

```txt
                        merge
                          │
                          ▼
                     detect-changes
                          │
          ┌───────────────┼───────────────┐
          │               │               │
  ┌───────┴──────┐        │      ┌────────┴──────┐
  │   Backend    │        │      │    Frontend   │
  │  lint        │        │      │  lint         │
  │  dep-scan    │        │      │  unit-test    │
  │  unit-test   │        │      │  image-scan   │
  │  image-scan  │        │      └───────────────┘
  └───────┬──────┘        │               │
          └───────────────┼───────────────┘
                          │ all must pass
                          ▼
                      smoke-test
                    (local compose)
                          │
               ┌──────────┴──────────┐
               │                     │
          backend-push          frontend-push
         (if changed)           (if changed)
               │                     │
               └──────────┬──────────┘
                          │
                        notify
                  (success or failure)
``` -->
