# Project GitOps Demo — Frontend

**Goal:** Build a containerized React application that fetches and displays the application version from the backend REST API. This service is the frontend component of a GitOps deployment demo.

---

## Table of Contents

- [Project GitOps Demo — Frontend](#project-gitops-demo--frontend)
  - [Table of Contents](#table-of-contents)
  - [Module Overview](#module-overview)
  - [Requirements](#requirements)
    - [Functionalities](#functionalities)
    - [Out of Scope](#out-of-scope)
  - [UI Specification](#ui-specification)
  - [Project Structure](#project-structure)
  - [Specification](#specification)
  - [Development Steps](#development-steps)
    - [Step 1 — Scaffold the React Project](#step-1--scaffold-the-react-project)
    - [Step 2 — Implement the Page Layout](#step-2--implement-the-page-layout)
    - [Step 3 — Connect to the Backend](#step-3--connect-to-the-backend)
    - [Step 4 — Create the Docker Image](#step-4--create-the-docker-image)
    - [Step 5 — Run with Docker Compose](#step-5--run-with-docker-compose)
  - [Acceptance Criteria](#acceptance-criteria)
  - [Code Validation Check](#code-validation-check)

---

## Module Overview

A minimal React single-page application with one page:

- Fetches `{version, bg_color}` from `GET /api` on the backend
- Displays the version in a centered title: `GitOps Demo App - <version>`
- Paints the full-page background using the `bg_color` returned by the backend

The nginx container also serves `GET /healthz` directly (no upstream call) for K8s liveness/readiness probes — the frontend is considered healthy as long as nginx is serving static assets, independent of the backend.

This makes it easy to verify that different deployments serve different versions and theme colors end-to-end, without changing any application code.

---

## Requirements

### Functionalities

1. On load, fetch `{version, bg_color}` from the backend `GET /api` endpoint
2. Render the version in the page title: `GitOps Demo App - <version>`
3. Paint the full-page background using `bg_color` from the response, with the title centered on the page
4. Expose `GET /healthz` on the nginx container, returning HTTP 200 `ok` independently of the backend, for K8s liveness/readiness probes

### Out of Scope

This module is intentionally minimal for GitOps demonstration purposes. The following are excluded from this stage:

- User authentication
- Routing or multi-page layout
- State management libraries
- Backend integration beyond the version endpoint
- Production performance optimization

---

## UI Specification

| Element    | Value                                                      |
| ---------- | ---------------------------------------------------------- |
| Layout     | Single centered title, full-page                           |
| Background | Driven by `bg_color` from `GET /api` (defaults to green)   |
| Title      | `GitOps Demo App - <version>`                              |
| Version    | Fetched from backend `GET /api` (`version` field)          |
| Fallback   | Show `loading...` while fetching; `unavailable` on failure |

---

## Project Structure

```
frontend/
├── src/
│   ├── App.jsx
│   └── main.jsx
├── index.html
├── package.json
├── vite.config.js
├── Dockerfile
└── README.md
```

---

## Specification

| Item              | Value       |
| ----------------- | ----------- |
| Framework         | React       |
| Language          | JavaScript  |
| Build tool        | Vite        |
| Project directory | `frontend/` |
| Backend URL       | `/api`      |
| Default port      | `8080`      |
| Production server | nginx       |

---

## Development Steps

### Step 1 — Scaffold the React Project

Create the Vite + React project under `frontend/`.

```sh
npm create vite@latest frontend/app -- --template react
cd frontend/app
npm install
```

- [x] Project scaffolded under `frontend/`
- [x] Dev server starts successfully

---

### Step 2 — Implement the Page Layout

Edit `App.jsx` to render a full-page background with a centered title.

Expected layout:

- Background: green (`#4caf50` or equivalent) — used as the default until the backend response arrives
- Title: `GitOps Demo App - 0.1.0` (hardcoded for now)
- Title is centered horizontally and vertically

```sh
cd frontend/app/
npm run dev
```

- [x] Green background renders correctly
- [x] Title is centered on the page

---

### Step 3 — Connect to the Backend

Replace the hardcoded version with a `fetch` call to `GET /api` on page load. The response body is `{version, bg_color}` — use `version` for the title and `bg_color` to drive the page background.

```jsx
const [version, setVersion] = useState("loading...");
const [bgColor, setBgColor] = useState(null);

useEffect(() => {
  fetch("/api")
    .then((res) => res.json())
    .then((data) => {
      setVersion(data.version);
      if (data.bg_color) setBgColor(data.bg_color);
    });
}, []);
```

Apply `bg_color` as an inline style on the page container so the CSS default still applies until the response arrives:

```jsx
<div className="page" style={bgColor ? { backgroundColor: bgColor } : undefined}>
  <h1>GitOps Demo App - {version}</h1>
</div>
```

```sh
cd frontend/app
npm run dev

curl -i http://localhost:5173/
```

- [x] Version is fetched from the backend on load
- [x] Title reflects the backend `version` value
- [x] Page background reflects the backend `bg_color` value

---

### Step 4 — Create the Docker Image

Create a multi-stage `Dockerfile` at `frontend/Dockerfile`:

- **Stage 1 (builder):** Install dependencies and build static assets with Vite
- **Stage 2 (runtime):** Serve the built assets via nginx

**nginx proxy configuration** — forward `/api` requests to the backend so the frontend container does not need to know the backend's host at build time, and expose `/healthz` directly for K8s probes:

```nginx
server {
    listen 80;

    root /usr/share/nginx/html;
    index index.html;

    location = /healthz {
        access_log off;
        add_header Content-Type text/plain;
        return 200 "ok\n";
    }

    location /api {
        proxy_pass ${BACKEND_URL};
    }

    location / {
        try_files $uri /index.html;
    }
}
```

`/healthz` is served by nginx itself — it does **not** proxy to the backend. A backend outage must not restart the frontend pod.

**Verify locally** (requires the backend container to be running):

```sh
cd frontend
docker build -t simonangelfong/gitops-demo-frontend .
docker run --rm -d --name gitops-frontend -p 8000:8080 simonangelfong/gitops-demo-frontend

curl -i http://localhost:8000
curl -i http://localhost:8000/healthz
# Page displays: GitOps Demo App - 0.1.0
```

- [x] Image builds successfully

---

### Step 5 — Run with Docker Compose

Wire the frontend and backend together using `docker-compose.yml` at the project root.

```sh
# default version
docker compose -f ci-test/docker-compose.yaml up -d --build
docker compose -f ci-test/docker-compose.yaml ps

curl -i http://localhost:8000/healthz
curl -i http://localhost:8000/api 

docker compose -f ci-test/docker-compose.yaml down -v

# custom version
APP_VERSION=1.2.3 docker compose up --build

```

- [x] Both services start via `docker compose up`
- [x] Page displays the version matching `APP_VERSION`
- [x] Container starts and serves the page on port `8080`
- [x] Page displays the version fetched from the backend

---

## Acceptance Criteria

| #   | Criterion                                                              | Status |
| --- | ---------------------------------------------------------------------- | ------ |
| 1   | React app scaffolded and dev server starts                             | Done   |
| 2   | Page renders backend-driven `bg_color` background with centered title  | Done   |
| 3   | Version is fetched from `GET /api` and rendered in the title           | Done   |
| 4   | Docker image builds and container serves the page on port `8080`       | Done   |
| 5   | Full stack runs via `docker compose up` with correct version displayed | Done   |
| 6   | `GET /healthz` returns HTTP 200 `ok` from nginx, independent of backend | Done   |

---

## Code Validation Check

```sh
cd backend/app

# coding standards and style rules
mvn checkstyle:check
# Security Scanning
mvn dependency-check:check
# unit test
mvn test
# image-scan
trivy image gitops-backend
```

```sh
cd frontend/app

# lint check
npm run lint
# unit test
npm test
npm run build

# image-scan
docker build -t gitops-frontend .
trivy image simonangelfong/gitops-demo:frontend-latest


docker compose up -d --build
docker compose down -v



cd backend/
docker build -t simonangelfong/gitops-demo-backend .
docker push simonangelfong/gitops-demo-backend

cd frontend/
docker build -t simonangelfong/gitops-demo-frontend .
docker push simonangelfong/gitops-demo-frontend
```
