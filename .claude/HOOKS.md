# Repository Claude Code checks

`.claude/settings.json` installs a Bash PreToolUse hook and a Stop hook. Both use the same committed-HEAD checks, saved results, and PR status logic as the [Codex hook](../.codex/HOOKS.md). No check runs at commit time.

Run `git push` as its own Bash call; `git push 2>&1 | tail -30` is supported. Before push, the hook checks the committed diff and runs `mvn clean verify -Pcoverage -DskipTests=false -B` in an isolated checkout of `HEAD`. A failure blocks the push. On success, Claude receives each check's command, result, and log path through PreToolUse context. Results live in `${CODEX_CHECK_STATE:-/tmp/codex-repo-checks}/sonar-analyzer-commons/`.

After push, `python3 .codex/checks.py finalize` reports all local checks, required Build PR jobs, and review for exactly `HEAD`. The Stop hook reports their passed, failed, running, and pending states. With an active PR, it asks Claude to fix failures or arrange monitoring for running jobs. Approval alone does not block the stop. If monitoring is arranged, end the reply with `CI-WAITING: <what is monitored>`. If the user explicitly authorizes ignoring CI, end with `CI-OVERRIDE: "<their exact words>"`; the hook accepts it only when those words appear in a user message in the Claude session transcript. After three identical blocks, the loop guard reports instead of blocking. PR fetch errors also only report.

Run `python3 .codex/checks.py --help` for manual commands. `verify-head` reruns the local checks, and `finalize` lists every required result.
