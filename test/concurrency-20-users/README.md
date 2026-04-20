# 20-User Frontend Concurrency Test

## Purpose

This directory stores all working artifacts for the 20-user frontend concurrency test against `gateway` and `web-app`.

The test goal is to simulate realistic user behavior from the frontend, measure gateway behavior under concurrent pressure, and preserve enough logs and reports to diagnose failures.

## Scope

In scope:

- frontend-driven concurrent user flows
- gateway logging and diagnosis support
- performance baseline and comparison
- test scripts, datasets, screenshots, and reports

Out of scope for the first pass:

- large-scale distributed load testing
- provider-side benchmarking in isolation
- synthetic backend-only benchmark as the primary result

## Validation Targets

The test must validate:

1. `gateway` stays available during a 20-user concurrent run.
2. A single user or session failure does not expand into full gateway unavailability.
3. Core frontend journeys remain functional under load:
   - open home
   - start new chat
   - resume from history
   - send streamed reply
   - stop generation
4. Gateway logs are sufficient to localize failures to a concrete stage.
5. Actual results can be compared against a pre-declared performance expectation.

## Planned Artifacts

- `test-plan.md`: scenario matrix, metrics, pass/fail criteria
- `logging-plan.md`: required gateway and frontend observability additions
- `playwright-scenarios.md`: concrete user journey design
- `scripts/`: Playwright orchestration and helper tools
- `results/`: raw run outputs, screenshots, traces, summaries
- `analysis/`: prediction vs actual comparison and failure diagnosis

## Execution Principle

The authoritative run must start from the frontend and simulate real users through browser contexts. Backend-only helper scripts may be used for comparison or diagnosis, but not as the primary result.
