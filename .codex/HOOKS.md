# Repository Codex checks

Review and trust `.codex/hooks.json` with `/hooks` in Codex. The Bash `PostToolUse` hook runs the shared local checks after a push. Failures are reported to the agent and do not prevent the push. Missing tools skip dependent checks and produce setup guidance.

The `Stop` hook reports local and PR status and asks the agent to fix or monitor incomplete PR CI. It uses the same policy and manual commands as the [Claude hooks](../.claude/HOOKS.md). Run `mise run test:agent-hooks` to check the shared runner and adapters.
