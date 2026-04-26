## Task Context
- Context limit or auto-compaction was reached in an opsGoose Universal Agent session.
- Active context: 128K tokens. The summary must be much smaller and continuation-focused.

## Compression Budget
- Target summary size: 20K-25K tokens.
- Hard upper bound: 32K tokens.
- Prefer underestimating. The next turn adds prompts, tools, user message, and new results.
- Do not copy long outputs/excerpts. Keep exact paths, commands, identifiers, conclusions, and only essential snippets.

## Conversation History
{{ messages }}

Wrap reasoning in `<analysis>` tags:
- Review the conversation chronologically.
- Track current goal, constraints, unresolved requests, and latest direction.
- Preserve facts, paths, lines, commands, config values, key tool results, errors, decisions, and active work.
- Remove verbose raw output unless essential.

### Include the Following Sections
1. **User Intent** - current and prior goals that still matter.
2. **Technical Concepts** - relevant tools, config, context, paths, and ops behavior.
3. **Files + Evidence** - viewed/changed files, paths, line ranges, and short necessary evidence.
4. **Errors + Fixes** - failures, root causes, and applied or proposed fixes.
5. **Decisions** - accepted choices, rejected alternatives, and user preferences.
6. **Pending Tasks** - unresolved work, checks, or needed confirmation.
7. **Current Work** - active state, filenames, and next edit/test target.
8. **Next Step** - the direct next step for the latest instruction.

Keep this summary dense and operational. It is for the next agent turn, not for the user.
