---
name: "Database Performance Diagnosis"
description: "Investigate database latency, lock waits, slow queries, saturation, and connection pool pressure."
---

# Database Performance Diagnosis

## When to Use

Use this skill when the user reports slow SQL, elevated database latency, lock waits, connection exhaustion, or timeout errors.

## Required Inputs

- Database engine and version
- Slow query samples or top SQL
- Time range and affected application
- CPU, memory, I/O, active sessions, and connection pool metrics

## Workflow

1. Determine whether the symptom is query, lock, resource, or connection related.
2. Compare current metrics with baseline.
3. Identify top SQL, wait events, blocked sessions, or pool exhaustion.
4. Recommend low-risk mitigations and longer-term fixes.
5. Define post-change validation metrics.

## Output Format

### Symptom
### Evidence
### Bottleneck Type
### Immediate Mitigation
### Long-term Fix
### Validation

## Rules

- Do not recommend destructive SQL without explicit approval.
- Separate application pool saturation from database server saturation.
- Prefer explain plans and wait events over guesses.
