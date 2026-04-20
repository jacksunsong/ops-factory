# Playwright Scenario Design: 20-User Frontend Concurrency

## 1. Purpose

This document turns the concurrency test plan into executable browser behavior.

The target is a single orchestration script that:

- launches isolated browser contexts for 20 users
- drives realistic frontend actions
- synchronizes key contention points
- captures browser-side artifacts for every failure

## 2. Execution Model

### 2.1 Browser Isolation

Each simulated user gets:

- one Playwright browser context
- one page
- isolated storage state
- its own `userId`
- its own scenario group and action timeline

This matches the gateway's `(agentId, userId)` isolation model closely enough for frontend validation.

### 2.2 User Naming

Use stable test user IDs, grouped by scenario:

- `cc20-a1` to `cc20-a6`
- `cc20-b1` to `cc20-b4`
- `cc20-c1` to `cc20-c4`
- `cc20-d1` to `cc20-d4`
- `cc20-e1` to `cc20-e2`

### 2.3 Test Run ID

Each run receives one `testRunId`, for example:

- `cc20-20260410-01`

This ID must be attached to:

- browser logs
- screenshots
- result files
- gateway request headers when possible

## 3. Existing Frontend Paths and Selectors

The scenario should reuse selectors already validated by existing E2E coverage.

### Login

- route: `/login`
- username input: `input[placeholder="Your name"]`
- enter button: `button:has-text("Enter")`

Reference:

- [home.spec.ts](/Users/buyangnie/Documents/GitHub/ops-factory/test/e2e/home.spec.ts)

### Sidebar Navigation

- new chat: `.new-chat-nav`
- history nav: `.sidebar-nav a[href="/history"]`
- home nav: `.sidebar-nav a[href="/"]`

Reference:

- [sidebar-settings.spec.ts](/Users/buyangnie/Documents/GitHub/ops-factory/test/e2e/sidebar-settings.spec.ts)

### Home

- template card: `.prompt-template-card`
- category tab: `.home-template-tab`

Reference:

- [home.spec.ts](/Users/buyangnie/Documents/GitHub/ops-factory/test/e2e/home.spec.ts)

### Chat

- input: `.chat-input`
- send / stop button: `.chat-send-btn-new`
- message area: `.chat-messages-area`

Reference:

- [chat-advanced.spec.ts](/Users/buyangnie/Documents/GitHub/ops-factory/test/e2e/chat-advanced.spec.ts)

### History

- search input: `.search-input`
- session item: `.session-item`
- type filter: `.seg-filter-btn`

Reference:

- [history.spec.ts](/Users/buyangnie/Documents/GitHub/ops-factory/test/e2e/history.spec.ts)

## 4. Shared Helper Actions

The orchestration script should implement these shared helpers.

### `loginAs(page, userId)`

Steps:

1. open `/login`
2. fill username
3. submit
4. wait for `/`
5. wait until shell is stable

### `waitForHomeReady(page)`

Readiness condition:

- `.prompt-template-card` visible

### `openNewChat(page)`

Steps:

1. ensure app shell ready
2. click `.new-chat-nav`
3. wait for `/chat`
4. wait for `.chat-input`

### `sendChatMessage(page, text)`

Steps:

1. fill `.chat-input`
2. submit via `Enter`
3. record send start timestamp

### `waitForStreamingStart(page)`

Readiness condition:

- `.chat-send-btn-new` has class `is-stop`

### `waitForChatFinish(page, timeoutMs)`

Completion condition:

- `.chat-send-btn-new` no longer has class `is-stop`

### `openHistory(page)`

Steps:

1. click `.sidebar-nav a[href="/history"]`
2. wait for `/history`
3. wait for `.search-input`

### `openMostRecentHistorySession(page)`

Steps:

1. ensure `.session-item` exists
2. click first item
3. wait for `/chat`
4. wait for messages area stable

## 5. Synchronization Strategy

This test needs barriers. Without barriers, users will drift and contention will be weak.

The orchestration script should implement a barrier helper:

- `await barrier('wave-1-ready')`

All users in the participating set must reach the barrier before the next burst starts.

### Barrier Rules

- barrier timeout must be explicit
- barrier failure must be logged with participating users
- if a user fails before a barrier, the test should record the failure and decide whether to continue the wave

## 6. Scenario Groups

## Group A: Cold Start Chat

Users:

- `cc20-a1` to `cc20-a6`

Initial state:

- no session created during this run

Steps:

1. login
2. wait for home ready
3. barrier `wave-a-home-ready`
4. click new chat
5. wait for chat composer ready
6. barrier `wave-a-send-1`
7. send short prompt
8. wait for finish
9. for selected users, send a second short prompt immediately

Prompt style:

- deterministic short answer
- no ambiguous wording

Examples:

- `Reply with exactly "A1-OK".`
- `What is 7 times 7? Reply with only the number.`

Main metrics:

- login to home ready
- new chat click to composer ready
- send to stream start
- send to finish

## Group B: Hot Session Multi-Turn

Users:

- `cc20-b1` to `cc20-b4`

Initial state:

- must already have at least one resumable session

Precondition setup:

- before the measured run, seed one session per user

Measured steps:

1. login
2. open chat route with seeded session or resume from recent chat entry
3. barrier `wave-b-send-1`
4. send normal prompt
5. wait for finish
6. barrier `wave-b-send-2`
7. send second prompt quickly
8. wait for finish

At least one second-round prompt per group should encourage a tool-like action.

Examples:

- `Reply with exactly "B1-hot".`
- `Run the shell command: echo "B-tool".`

Main metrics:

- resume/open to ready
- first send latency
- second send latency
- success continuity within same session

## Group C: History Pressure

Users:

- `cc20-c1` to `cc20-c4`

Initial state:

- must already have multiple sessions to search/open

Precondition setup:

- seed at least 3 sessions per user

Measured steps:

1. login
2. barrier `wave-c-open-history`
3. open history
4. wait for list
5. apply search term
6. open first matching session
7. return to history
8. clear or change search
9. open another session

Main metrics:

- history nav latency
- history list visible latency
- search apply latency
- session click to chat visible

## Group D: History Resume to Chat Continue

Users:

- `cc20-d1` to `cc20-d4`

Initial state:

- seeded session with recognizable previous content

Measured steps:

1. login
2. barrier `wave-d-open-history`
3. open history
4. click recent session
5. wait for chat with restored messages
6. barrier `wave-d-send`
7. send follow-up prompt
8. wait for finish

Main metrics:

- history click to restored chat ready
- restored chat to successful reply

## Group E: Stop and Recovery

Users:

- `cc20-e1` to `cc20-e2`

Initial state:

- open chat with fresh or seeded session

Measured steps:

1. login
2. open chat
3. barrier `wave-e-send-long`
4. send long-running prompt
5. wait for stream start
6. wait fixed delay, for example `3000ms`
7. click stop
8. wait until stop state clears
9. barrier `wave-e-recover-send`
10. send short verification prompt
11. wait for finish

Examples:

- long prompt: `Write a very detailed essay about operating systems from 1960 to 2020.`
- recovery prompt: `Reply with exactly "E-recovered".`

Main metrics:

- send to stream start
- stop click to idle
- recovery send to finish

## 7. Wave Timeline

The orchestration script should execute these waves in order.

### Wave 0: Login and Initial Readiness

All users:

1. login
2. reach initial route
3. wait for app shell ready

Artifacts:

- login timings
- initial console logs

### Wave 1: Navigation Burst

- group A waits on home
- groups C and D open history
- group B reaches existing chat entry
- group E reaches chat entry

Purpose:

- route and page-load contention

### Wave 2: Session/Open Burst

- group A clicks new chat simultaneously
- group D clicks history sessions simultaneously
- group C finishes first history list/search stage

Purpose:

- `start`, `resume`, session-read contention

### Wave 3: Reply Burst

- groups A, B, D send at nearly same time
- group E sends long prompts

Purpose:

- main reply pressure

### Wave 4: Interrupt and Follow-Up Burst

- group E stops generation
- part of group A sends second prompt
- group B sends second hot-session prompt
- group C opens second history item

Purpose:

- overlap cancellation, second-turn reply, and history access

## 8. Prompt Design Rules

Prompts should follow these rules:

- short prompts for latency-sensitive steps
- deterministic expected text when possible
- a small number of tool-oriented prompts for realism
- avoid prompts that depend on unstable provider creativity for pass/fail

### Prompt Categories

- short deterministic
- normal conversational
- tool-encouraging
- long-streaming

### Validation Rules

A prompt is considered successful if:

- UI finishes streaming
- no frontend error is shown
- no gateway error event is surfaced
- response contains required marker when marker-based prompt is used

## 9. Browser Artifact Capture Rules

### Always Capture

- console logs per user
- failed network requests per user
- per-step timings

### Capture On Failure

- screenshot
- current URL
- HTML snapshot or key DOM text snippet
- Playwright trace when configured

### Suggested File Names

- `browser-console-<user>.log`
- `network-failures-<user>.json`
- `failure-<user>-<step>.png`
- `timings-<user>.json`

## 10. Seed Data Strategy

Some groups need existing sessions. These must be created before the measured run.

### Seed Required For

- group B
- group C
- group D

### Seed Requirements

- B: at least 1 valid existing session per user
- C: at least 3 sessions per user
- D: at least 1 session per user with unique marker text

Seeding should happen in a separate, clearly logged setup phase so it does not pollute measured timings.

## 11. Pass / Fail at Scenario Level

Each user scenario should emit:

- `success`
- `failed_step`
- `error_type`
- `duration_ms`
- `request_ids`
- `session_id`

Example failure classes:

- login_failed
- home_not_ready
- history_not_ready
- new_chat_failed
- stream_never_started
- stream_never_finished
- stop_failed
- recovery_failed

## 12. Script Layout Recommendation

Recommended structure under this directory:

- `scripts/run_frontend_concurrency.py`
- `scripts/seed_sessions.py`
- `scripts/extract_gateway_windows.py`
- `results/<run-id>/...`

The main runner should:

1. create `testRunId`
2. optionally seed sessions
3. launch browser
4. create 20 contexts
5. assign users to scenario groups
6. execute wave barriers
7. collect timings and artifacts
8. write machine-readable summary

## 13. Pre-Run Checklist

Before running the full 20-user scenario:

1. confirm app stack is running
2. confirm one user can login and send a message
3. confirm one user can open history
4. confirm one long reply can be stopped
5. confirm screenshots and console logs are being saved
6. confirm at least one gateway request can be correlated with frontend timing

## 14. Next Implementation Step

After this document, the next concrete step should be:

- implement the browser orchestration skeleton and result directory layout

The first executable milestone should be a `1-user` dry run using the same helpers and artifact capture paths intended for the full 20-user run.
