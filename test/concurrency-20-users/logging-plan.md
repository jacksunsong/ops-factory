# Logging Plan: 20-User Frontend Concurrency

## 1. Goal

This logging plan exists to ensure that any failure during the 20-user frontend concurrency run can be localized to:

- the affected user
- the affected session
- the exact frontend action
- the exact gateway stage
- the exact runtime instance

The logging design must support diagnosis first. Noise reduction is secondary.

## 2. Diagnostic Questions

The logs must let us answer the following for any failed sample:

1. Which frontend user action triggered the failure?
2. Which gateway request corresponds to that action?
3. Was the failure in:
   - frontend navigation/rendering
   - gateway request admission
   - `getOrSpawn`
   - `agent/start`
   - `agent/resume`
   - SSE first-byte
   - SSE first-content
   - SSE completion
   - `agent/stop`
4. Which `(agentId, userId)` instance handled the request?
5. Was the instance reused, cold-started, recycled, or respawned?
6. Was the failure isolated or shared across multiple users?

## 3. Correlation Model

Every relevant signal should be correlatable by a stable set of IDs.

### Required Correlation Keys

- `testRunId`
- `scenarioGroup`
- `scenarioStep`
- `frontendActionId`
- `requestId`
- `userId`
- `agentId`
- `sessionId`
- `instanceKey`
- `port`
- `pid`

### Recommended Construction

- `testRunId`
  - one ID per full test execution
  - example: `cc20-20260410-01`
- `scenarioGroup`
  - one of `A`, `B`, `C`, `D`, `E`
- `scenarioStep`
  - example: `history-open`, `chat-send-1`, `stop-after-3s`
- `frontendActionId`
  - unique per browser action block
- `requestId`
  - existing `X-Request-Id`

## 4. Frontend Logging Requirements

Frontend logs must be captured from browser console and, where possible, emitted through the existing frontend logging utilities.

### 4.1 Existing Mechanisms To Reuse

- `trackedFetch` already emits request lifecycle logs and injects `X-Request-Id`.
  - [requestClient.ts](/Users/buyangnie/Documents/GitHub/ops-factory/web-app/src/app/platform/logging/requestClient.ts)
- `GoosedContext` and `UserContext` already use `trackedFetch` for some gateway calls.
  - [GoosedContext.tsx](/Users/buyangnie/Documents/GitHub/ops-factory/web-app/src/app/platform/providers/GoosedContext.tsx)
  - [UserContext.tsx](/Users/buyangnie/Documents/GitHub/ops-factory/web-app/src/app/platform/providers/UserContext.tsx)

### 4.2 Frontend Signals To Add

For this test we should log:

- page open start / page ready
- route transition start / complete
- history list request start / complete
- history search input applied
- session row click
- new chat click
- composer ready
- send button click
- stream state entered
- first assistant fragment rendered
- finish rendered
- stop click
- stop acknowledged locally
- browser request failure
- browser console error
- unhandled promise rejection

### 4.3 Frontend Log Payload Fields

Each frontend log entry should include:

- `testRunId`
- `scenarioGroup`
- `scenarioStep`
- `frontendActionId`
- `userId`
- `agentId`
- `sessionId` if known
- `requestId` if tied to a request
- `route`
- `durationMs` where applicable
- `result`
- `errorMessage` when failed

### 4.4 Header / Metadata Injection

For browser-to-gateway requests, add these request headers where possible:

- `X-Test-Run-Id`
- `X-Test-Scenario-Group`
- `X-Test-Scenario-Step`
- `X-Frontend-Action-Id`

If the chat SDK path cannot inject custom headers directly, use both of these:

1. preserve `X-Request-Id` wherever supported
2. emit parallel frontend console logs keyed by `frontendActionId`, `userId`, `sessionId`

This is acceptable for phase 1 as long as gateway-side logs still contain `userId`, `agentId`, `sessionId`, `port`, and `pid`.

## 5. Gateway Logging Requirements

Gateway already has useful diagnostics, but they are not yet complete for a 20-user frontend run.

### 5.1 Existing Mechanisms To Reuse

- access log with `requestId`
  - [RequestContextFilter.java](/Users/buyangnie/Documents/GitHub/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/filter/RequestContextFilter.java)
- session lifecycle logs
  - [SessionController.java](/Users/buyangnie/Documents/GitHub/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/controller/SessionController.java)
- reply stage logs
  - [ReplyController.java](/Users/buyangnie/Documents/GitHub/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/controller/ReplyController.java)
- SSE relay diagnostics
  - [SseRelayService.java](/Users/buyangnie/Documents/GitHub/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/proxy/SseRelayService.java)
- instance spawn/reuse/recycle diagnostics
  - [InstanceManager.java](/Users/buyangnie/Documents/GitHub/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/process/InstanceManager.java)

### 5.2 Access Log Additions

Augment the access log to include, when present:

- `testRunId`
- `scenarioGroup`
- `scenarioStep`
- `frontendActionId`
- `userId`
- `requestId`
- request path
- status
- duration

Purpose:

- fast filtering by test run
- direct correlation from browser event to gateway request

### 5.3 Session Lifecycle Logs

For `start`, `resume`, `list sessions`, `get session`, and `stop`, log:

- begin
- instance resolved
- upstream request started
- upstream request completed
- total duration
- failure reason on error

Required fields:

- `requestId`
- `testRunId`
- `frontendActionId`
- `userId`
- `agentId`
- `sessionId`
- `port`
- `pid`

### 5.4 Reply Chain Logs

For `/reply`, the following stages must be explicit:

- request received
- hooks begin / end
- file snapshot begin / end
- `getOrSpawn` begin / end
- session resume begin / end
- SSE relay subscribed
- first chunk
- first non-Ping content
- finish
- error
- client cancel
- output-files post-processing

The key missing signal today is a distinct `firstNonPingContent` stage. Existing logs distinguish chunks and pings in SSE diagnostics, but the test report should be able to read this directly without re-parsing all chunk logs.

### 5.5 Instance Management Logs

For instance management, we need structured visibility into:

- reuse hit
- cold start
- spawn lock wait
- runtime prepare duration
- process start duration
- wait-ready duration
- health-check failure
- force recycle
- watchdog respawn
- stop cause

Required fields:

- `instanceKey`
- `agentId`
- `userId`
- `port`
- `pid`
- `requestId` when request-triggered
- `cause`
- `durationMs`

### 5.6 Failure Reason Taxonomy

When logging failures, normalize the reason into one of:

- `frontend_cancel`
- `request_timeout`
- `first_byte_timeout`
- `content_idle_timeout`
- `upstream_unavailable`
- `instance_limit`
- `spawn_failed`
- `resume_failed`
- `provider_not_set`
- `premature_close`
- `unexpected_exception`

This taxonomy is needed so the final report can group failures without hand-reading raw logs.

## 6. Browser Artifact Capture

For each failed user action, preserve:

- screenshot
- page URL
- browser console logs
- failed network requests
- route and visible UI state

For severe failures, also preserve Playwright trace if enabled.

## 7. Result Storage Layout

Store all artifacts under this directory:

- `results/<run-id>/browser/`
- `results/<run-id>/gateway/`
- `results/<run-id>/summary/`

Recommended contents:

- `browser/console-<user>.log`
- `browser/network-<user>.json`
- `browser/failure-<user>-<step>.png`
- `gateway/gateway-tail.log`
- `gateway/failure-window-<requestId>.log`
- `summary/metrics.json`
- `summary/prediction-vs-actual.md`

## 8. Minimum Logging Bar Before Running 20 Users

Do not run the full 20-user scenario until all of the following are true:

1. access logs carry `requestId` and `userId`
2. `start`, `resume`, and `reply` logs include stage timings
3. SSE logs distinguish no-byte timeout vs slow-content timeout
4. frontend logs preserve browser-side failures
5. at least one baseline run can be fully correlated end-to-end

## 9. Validation Checklist

Before full execution, verify:

- one frontend action can be traced from browser log to gateway access log
- one `/reply` can be traced from request receipt to SSE completion
- one failed request produces enough information to classify the reason
- one instance recycle can be tied back to the initiating user/session

## 10. Implementation Priority

Priority order:

1. preserve and propagate correlation IDs
2. add missing gateway stage logs
3. add browser-side capture
4. normalize failure reasons
5. only then run the comparative baseline and full-load scenarios
