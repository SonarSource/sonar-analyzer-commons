# Repository Claude Code checks

`.claude/settings.json` installs a Bash PreToolUse hook and a Stop hook. Both use the same committed-HEAD checks, saved results, and PR status logic as the [Codex hook](../.codex/HOOKS.md). No check runs at commit time.

Run `git push` as its own Bash call; `git push 2>&1 | tail -30` is supported. Shell wrapped pushes are blocked with instructions to run a standalone push. Before push, the hook checks the committed diff and runs `mvn clean verify -Pcoverage -DskipTests=false -B` in an isolated checkout of `HEAD`. A failure blocks the push. On success, Claude receives each check's command, result, and log path through PreToolUse context. Results live in the checkout-specific state directory described in the [Codex hook](../.codex/HOOKS.md).

After push, `python3 .codex/checks.py finalize` reports all local checks, required Build PR jobs, and review for exactly `HEAD`. The Stop hook reports their passed, failed, running, and pending states through a visible system message. With an active PR, it asks Claude to fix failures or arrange monitoring for running jobs. Approval alone does not block the stop. If monitoring is arranged, end the reply with `CI-WAITING: <what is monitored>`. A user can explicitly authorize ignoring CI by starting their latest message with `CI-OVERRIDE:`. Then end the reply with `CI-OVERRIDE: "<their entire latest message>"`; the hook accepts only an exact match to that message in the Claude session transcript. After three identical blocks, the loop guard reports instead of blocking. PR fetch errors also only report.

Run `python3 .codex/checks.py --help` for manual commands. `verify-head` reruns the local checks, and `finalize` lists every required result.
