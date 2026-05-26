FROM node:24.15.0-trixie-slim@sha256:4f2b45e32dc7d2caf66b6dbd59fac50e32f8077769efe0ef4d4c3f114672537d AS builder
WORKDIR /app
COPY app/package.json app/package-lock.json ./
RUN npm ci
COPY app/ .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine3.23@sha256:8122337ed6c475bb486bc9340da453d4599f225e6b920ff0d92ca2267486b9b5
WORKDIR /usr/share/nginx/html
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_URL=http://backend:8080
ENV DNS_RESOLVER=127.0.0.11
ENV NGINX_ENVSUBST_FILTER='BACKEND_URL|DNS_RESOLVER'
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

