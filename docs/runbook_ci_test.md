# Runbook - Integrationg Test

```sh
docker compose -f ci-test/docker-compose.yaml up -d --build
docker compose -f ci-test/docker-compose.yaml down -v

# #######################
# enable db
# #######################
curl -i localhost:8080/api/healthz
# HTTP/1.1 503
# Content-Type: application/json
# Transfer-Encoding: chunked
# Date: Mon, 25 May 2026 22:17:31 GMT
# Connection: close
# {"status":"db_error"}

# #######################
# enable oom 1m
# #######################
curl -i localhost:8080/api/healthz

```
