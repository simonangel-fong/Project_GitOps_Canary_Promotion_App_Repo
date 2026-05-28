# GitOps Canary Promotion - Application Repo

**End to End. Auto-Promoted. Canary-Released**

> A production-style GitOps project that separates application, infrastructure, and platform delivery across 3 repositories. <br>
> It uses EKS, ArgoCD, Argo Rollouts, Terraform, and GitHub Actions to automate environment-based deployments, canary promotion, rollback, and post-deployment monitoring and alerting.

![Git](https://img.shields.io/badge/git-%23F05033.svg?style=for-the-badge&logo=git&logoColor=white&style=plastic) ![Argo CD](https://img.shields.io/badge/Argo%20CD-EF7B4D?style=for-the-badge&logo=argo&logoColor=white&style=plastic) ![Argo Rollouts](https://img.shields.io/badge/Argo%20Rollouts-EF7B4D?style=for-the-badge&logo=argo&logoColor=white&style=plastic) ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white&style=plastic) ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white&style=plastic) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white&style=plastic) ![Alertmanager](https://img.shields.io/badge/Alertmanager-E6522C?style=for-the-badge&logo=prometheus&logoColor=white&style=plastic) ![Slack](https://custom-icon-badges.demolab.com/badge/Slack-4A154B?logo=slack&logoColor=fff) <br>
![AWS](https://img.shields.io/badge/AWS-FF9900?style=for-the-badge&logo=amazonwebservices&logoColor=white&style=plastic) ![Amazon EKS](https://img.shields.io/badge/Amazon%20EKS-FF9900?tyle=for-the-badge&logo=amazoneks&logoColor=white&style=plastic) ![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white&style=plastic) ![Terraform](https://img.shields.io/badge/Terraform-7B42BC?style=for-the-badge&logo=terraform&logoColor=white&style=plastic) ![Kustomize](https://img.shields.io/badge/Kustomize-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white&style=plastic) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white&style=plastic) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?logo=springboot&logoColor=fff&style=plastic&style=plastic) ![React](https://img.shields.io/badge/React-%2320232a.svg?logo=react&logoColor=%2361DAFB) <br>

- [GitOps Canary Promotion - Application Repo](#gitops-canary-promotion---application-repo)
  - [1. Why This Project Exists](#1-why-this-project-exists)
    - [1.1 Managing App, Infrastructure, and Platform Changes](#11-managing-app-infrastructure-and-platform-changes)
    - [1.2 Releasing Safely Without Business Interruption](#12-releasing-safely-without-business-interruption)
  - [2. Project Architecture](#2-project-architecture)
  - [3. What This Application Repo Manages](#3-what-this-application-repo-manages)
    - [3.1 Full-Stack Web Application](#31-full-stack-web-application)
    - [3.2 Multi-Stage Docker Build](#32-multi-stage-docker-build)
    - [3.3 Automated CI/CD Pipelines and Image Delivery](#33-automated-cicd-pipelines-and-image-delivery)
  - [4. Operational Runbooks](#4-operational-runbooks)

---

## 1. Why This Project Exists

### 1.1 Managing App, Infrastructure, and Platform Changes

**Challenge:**

- In enterprise environments, _application code_, _cloud infrastructure_, and _Kubernetes platform configuration_ are often owned by different roles.
- Without clear separation and automated GitOps workflows, delivery can become slow, inconsistent, and difficult to audit.

**Solution:**

- This project uses a `3-repo GitOps strategy` to separate application, infrastructure, and platform responsibilities.
- `CI/CD pipelines` automate validation, image delivery, infrastructure provisioning, and manifest updates, making the delivery process more traceable and repeatable.

---

### 1.2 Releasing Safely Without Business Interruption

**Challenge:**

- Direct production releases increase the risk of downtime, failed deployments, and slow recovery.
- Without a controlled deployment strategy, issues may only be detected after they affect users.

**Solution:**

- This project implements `canary deployment` across isolated `dev`, `stage`, and `prod` environments.
- `Argo Rollouts`, automated analysis, monitoring, and rollback logic help detect issues early, reduce release risk, and protect business continuity.

---

## 2. Project Architecture

- 3-repo GitOps model to separate application delivery, infrastructure provisioning, and platform configuration.

```txt
                                      End Users
                                          |
                                          v
        +---------------------------------------------------------------------+
        |                            EKS Runtime                              |
        |                                                                     |
        |  Applications: Frontend App, Backend App                            |
        |                                                                     |
        |  Platform Add-ons: ESO, Karpenter, ALBC, Envoy, ExternalDNS         |
        |  Delivery & Observability: Argo Rollouts, Prometheus,               |
        |  Alertmanager, Slack Notifications                                  |
        +---------------------------------------------------------------------+
                                            ^
                                            |
                  +-------------------------+------------------------+
                  ^                         ^                        ^
                  |                         |                        |
             Provisioning            Container Image         GitOps Sync / Rollout
                  |                         |                        |
        +---------------------+  +---------------------+   +---------------------+
        |Infrastructure Repo  |  | Application Repo    |   | Platform Repo       |
        |                     |  |                     |   |                     |
        | Terraform           |  |App source code      |   | GitOps manifests    |
        | AWS / EKS clusters  |  | Docker build        |   | App-of-Apps         |
        | ArgoCD install      |  |  CI pipeline        |   | Add-ons / apps      |
        +---------------------+  +---------------------+   +---------------------+
                  ^                        ^                         ^
                  |                        |                         |
             Cloud Engineer             Developer            Platform Engineer

```

| Repository                                                                                                     | Main responsibility                                                                |
| -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| [Platform Repository](https://github.com/simonangel-fong/Project_GitOps_Canary_Promotion_Platform_Repo.git)    | Add-ons, app manifests, sync waves, canary rollout, monitoring, Slack notification |
| [Application Repository](https://github.com/simonangel-fong/Project_GitOps_Canary_Promotion_App_Repo.git)      | Source code, Docker image build, image push, manifest/image update trigger         |
| [Infrastructure Repository](https://github.com/simonangel-fong/Project_GitOps_Canary_Promotion_Infra_Repo.git) | AWS, EKS clusters, ArgoCD installation, networking foundation                      |

---

## 3. What This Application Repo Manages

### 3.1 Full-Stack Web Application

This repo contains a simple full-stack web application used as the deployable workload for the GitOps canary promotion workflow.

| Layer            | Technology  | Responsibility                                                     |
| ---------------- | ----------- | ------------------------------------------------------------------ |
| Backend          | Spring Boot | Provides RESTful APIs for the application                          |
| Frontend         | React       | Provides the user-facing web UI                                    |
| Containerization | Docker      | Packages the frontend and backend into deployable container images |

---

### 3.2 Multi-Stage Docker Build

Multi-stage Docker builds separate build-time dependencies from runtime images, reducing image size and limiting what is shipped to production.

| Service  | Build Stage                                          | Runtime Stage                                | Benefit                                                                                |
| -------- | ---------------------------------------------------- | -------------------------------------------- | -------------------------------------------------------------------------------------- |
| Backend  | Maven builds the Spring Boot `.jar` with a JDK image | Runs the `.jar` with a lightweight JRE image | Removes build tools from the runtime image and reduces image size                      |
| Frontend | Node.js builds static React assets                   | Nginx serves the compiled static files       | Removes Node/npm from runtime and uses an unprivileged Nginx image for safer execution |

---

### 3.3 Automated CI/CD Pipelines and Image Delivery

This repo uses CI/CD pipelines to validate application changes, build container images, scan artifacts, and hand off release versions to the Platform Repo for GitOps deployment.

| Stage                  | Automated Jobs                                                                             | Purpose                                                                                        |
| ---------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Code validation        | Lint check, unit test, dependency scan                                                     | Catch code quality, test, and dependency issues before building images                         |
| Integration validation | Docker Compose integration test                                                            | Verify the frontend, backend, and dependent services can run together before release           |
| Image delivery         | Docker build, image scan, Docker push                                                      | Build trusted application images, scan for vulnerabilities, and publish images to the registry |
| Release version update | Human approval, image retag, commit updated image tag to Platform Repo Kustomize manifests | Control release version handoff while keeping deployment managed by GitOps                     |

---

## 4. Operational Runbooks

- [Debugging CI/CD Pipeline Failures](docs/runbook_cicd.md):
  - Diagnose lint check, unit test, dependency scan, image scan, Docker Compose integration test, and Docker push failures
