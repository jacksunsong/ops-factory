# API Boundaries

## Gateway As Single Entry
All browser and SDK traffic enters through the gateway. Preserve the gateway as the only stable integration point for:
- authentication via `x-secret-key`
- user scoping via `x-user-id`
- agent routing, sessions, file APIs, and monitoring

## Compatibility Rules
- Do not change route prefixes, response shapes, or auth header names casually.
- Changes to SSE or streaming event payloads require explicit review and coordinated frontend/test updates.
- If an endpoint contract changes, update frontend consumers, SDK types, and integration tests in the same change.

## File and Config APIs
- File access must continue to flow through gateway services/controllers rather than direct filesystem exposure from the UI.
- Agent config CRUD should remain centralized in gateway services and controller routes.

## Review Triggers
Request explicit cross-team review when a change affects:
- API path structure
- auth semantics
- session lifecycle
- SSE message format
- compatibility with existing test fixtures or SDK consumers
