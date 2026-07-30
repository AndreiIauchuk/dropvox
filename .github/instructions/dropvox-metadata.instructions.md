---
applyTo: "dropvox-metadata/src/main/**"
---

# Metadata-specific instructions

- Keep Vert.x request handling asynchronous (`Future`, `compose`, `onSuccess`, `onFailure`) and avoid blocking code.
- Metadata service owns lifecycle state, DB writes, and MinIO interactions. Keep this ownership inside metadata.
- Preserve lifecycle/status semantics:
  - Persisted states are `PENDING`, `UPLOADED`, `FAILED`.
  - Completion request API may return `202 PROCESSING` while async verification runs.
- Preserve user-scoped object key pattern: `users/{userUuid}/files/{fileUuid}/{sanitizedFilename}`.
- Continue validating `X-User-Id` as UUID before processing metadata requests.
- Use Flyway migrations for schema changes; do not mutate existing migration history.
- If upload/download flow or lifecycle semantics change, also update:
  - metadata integration tests (`dropvox-metadata/src/test/java/com/iovchukandrew/dropvox/metadata/IntegrationTest.java`)
  - `perf/jmeter/dropvox-files-flow.jmx`
  - diagrams in `diagrams/`
  - `AGENTS.md`
  - `README.md`

