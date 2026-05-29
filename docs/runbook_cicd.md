# Runbook - Debugging CI/CD Pipeline Failures

[Back](../README.md)

- [Runbook - Debugging CI/CD Pipeline Failures](#runbook---debugging-cicd-pipeline-failures)
  - [Reusable Actions](#reusable-actions)
  - [CI/CD Pipelines](#cicd-pipelines)

---

## Reusable Actions

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
| integration test           | docker compose                             | integration test                    |        |
| image-scan           | trivy image scan                           | image scan                    |        |
| image-push           | docker push                                | image push                    |        |
| notify-slack         | slack                                      | slack notification            | \*     |

---

## CI/CD Pipelines

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
    - dependency-scan
    - ci-integration test: ci-test/docker-compose.yaml
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
