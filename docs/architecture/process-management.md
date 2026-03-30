# Process Management

## Runtime Model
The gateway manages `goosed` as per-user, per-agent runtime processes. Treat `(agentId, userId)` as the isolation boundary: each runtime gets its own port, working directory, uploads area, and process lifecycle.

## Required Properties
- Spawn lazily on demand; do not pre-create user runtimes unless the prewarm path or configured resident-instance list explicitly owns that behavior.
- Reap idle instances automatically rather than keeping all runtimes resident.
- Keep agent config shared, but keep runtime data and uploads isolated per user.
- Bind runtime services to local addresses only; external access must continue to flow through the gateway.

## Resident Instances
- Resident instances are configured in `gateway/config.yaml` as explicit `(userId, agentId)` targets.
- Resident instances are started during gateway boot and are exempt from idle reaping only.
- Health checks, timeout recycling, and crash recovery still apply to resident instances.

## Runtime Directory Contract
The gateway prepares runtime directories under `gateway/users/<userId>/agents/<agentId>/`. Shared agent config is linked in, while mutable runtime state remains user-local. New features should respect that split instead of writing directly into shared agent config trees.

## Health And Recovery
- Health checks, idle cleanup, and restart logic belong in gateway process-management classes, not in frontend code or ad hoc scripts.
- Changes to watchdog, restart backoff, or instance limits require careful regression coverage because they affect all agents.
- Preserve process-output draining and similar defensive runtime behavior; historical incidents show that missing drain logic can freeze otherwise healthy runtimes under tool-heavy workloads.

## Review Triggers
Request explicit review when changing:
- runtime directory layout
- spawn/reap/restart semantics
- port allocation behavior
- health-check readiness rules
- environment variable injection for `goosed`
