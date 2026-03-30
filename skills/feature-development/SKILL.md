---
name: feature-development
description: Guide end-to-end feature development with deliberate discovery, codebase exploration, clarifying questions, architecture comparison, implementation, and review. Use when Codex is asked to build or extend a non-trivial feature, such as "implement a new feature", "add support for", "build a new page/endpoint/workflow", or "extend an existing capability", especially when requirements are incomplete or architecture trade-offs matter.
---

# Feature Development

## Overview

Follow a staged workflow for non-trivial feature work. Optimize for codebase understanding, explicit requirement clarification, simple architecture, and disciplined review before declaring the work complete.

## Core Principles

- Understand the existing codebase before proposing designs or writing code.
- Maintain a running plan throughout the task. Use the plan tool if available; otherwise keep a concise checklist in the conversation.
- Re-read the most important files discovered during exploration before designing or implementing.
- Ask clarifying questions after exploration and before architecture or implementation. Do not silently assume behavior that materially affects scope, UX, contracts, or data flow.
- Prefer the simplest architecture that fits the requirement and matches existing patterns.
- Start implementation only after the user has approved the scope and approach.
- Treat review as a distinct phase. Look for bugs, regressions, abstraction damage, and missing tests.

## Phase 1: Discovery

Goal: understand what needs to be built.

Actions:

1. Create a plan covering discovery, exploration, questions, architecture, implementation, review, and summary.
2. If the request is vague, ask for the problem being solved, the expected behavior, constraints, and non-goals.
3. Restate the feature in concrete terms and confirm that understanding before moving on.

## Phase 2: Codebase Exploration

Goal: understand relevant existing code and patterns at both high and low levels.

Actions:

1. Inspect the code paths that appear relevant: routes, handlers, services, models, UI components, tests, config, and docs.
2. If subagents are available and the user has explicitly allowed delegation, launch 2-3 focused explorer agents in parallel. Give each agent a different angle and require a list of 5-10 key files to read.
3. Good exploration angles include:
   - similar features or adjacent workflows
   - architecture and flow of control for the feature area
   - UI patterns, extension points, data contracts, and testing approaches
4. After exploration or agent results, read the key files directly instead of relying only on summaries.
5. Summarize the important patterns, constraints, extension points, risks, and file locations that will shape the solution.

Example subagent prompts:

- Find features similar to this request and trace their implementation end to end. Return the most important files to read.
- Map the architecture, abstractions, and control flow for the relevant feature area. Return the most important files to read.
- Identify UI patterns, API contracts, test coverage, and extension points relevant to this feature. Return the most important files to read.

## Phase 3: Clarifying Questions

Goal: resolve ambiguity before architecture design.

This phase is mandatory for underspecified work.

Actions:

1. Review the original request and exploration findings together.
2. Identify missing decisions around:
   - scope boundaries and non-goals
   - user-visible behavior and UX details
   - edge cases and error handling
   - data model changes, API contracts, and backward compatibility
   - permissions, rollout constraints, performance, telemetry, and testing expectations
3. Present all clarifying questions in a single organized list.
4. Wait for answers before designing the architecture.

If the user says "whatever you think is best", recommend explicit choices and get confirmation instead of treating that as blanket approval.

## Phase 4: Architecture Design

Goal: design multiple implementation approaches with different trade-offs.

Actions:

1. Produce 2-3 viable approaches when the change is not trivial:
   - minimal change and maximum reuse
   - cleaner abstraction and long-term maintainability
   - pragmatic balance between speed and quality
2. If subagents are available and the user has explicitly allowed delegation, use architecture-focused agents to stress-test these options.
3. Compare trade-offs concretely: implementation scope, complexity, maintainability, migration risk, test impact, and delivery speed.
4. Recommend one approach and explain why it best fits this task.
5. Ask the user to choose or approve the recommended approach before implementation.

## Phase 5: Implementation

Goal: build the feature.

Do not start this phase without explicit user approval.

Actions:

1. Re-read the key files and inspect the current workspace state before editing.
2. Implement the approved approach with small, coherent changes that match local conventions.
3. Add or update tests alongside the code changes.
4. Update config examples, docs, or schemas when the feature introduces new behavior or new configuration.
5. Keep the running plan current while implementing.

Implementation guidelines:

- Preserve existing architectural boundaries unless the approved design intentionally changes them.
- Prefer clear names, direct control flow, and minimal indirection.
- Avoid speculative abstractions.
- Keep comments rare and high-signal.

## Phase 6: Quality Review

Goal: ensure the result is simple, correct, and well integrated.

Actions:

1. Run targeted verification such as unit tests, integration tests, type checks, lint, or build commands appropriate to the repo.
2. If subagents are available and the user has explicitly allowed delegation, launch parallel reviewers with different focuses:
   - simplicity, duplication, and abstraction quality
   - bugs, regressions, and edge-case correctness
   - repo conventions, boundaries, and test coverage
3. Consolidate findings and fix the highest-severity issues.
4. If meaningful issues remain, present them clearly and ask whether to fix now or defer.

## Phase 7: Summary

Goal: close the task with clear documentation of what happened.

Actions:

1. Mark the running plan complete.
2. Summarize:
   - what was built
   - key requirement or architecture decisions
   - files changed
   - verification performed
   - suggested next steps or follow-up risks

## Example Triggers

- Implement a new approval workflow in this service.
- Add support for bulk actions in the existing UI.
- Extend the current API to handle a new resource type.
- Build this feature carefully and propose the architecture before coding.
