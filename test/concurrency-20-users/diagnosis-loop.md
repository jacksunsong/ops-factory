# Diagnosis Loop: Frontend Concurrency Test

## 1. Purpose

This document defines how to investigate failures during the 20-user frontend concurrency effort.

The objective is to avoid two bad outcomes:

- collecting too little information and being unable to localize failures
- adding uncontrolled amounts of logging and turning the test into a logging exercise

This test should assume iteration. It is expected that:

1. an initial run will expose incomplete observability
2. logs will be reviewed
3. targeted additions will be made
4. the run will be repeated

## 2. Core Principle

Do not add logs because "more logs might help".

Only add a log when:

- a concrete diagnostic question cannot be answered with current data
- the new log has a clear consumer
- the new log is scoped to a specific stage or correlation need

## 3. Logging Levels Strategy

### L1: Minimum Run Logging

Used for:

- baseline run
- 5-user run
- 20-user official run

Level:

- application logs at `INFO`
- Spring / Reactor / Netty remain `WARN`

Purpose:

- stable observability without high noise or high runtime risk

### L2: Targeted Stage Logging

Used for:

- reruns after one class of failure has been identified

Level:

- still prefer `INFO`
- add only narrow stage logs

Examples:

- reply first non-Ping content stage
- spawn lock wait duration
- history list item count and duration

Purpose:

- fill one diagnostic gap without changing global logging posture

### L3: Temporary Localized Debug

Used for:

- reproduction of a specific failure after L1/L2 are insufficient

Level:

- `DEBUG` only for a narrow set of classes

Typical candidates:

- `ReplyController`
- `SseRelayService`
- `InstanceManager`

Rules:

- never enable broad project-wide debug for official load runs
- never leave L3 logging enabled for the final comparison run

## 4. Investigation Sequence

Every failed sample should be investigated in the same order.

### Step 1: Identify the Failing User Action

From browser-side artifacts, determine:

- `testRunId`
- `userId`
- `scenarioGroup`
- `scenarioStep`
- `frontendActionId`
- visible failure symptom
- page URL at failure time

If this cannot be determined, fix browser artifact capture before continuing.

### Step 2: Inspect Browser Signals

Check:

- console errors
- failed requests
- whether the request was emitted
- whether the UI entered streaming state
- whether the UI returned from streaming state

Primary question:

- is this failure caused before the request reaches gateway?

### Step 3: Locate the Gateway Request

Using `requestId`, `userId`, path, and time window, locate:

- access log entry
- HTTP status
- duration
- whether the request completed at all

Primary question:

- did gateway accept and complete the request?

### Step 4: Inspect Gateway Stage Logs

Then inspect the relevant stage family:

- `SESSION-START`
- `SESSION-LIST`
- `SESSION-GET`
- `REPLY-PERF`
- `SSE-DIAG`
- `INSTANCE`

Primary question:

- which internal stage was the last confirmed successful point?

### Step 5: Determine Failure Domain

Classify the failure as one of:

- frontend-only
- navigation/rendering
- gateway admission / routing
- instance management
- session start / resume
- SSE first-byte
- SSE mid-stream
- stop / recovery
- upstream model slowness

### Step 6: Decide Whether Logging Is Missing

Only after the failure domain is identified, ask:

- can we already explain the failure well enough?

If yes:

- do not add more logs

If no:

- define the single missing question
- add the smallest log required to answer that question

## 5. Missing-Question Template

Before adding any new log, write the missing question explicitly:

- "We cannot tell whether the request reached `agent/resume`."
- "We cannot distinguish first byte from first non-Ping content."
- "We cannot tell whether history list delay is browser-side or gateway-side."

If a proposed log does not answer a written question, do not add it.

## 6. Iteration Loop

The expected loop is:

1. run scenario
2. collect failures
3. classify each failure using current logs
4. identify missing questions
5. add minimal logs for unresolved questions
6. rerun the smallest reproducing scenario first
7. only then rerun a larger scenario

This means:

- do not jump directly from a failed 20-user run to another 20-user run with more logs
- first reproduce on 1-user or 5-user if possible

## 7. Recommended Run Progression

### Phase A: Correlation Dry Run

- `1` user
- validate end-to-end correlation
- validate artifact persistence

Exit condition:

- one request can be traced from browser action to gateway stages

### Phase B: Small Concurrency Run

- `5` users
- mixed scenario, shorter duration

Exit condition:

- identify first contention symptoms
- confirm current logs are adequate or list missing questions

### Phase C: Targeted Rerun

- `1` or `5` users
- only for reproducing a discovered problem

Exit condition:

- confirm or reject the suspected bottleneck

### Phase D: Full Concurrency Run

- `20` users
- official comparison run

Exit condition:

- prediction vs actual comparison is possible

## 8. Stop Rules For Logging Expansion

Do not keep expanding logs indefinitely.

Stop adding logs when:

- the failure domain is already clear
- the root bottleneck is already consistent across samples
- added logs no longer change the diagnosis
- the next action should be scenario refinement or code fix, not more observability

## 9. When To Escalate To Targeted Debug

Use localized `DEBUG` only when all of the following are true:

1. the same failure has reproduced at least twice
2. L1/L2 logs narrowed it to one subsystem
3. the unresolved question is internal to that subsystem
4. reproducing with a smaller scenario is possible

Do not use targeted debug for:

- the first 20-user official run
- broad exploratory fishing
- framework packages unrelated to the suspected bottleneck

## 10. Output Required After Each Iteration

After every meaningful run or rerun, record:

- run scope: `1-user`, `5-user`, or `20-user`
- observed failure classes
- what was diagnosable with current logs
- what was not diagnosable
- whether logging changes are required
- exact next action

This should be stored under the run's result directory so the diagnostic reasoning is preserved.

## 11. Operational Rule

The concurrency effort is not "implement script then run once".

It is:

- instrument enough to correlate
- run a small scenario
- inspect logs
- add only what is missing
- rerun at the smallest useful scale
- then promote to the full 20-user test
