# AGENTS.md

## Project overview
- `dropvox` is a Java 21 multi-module Maven repo with three services: `dropvox-gateway`, `dropvox-metadata`, and `dropvox-auth` (root `pom.xml`).
- Runtime architecture is: gateway exposes the public API, metadata owns PostgreSQL + MinIO interactions, auth is currently a lightweight stub.
- Main entrypoints: `dropvox-gateway/.../GatewayMain.java`, `dropvox-metadata/.../MetadataMain.java`, `dropvox-auth/.../AuthMain.java`.

## Service boundaries and request flow
- `dropvox-gateway` is the front door. It mounts handlers by OpenAPI `operationId` from `dropvox-gateway/src/main/resources/swagger/openapi.json` inside `dropvox-gateway/src/main/java/com/iovchukandrew/dropvox/gateway/server/Server.java`.
- Gateway handlers should stay thin: they authenticate via `AuthServiceClient` and proxy business operations through `MetadataServiceClient`.
- `dropvox-metadata` owns file lifecycle state and presigned URLs. Its HTTP routes are wired directly in `dropvox-metadata/src/main/java/com/iovchukandrew/dropvox/metadata/server/Server.java`.
- Current upload flow is: `POST /files/init` -> DB row in `PENDING` -> client uploads directly to MinIO via presigned PUT -> `POST /files/complete/:fileId` verifies object existence -> DB row becomes `UPLOADED` -> `GET /files/:fileId` returns presigned GET URL.
- The best executable reference for the full flow is `dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/IntegrationTest.java`.

## Data and storage conventions
- Metadata service runs Flyway on startup (`MetadataMain` -> `FlywayRunner`) before opening the HTTP server.
- PostgreSQL schema defaults to `metadata`; `PgPoolCreator` also sets PostgreSQL `search_path` from `db.scheme`, so SQL intentionally uses unqualified table names like `files`.
- Migration `dropvox-metadata/src/main/resources/db/migration/V1__create_files_table.sql` defines only two file states: `PENDING` and `UPLOADED`.
- S3 object keys are intentionally user-scoped and file-scoped: `users/{userUuid}/files/{fileUuid}/{sanitizedFilename}` in metadata `FileUploadInitHandler`.
- API JSON naming is not raw DB naming: DAO responses use keys like `fileId`, `ownerId`, `s3Key`, `uploadedAt`, `lastModifiedAt` (`FilesDAO.mapRowToJson`).

## Config and environment behavior
- Gateway and metadata both normalize env vars by lowercasing and replacing `_` with `.`. Example: `METADATA_SERVICE_HOST` becomes `metadata.service.host`; `SERVER_PORT` becomes `server.port`.
- Default local values live in `dropvox-gateway/src/main/resources/application.properties` and `dropvox-metadata/src/main/resources/application.properties`.
- `docker-compose.yaml` is the practical source of truth for local wiring: PostgreSQL, MinIO, gateway, metadata, Prometheus, Loki, Alloy, and Grafana.
- `dropvox-auth` is commented out in `docker-compose.yaml`; do not assume auth is part of the default local compose flow.

## HTTP and cross-service patterns
- Trace propagation matters here: services accept or generate `X-Trace-Id`, store it in MDC, and echo it back in responses; gateway forwards it downstream in `MetadataServiceClient.withTrace(...)`.
- Metadata expects user identity in `X-User-Id` and validates UUIDs before processing requests.
- Gateway request validation is OpenAPI-driven. If you add or rename an endpoint, update both the OpenAPI spec and the `operationId` mounting in gateway `Server.java`.
- Gateway converts OpenAPI validation failures to `400`; other uncaught failures generally surface as `500`.

## Developer workflows
- Full repo build/test from root: `mvn test` or `mvn package`.
- Each module builds a shaded runnable jar; main classes are configured in each module `pom.xml`.
- Useful focused tests:
  - `dropvox-gateway/src/test/java/com/iovchukandrew/dropvox/gateway/server/DeployServerTest.java` verifies docs/OpenAPI serving.
  - `dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/db/FilesDAOTest.java` covers DAO behavior and status transitions.
  - `dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/IntegrationTest.java` covers Postgres + MinIO with Testcontainers.
- Perf testing already exists in `perf/jmeter/README.md`; the JMeter plan targets `http://localhost:8080`.

## Change guidance for agents
- Preserve the existing Vert.x async style (`Future`, `compose`, `onSuccess`, `onFailure`) instead of introducing blocking request-path code.
- When changing file lifecycle logic, update both the DAO/state handling and the metadata integration test.
- When adding config, support both properties-file keys and env-var override form because startup code auto-maps env vars.
- Do not silently “upgrade” auth assumptions: `AuthMain` and gateway `AuthServiceClient.validateToken()` are intentionally stubbed today and affect local development behavior.
- When adding new endpoints, update the OpenAPI spec.
- When changing the flow, update the integration test to cover the updated flow. 
- When changing the flow, update the jmeter plan in 'perf/jmeter/dropvox-files-flow.jmx' to cover the updated flow.
- When changing the flow, update the diagrams in 'diagrams' folder to cover the updated flow.