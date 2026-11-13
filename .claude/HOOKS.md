# Repository Claude Code checks

The Bash `PostToolUse` and `PostToolUseFailure` hooks run local checks after a command that includes `git push`. Combined commit and push commands are supported. The checks use an isolated checkout of committed `HEAD` and the tasks in `.claude/checks.json`: build with CI coverage, Java formatting, and agent hook checks. Local failures are advisory. Missing tools skip the checks that need them. The agent must inspect `mise doctor project`, run `mise install` when it can repair missing tools, retry the failed command, and report remaining failures or skips.

The `Stop` hook reports local checks, the required Build PR jobs, and review for the current commit. Local failures and review do not block the stop. Failed or running PR CI asks the agent to fix or monitor it, with a loop guard for repeated unchanged results. Without an available PR for `HEAD`, it reports the incomplete state.

Run `python3 .claude/checks.py verify-head` to repeat the local checks, or `python3 .claude/checks.py finalize` to report local and PR status. Run `mise run test:agent-hooks` to check the shared runner and adapters.
