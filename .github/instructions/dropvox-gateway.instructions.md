---
applyTo: "dropvox-gateway/src/main/**"
---

# Gateway-specific instructions

- Keep handlers thin: authenticate with `AuthServiceClient` and proxy business operations via `MetadataServiceClient`.
- Treat gateway as OpenAPI-first:
  - Keep `operationId` values aligned with handler mounting.
  - Update `dropvox-gateway/src/main/resources/swagger/openapi.json` when endpoint contracts change.
- Preserve trace forwarding to downstream services (`X-Trace-Id`) and existing `MetadataServiceClient.withTrace(...)` behavior.
- Keep gateway error behavior consistent:
  - OpenAPI validation failures should map to `400`.
  - Unexpected uncaught failures generally surface as `500`.
- Do not introduce assumptions that auth is fully implemented; `AuthServiceClient.validateToken()` behavior is intentionally stubbed for local development.

