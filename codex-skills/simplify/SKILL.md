---
name: simplify
description: Simplify recently changed or user-specified code without changing behavior. Use when the user asks to simplify code, do a light refactor, reduce nesting, remove redundancy, clean up recent edits, or make code easier to read while keeping functionality the same; 也适用于“简化代码 / 轻量重构 / 清理最近改动 / 在不改行为前提下优化可读性”这类请求。
---

# Simplify

Simplify code conservatively. The goal is clearer code with the same behavior, not fewer lines.

## Scope

Default to the narrowest safe scope:

1. If the user names files, functions, or a diff, use that exact scope.
2. Otherwise, prefer the files changed in the current branch or working tree.
3. If there is no obvious changed scope, ask the user what to simplify instead of refactoring broadly.

Do not expand into unrelated files just because a broader cleanup is possible.

## Instruction Sources

Before editing, gather the instruction files that apply to the target files:

- `AGENTS.md` in the repository root or any ancestor directory
- `CLAUDE.md` in the repository root or any ancestor directory

For a given file, only apply instruction files from that file's directory or its parents. If no instruction file applies, follow the surrounding local style.

## What Good Simplification Looks Like

Prefer explicit, local, behavior-preserving improvements such as:

- flattening unnecessary nesting and early-returning
- replacing dense conditionals with clearer `if` / `switch` logic
- splitting complex expressions into well-named intermediate values
- removing dead branches or redundant wrappers when behavior is unchanged
- consolidating duplicated logic when the shared code is genuinely simpler
- renaming local variables or private helpers when that materially improves clarity
- removing comments that only restate obvious code

Prefer readable code over clever code. Avoid nested ternaries unless the surrounding codebase clearly favors them.

## What To Avoid

Do not use this skill for broad redesign. Avoid:

- changing public APIs, request or response shapes, event names, routes, schemas, or persistence behavior
- moving files, renaming exported symbols broadly, or reorganizing modules unless the user asked for it
- framework migrations, dependency changes, or config churn
- speculative performance rewrites, caching, or concurrency changes
- refactors that require trusting hidden behavior you cannot validate
- touching generated files, snapshots, fixtures, or vendored code unless requested
- "cleanup" that mainly makes code shorter but harder to read

If a simplification idea might change semantics, do not do it.

## Workflow

1. Determine the exact edit scope.
2. Read the applicable instruction files and the nearby code.
3. Identify only high-confidence simplifications.
4. Make the smallest set of edits that materially improves clarity.
5. Validate with the narrowest relevant check available.
6. Report what changed and what was validated.

## Validation

After simplifying code, run the narrowest useful verification you can:

- targeted tests for the touched area
- typecheck or compile for the affected module
- linters only if they are already part of the normal local workflow and directly relevant

Do not run broad suites unless the change truly requires them.

If you cannot validate a change confidently, either:

- reduce the change to a safer version, or
- stop and tell the user why the refactor is too risky without more context

## Output

Keep the summary brief and concrete:

- what scope was simplified
- the main clarity improvements
- what validation ran
- any risk or limitation if validation was incomplete

## Review Heuristics

Use these as defaults unless the repository's own instructions say otherwise:

- preserve behavior first
- prefer local clarity over abstraction
- prefer explicit names over compressed expressions
- prefer editing touched code over opportunistic cleanup elsewhere
- stop before the refactor becomes structural
