#!/usr/bin/env python3
"""Codex lifecycle adapter for the repository's committed-tree checks."""

import importlib.util
import json
from pathlib import Path
import sys


def load_checks():
    script = Path(__file__).resolve().parents[1] / ".claude" / "checks.py"
    spec = importlib.util.spec_from_file_location("repo_checks", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def codex_stop_message(message):
    lines = []
    for line in message.splitlines():
        if line.startswith("Only if the user explicitly said"):
            continue
        if line.startswith("If nothing has failed and you have set up monitoring"):
            lines.append("Use gh run watch or gh run view to check CI. If jobs are still running, "
                         "end your reply with 'CI-WAITING: <what you are monitoring>'.")
            continue
        lines.append(line)
    return "\n".join(lines)


def report_error(error, command):
    message = f"Repository check unavailable: {error}"
    if command == "post-tool-use":
        message += ". Report this check failure in your final reply."
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PostToolUse", "additionalContext": message}}))
    else:
        print(json.dumps({"systemMessage": message}))
    return 0

def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("post-tool-use", "stop"):
        print("Usage: python3 .codex/hooks.py {post-tool-use|stop}", file=sys.stderr)
        return 2
    try:
        checks = load_checks()
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("hook input must be a JSON object")
        if sys.argv[1] == "post-tool-use":
            if payload.get("tool_name") != "Bash":
                return 0
            _, message = checks.post_tool_use(payload)
            if message:
                print(json.dumps({"hookSpecificOutput": {
                    "hookEventName": "PostToolUse", "additionalContext": message}}))
            return 0
        blocked, message = checks.stop(payload)
        message = codex_stop_message(message)
        print(json.dumps({"decision": "block", "reason": message} if blocked
                         else {"systemMessage": message}))
        return 0
    except Exception as error:
        return report_error(error, sys.argv[1])


if __name__ == "__main__":
    sys.exit(main())
