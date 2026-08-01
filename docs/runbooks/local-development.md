# Local Development Runbook

## Prerequisites

Install Git, Java 21, Node.js 24, pnpm 11.9, and Docker Engine with Compose v2.
Confirm Docker has enough resources for a PostgreSQL container and application
image builds.

## Complete container startup

```bash
git clone https://github.com/Yanziki/enterprise-ai-knowledge-ops-platform.git
cd enterprise-ai-knowledge-ops-platform
cp .env.example .env
docker compose config
docker compose up --build --wait
curl --fail http://localhost:8080/api/v1/system/status
curl --fail http://localhost:8080/actuator/health/readiness
open http://localhost:8080
```

Stop the stack with `docker compose down`.

## Host development

Start PostgreSQL and export the values from `.env`, then:

```bash
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

cd apps/web
pnpm install --frozen-lockfile
pnpm dev
```

Vite proxies `/api` and `/actuator` to `http://localhost:8081`; no wildcard CORS
configuration is required.

## Common failures

- **Docker daemon unavailable:** start Docker Desktop/Engine and rerun
  `docker info`. Backend integration tests require Docker.
- **Database unhealthy:** inspect `docker compose logs postgres`; verify the
  credentials in `.env` match the API variables.
- **API startup configuration error:** define nonblank `DB_NAME`, `DB_USERNAME`,
  and `DB_PASSWORD`, and a valid JDBC `DB_URL`.
- **Port in use:** stop the conflicting process or change host ports in `.env`.
- **Frontend reports unavailable:** call the status URL directly and inspect web
  and API logs. The production browser must use the Nginx origin.
- **Stale dependencies:** use the committed Maven wrapper and
  `pnpm install --frozen-lockfile`; never hand-edit the lockfile.

## Database inspection

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
```

## Reset procedure

The following deletes only this Compose project's named database volume:

```bash
docker compose down --volumes
docker compose up --build --wait
```

Back up any needed local data first. The volume deletion is not recoverable.

## Logs

Container logs are written to the Docker logging driver and viewed with
`docker compose logs --follow [postgres|api|web]`. Host API logs go to its
terminal; Vite development logs go to the frontend terminal. Day 1 does not
configure external log shipping.
