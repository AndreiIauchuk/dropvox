# dropvox

`dropvox` is a Java 21 multi-module Maven project for file upload/download workflows.

It is split into three services:

- `dropvox-gateway`: public API entrypoint and OpenAPI-based routing.
- `dropvox-metadata`: file lifecycle, PostgreSQL metadata, MinIO presigned URLs.
- `dropvox-auth`: lightweight auth stub used by gateway integrations.

## Architecture

### Services at a glance

- **Gateway (`:8080` in Docker Compose)**
  - Validates incoming requests from `dropvox-gateway/src/main/resources/swagger/openapi.json`.
  - Forwards business operations to metadata and propagates `X-Trace-Id`.
- **Metadata (`:8082` in Docker Compose)**
  - Owns file lifecycle states (`PENDING`, `UPLOADED`, `FAILED`).
  - Runs Flyway migrations and manages PostgreSQL + MinIO integration.
- **Auth (`:8081`, currently stubbed and disabled in compose by default)**
  - Token validation placeholder for local/dev flows.

### Upload flow (current behavior)

1. `POST /files/init` creates a metadata row in `PENDING` and returns a presigned upload URL.
2. Client uploads directly to MinIO with the presigned PUT URL.
3. `POST /files/:fileId/completion-request` returns `202 PROCESSING` and triggers async verification.
4. Metadata transitions status to `UPLOADED` (success) or `FAILED` (terminal miss).
5. `GET /files/:fileId/status` returns lifecycle state.

## Diagrams

Project diagrams live in `diagrams/`:

- [High-level architecture](diagrams/highlevel_structural.puml)
- [Upload sequence](diagrams/upload_flow_seq.puml)
- [Download sequence](diagrams/download_flow_seq.puml)

## Repository layout

- `dropvox-gateway/` - API gateway service
- `dropvox-metadata/` - metadata and storage orchestration service
- `dropvox-auth/` - auth stub service
- `perf/jmeter/` - performance tests and JMeter plan
- `observability/` - Prometheus/Loki/Alloy/Grafana configs
- `docker-compose.yaml` - local multi-service environment

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

## Quick start (local)

```powershell
mvn test
```

```powershell
docker compose up --build
```

Useful local endpoints after startup:

- Gateway API: `http://localhost:8080`
- OpenAPI JSON: `http://localhost:8080/docs/openapi.json`
- Swagger UI assets: `http://localhost:8080/docs/swagger-ui/`
- MinIO console: `http://localhost:9001`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`

## Testing

Run all tests from the repo root:

```powershell
mvn test
```

Focused tests:

- `dropvox-gateway/src/test/java/com/iovchukandrew/dropvox/gateway/server/DeployServerTest.java`
- `dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/db/FilesDAOTest.java`
- `dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/IntegrationTest.java`

## Performance testing

JMeter instructions and plan details are in:

- [`perf/jmeter/README.md`](perf/jmeter/README.md)

Main test plan:

- `perf/jmeter/dropvox-files-flow.jmx`

## Build artifacts

Each module is configured to produce a shaded runnable JAR via Maven Shade Plugin.
