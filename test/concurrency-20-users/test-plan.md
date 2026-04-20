# Test Plan: 20-User Frontend Concurrency

## 1. Test Objective

This test verifies whether `web-app + gateway + goosed` can sustain a realistic 20-user concurrent frontend workload without:

- full gateway unavailability
- cascading failures from one user/session to others
- excessive latency on core user journeys
- insufficient diagnostic information after failure

The primary outcome is not just pass/fail. The primary outcome is a reproducible, diagnosable baseline for future regression comparison.

## 2. Primary Questions

This test must answer:

1. Can 20 concurrent frontend users complete mixed chat/history flows with acceptable success rate?
2. Which stage becomes the bottleneck first:
   - frontend page readiness
   - session start
   - session resume
   - first SSE byte
   - first non-Ping content
   - full reply completion
3. If one user/session hangs or fails, is the failure isolated to that `(agentId, userId)` instance?
4. Are current gateway logs sufficient to identify the failing stage and the affected instance?

## 3. Test Scope

In scope:

- browser-driven user behavior through the real frontend
- `home`, `chat`, `history`, and `stop generation` journeys
- gateway request/instance/SSE behavior
- comparative runs at 1-user, 5-user, and 20-user concurrency

Out of scope for phase 1:

- distributed multi-node load testing
- provider-only benchmarking
- synthetic API-only load test as the official result

## 4. Environment Assumptions

- frontend served at `http://127.0.0.1:5173`
- gateway served through the normal local stack
- Playwright drives the real frontend
- each simulated user uses an isolated browser context
- gateway logs are written to `gateway/logs/gateway.log`

## 5. Success Criteria

The run is considered operationally acceptable if all of the following hold:

1. Gateway process remains alive and responsive for the full test window.
2. Overall scenario success rate is at least `90%`.
3. No more than `2` users experience complete end-to-end failure.
4. No failure pattern indicates full-gateway hang.
5. Every failed request can be correlated to:
   - `userId`
   - `requestId`
   - `agentId`
   - `sessionId` when present
   - instance `port` and `pid` when applicable

## 6. Hard Failure Conditions

Any of the following is a test failure regardless of average latency:

- gateway process crash or restart during run
- more than `20%` scenario failure rate
- more than `5` users blocked on the same gateway-wide symptom
- repeated SSE first-byte timeout across unrelated users
- missing logs that prevent locating the failure stage

## 7. User Scenario Matrix

The 20 users are split into 5 groups to create mixed pressure instead of one-dimensional load.

| Group | Users | Main Journey | Purpose |
|------|------:|------|------|
| A | 6 | Home -> New Chat -> first message | cold start pressure |
| B | 4 | Existing session -> continue chat twice | hot session reply pressure |
| C | 4 | History -> search -> open session -> back -> open another | history/session read pressure |
| D | 4 | History -> resume old session -> continue chatting | resume-after-history pressure |
| E | 2 | Long reply -> stop generation -> immediate next message | stop/cancel/recovery pressure |

Total concurrent users: `20`

## 8. Traffic Wave Design

To create real contention, user actions are synchronized into waves.

### Wave 0: Warm Login / Initial Load

- all 20 contexts open the app
- wait until initial shell is ready
- record page-ready timing

### Wave 1: Navigation Burst

- group A enters home/new chat flow
- groups C and D open history
- group B restores existing chat entry points
- group E opens chat pages prepared for long replies

### Wave 2: Session Pressure Burst

- group A simultaneously triggers new session creation
- group D simultaneously opens historical sessions and restores chat state
- group B simultaneously prepares first hot-session continuation

### Wave 3: Reply Burst

- groups A, B, and D send messages nearly at the same time
- group C continues history interactions
- group E sends long-running prompts

### Wave 4: Interrupt and Follow-up Burst

- group E clicks stop during active generation
- group B sends second-round messages
- part of group A immediately sends a second message after first reply
- group C opens another history item

## 9. Detailed User Intent Design

### Group A: Cold Start Chat Users

Each user:

1. opens home
2. clicks `New Chat`
3. waits for composer ready state
4. sends a short deterministic prompt
5. waits for complete streamed reply

Expected stress point:

- `start -> resume -> first reply`

### Group B: Hot Session Conversation Users

Each user:

1. opens an existing session
2. sends a normal prompt
3. sends a second prompt quickly after completion
4. at least one prompt should encourage a tool or file-system action

Expected stress point:

- hot reply latency
- same user/session continuity

### Group C: History Load Users

Each user:

1. opens history
2. uses search
3. opens a session
4. returns to history
5. opens a different session

Expected stress point:

- `/sessions`
- `/sessions/{id}`
- frontend history rendering and navigation

### Group D: Resume from History Users

Each user:

1. opens history
2. selects an existing session
3. waits for chat view restoration
4. sends a follow-up message

Expected stress point:

- restore flow consistency
- resume correctness under concurrency

### Group E: Stop / Recovery Users

Each user:

1. opens a prepared chat
2. sends a prompt likely to stream for longer
3. clicks `Stop` after a short delay
4. immediately sends a follow-up prompt

Expected stress point:

- `/agent/stop`
- SSE cancellation
- recovery after interrupted generation

## 10. Metric Collection

### Frontend Metrics

For each user action, capture:

- page ready time
- route change time
- click-to-request start
- click-to-streaming state
- click-to-first rendered message fragment
- click-to-final completion
- stop click to UI idle time
- failed request count
- browser console error count

### Gateway Metrics

For each relevant request, capture:

- request start/end
- HTTP status
- `requestId`
- `userId`
- `agentId`
- `sessionId`
- instance `port`
- instance `pid`
- `getOrSpawn` duration
- `resume` duration
- SSE first-byte latency
- first non-Ping content latency
- total reply duration
- recycle / respawn / timeout reason

### Run-Level Metrics

- overall success rate
- per-group success rate
- p50/p95/p99 for critical stages
- number of cold starts
- number of instance recycles
- number of user-visible errors

## 11. Performance Prediction

This prediction is the pre-test expectation and must not be edited after measurement starts.

Environment assumption: single local node, approximately `8 CPU / 16 GB RAM`.

| Metric | Prediction |
|------|------|
| Home/history list p50 | `< 500ms` |
| Home/history list p95 | `< 1500ms` |
| New chat cold start to composer ready p50 | `2s - 5s` |
| New chat cold start to composer ready p95 | `6s - 10s` |
| Hot session reply first SSE byte p50 | `3s - 8s` |
| Hot session reply first SSE byte p95 | `10s - 20s` |
| Short reply total time p50 | `8s - 20s` |
| Short reply total time p95 | `20s - 45s` |
| Tool-heavy or interrupted flow p95 | `45s - 90s` |
| Overall scenario success rate | `>= 90%` |

## 12. Comparative Run Sequence

The test should be executed in three stages.

### Stage A: Baseline

- `1` user
- representative mixed journey
- purpose: establish uncontended timing baseline

### Stage B: Mid Load

- `5` users
- same mixed scenario pattern at reduced scale
- purpose: identify first visible degradation

### Stage C: Full Load

- `20` users
- full scenario matrix and wave orchestration
- purpose: validate concurrency target

Results must be compared across all three stages.

## 13. Diagnostics Requirements

The run is not complete unless failures can be analyzed. Therefore:

- browser-side logs must be preserved
- gateway log snippets around failed requests must be preserved
- screenshots must be captured on user-visible failure
- request timeline and gateway timeline must be correlated

If this cannot be done, the run is incomplete even if success rate is high.

## 14. Deliverables

The test effort should produce:

1. finalized logging plan
2. finalized Playwright user-orchestration plan
3. executable frontend concurrency script
4. baseline, mid-load, and full-load result sets
5. prediction vs actual comparison report
6. failure analysis report when failures occur

## 15. Next Implementation Steps

1. define the gateway logging additions required for diagnosis
2. define the exact Playwright user action script per group
3. implement result capture layout under `results/`
4. run baseline `1-user`
5. run `5-user`
6. run `20-user`
7. compare against this plan's prediction table
