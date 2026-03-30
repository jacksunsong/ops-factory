---
name: github-pr-review
description: Review a GitHub pull request for high-signal bugs and scoped instruction-file (`AGENTS.md` / `CLAUDE.md`) compliance. Use when the user asks to review a PR, inspect a diff, summarize review findings, or post review comments; 也适用于“评审 PR / review diff / 发布 review 评论”等请求。
---

# GitHub PR Review

Review GitHub pull requests with a high-signal bar. Focus on bugs, definite compile or type failures, and scoped instruction-file compliance. Ignore style, speculative concerns, and general quality advice.

## Inputs

Expect:
- A pull request number or URL
- An optional request to post review comments
- An optional local checkout of the target repository

## Core Rules

- Use `gh` CLI and any available GitHub review-comment tools. Do not browse the web for PR contents.
- Only call tools that directly advance the review. Do not make exploratory tool calls.
- Create a todo list with `update_plan` before starting.
- Treat `AGENTS.md` and `CLAUDE.md` as instruction files. For any changed file, only apply instruction files in that file's directory or ancestor directories. Root instruction files apply repo-wide.
- Keep the signal bar high. A finding must be one of:
  - a definite syntax, parse, type, import, or unresolved reference failure
  - logic that is clearly wrong from the diff and the minimum required context
  - an unambiguous instruction-file violation that you can cite exactly
- Do not flag:
  - style, naming, readability, or maintainability concerns
  - speculative bugs that depend on hidden state or specific runtime inputs
  - linter-only complaints
  - missing tests unless an applicable instruction file explicitly requires them
  - broad security or quality concerns without a concrete diff-local failure
  - pre-existing issues
  - issues explicitly silenced in code or by repository rules
- Still review AI-authored PRs. Only skip review if the PR is closed, draft, already reviewed by the same assistant identity, or the change is trivial and obviously correct.

## Recommended Commands

Use the minimum set needed. Common starting points:

```bash
gh pr view <PR> --json number,state,isDraft,title,body,author,files,headRefOid,baseRefName,headRefName
gh pr view <PR> --comments
gh pr diff <PR>
```

Use `rg --files` and `rg` locally to find scoped `AGENTS.md` / `CLAUDE.md` files and to inspect narrow file context when validating a finding.

## Workflow

1. Create a todo list with `update_plan`.
2. Resolve PR metadata, changed files, full head SHA, and existing comments.
3. Early-exit if any of these are true:
   - PR is closed
   - PR is draft
   - the change does not need review because it is trivial and obviously correct
   - the same assistant identity has already left review comments and the user did not ask for a rerun
4. Collect all relevant instruction files:
   - root `AGENTS.md`
   - root `CLAUDE.md`
   - any `AGENTS.md` / `CLAUDE.md` in directories containing changed files
   - any ancestor `AGENTS.md` / `CLAUDE.md` for changed files
5. Produce a short summary of the change.
6. Run independent review passes:
   - two instruction-compliance passes
   - two bug and correctness passes
7. Validate every candidate issue with a fresh pass before surfacing it.
8. Deduplicate confirmed issues.
9. Output findings locally.
10. If the user asked to comment on GitHub, post comments only for confirmed unique issues.

## Parallel Pattern

If the user explicitly asked for delegation or parallel work and subagents are available, use this split:

- Agent 1: screen for early-exit conditions
- Agent 2: list relevant instruction files only
- Agent 3: summarize the PR
- Agents 4-5: instruction-file compliance review
- Agents 6-7: bug and correctness review
- One validator agent per candidate issue

Give each review or validation agent:
- PR title
- PR description
- the relevant diff or finding description
- the exact scoped instruction files when checking compliance

If subagents are not allowed, perform the same passes locally in sequence.

## Validation Standard

A candidate issue survives only if a validation pass can confirm it with high confidence. Validation should answer:
- Is the code in the diff enough to prove the issue?
- If it is an instruction-file issue, does the cited `AGENTS.md` / `CLAUDE.md` actually apply to this file?
- Would the issue still hold regardless of normal inputs?

Drop anything uncertain.

## Output Format

Present findings first, ordered by severity. For each finding include:
- file path and line reference if available
- a brief description of the problem
- why it was flagged: `bug`, `compile failure`, `instruction-file compliance`, and so on

If no issues remain, say exactly:

```text
No issues found. Checked for bugs and instruction-file compliance.
```

## Commenting Rules

If the user did not ask to post comments, stop after the local review summary.

If the user asked to post comments and there are no issues, post:

```markdown
## Code review

No issues found. Checked for bugs and instruction-file compliance.
```

If the user asked to post comments and there are issues:

1. Prepare the full comment list privately first.
2. Post exactly one inline comment per unique confirmed issue.
3. Keep comments short and concrete.
4. Include a committable suggestion block only when applying that suggestion fully fixes the issue by itself.
5. For larger or multi-step fixes, describe the issue and suggested direction without a suggestion block.
6. When citing code or instruction files, use a full GitHub blob URL with the PR head SHA and a small context window:
   - `https://github.com/<owner>/<repo>/blob/<full_sha>/<path>#L<start>-L<end>`
   - include at least one line of context before and after when possible
7. Quote the exact instruction-file rule when raising a compliance issue.
8. Prefer available inline-comment tools. If only `gh` is available and it cannot create the required inline comment type in the current environment, stop after printing the confirmed findings.
9. Do not post duplicate comments.

## Review Stance

This skill is intentionally conservative. False positives are worse than missing a marginal issue. If you cannot validate a concern confidently, omit it.
