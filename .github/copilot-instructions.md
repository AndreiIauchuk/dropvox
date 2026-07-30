# Dropvox Copilot Repository Instructions

## Project context
- This repository is a Java 21 multi-module Maven project with three backend services: `dropvox-gateway`, `dropvox-metadata`, and `dropvox-auth`.
- Runtime ownership:
  - Gateway is the public API edge and should remain thin.
  - Metadata owns PostgreSQL and MinIO behavior, file lifecycle transitions, and presigned URL logic.
  - Auth is currently a lightweight stub used by gateway validation flow.

## Implementation expectations
- Keep existing Vert.x async patterns (`Future`, `compose`, `onSuccess`, `onFailure`) and avoid introducing blocking calls in request paths.
- Preserve trace behavior: propagate `X-Trace-Id`, keep MDC usage consistent, and return trace IDs in responses.
- Preserve metadata identity handling: `X-User-Id` is required and must be UUID-validated for metadata endpoints.
- Respect current lifecycle semantics:
  - API acceptance state: `PROCESSING` for completion request responses.
  - Persisted DB states: `PENDING`, `UPLOADED`, `FAILED`.
- Keep S3 key convention user/file scoped: `users/{userUuid}/files/{fileUuid}/{sanitizedFilename}`.

## API and contract rules
- Keep gateway request validation OpenAPI-driven.
- If an endpoint is added or renamed, update both:
  - OpenAPI spec (`dropvox-gateway/src/main/resources/swagger/openapi.json`)
  - Operation mounting in gateway server wiring.

## Data and migration rules
- Flyway migrations run before metadata server starts; schema changes should be done via new migration files.
- PostgreSQL uses `search_path` and existing SQL style intentionally references unqualified tables like `files`.
- Do not change JSON field naming conventions exposed by DAO/API (for example `fileId`, `ownerId`, `s3Key`, `uploadedAt`, `lastModifiedAt`) unless explicitly required.

## Configuration rules
- New config keys must work from both properties files and env vars (env vars are normalized to lowercase and `_` -> `.`).
