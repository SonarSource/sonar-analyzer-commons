#!/usr/bin/env python3
"""Adapt OpenCode requests to the shared committed-code checks."""

import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("repo_checks", ROOT / ".claude" / "checks.py")
checks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checks)


def configure_state():
    """Keep OpenCode results and temporary checkouts separate for each checkout."""
    default = checks.git("rev-parse", "--git-path", "opencode-checks")
    checks.STATE = (ROOT / os.environ.get("OPENCODE_CHECK_STATE", default)).resolve()
    temporary = checks.STATE / "tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(temporary)


def repository_matches(directory):
    """Return whether the command directory belongs to this checkout."""
    result = checks.run(["git", "rev-parse", "--show-toplevel"], cwd=directory)
    return result.returncode == 0 and Path(result.stdout.strip()).resolve() == ROOT


def post_tool_use(payload):
    """Check the committed HEAD after a shell push using its working directory."""
    command = payload.get("command", "")
    if not checks.is_push(command):
        return False, ""
    directory = Path(payload["directory"])
    workdir = directory / (payload.get("workdir") or ".")
    if not repository_matches(workdir):
        return False, "Local checks unavailable: the push ran outside this checkout."
    _, message = checks.post_tool_use({"tool_input": {"command": command}})
    return False, message


def read_messages(messages):
    """Read user input and the last assistant text from OpenCode context."""
    users, reply = [], ""
    for message in messages:
        if message.get("type") == "user":
            text = checks.strip_reminders(message.get("text", ""))
            if checks.OVERRIDE_MARKER not in text and checks.WAITING_MARKER not in text:
                users.append(text)
        elif message.get("type") == "assistant":
            reply = "\n".join(part["text"] for part in message.get("content", []) if part.get("type") == "text")
    return users, reply


def stop(payload):
    """Apply the shared CI policy to the current OpenCode session."""
    if not repository_matches(Path(payload["directory"])):
        return False, ""
    return checks.stop({"session_id": payload["session_id"]}, transcript=read_messages(payload["messages"]))


def main(argv=None):
    """Read one hook request and return its decision as JSON."""
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1 or argv[0] not in ("post-tool-use", "stop"):
        print("Usage: python3 .opencode/hooks.py {post-tool-use|stop}", file=sys.stderr)
        return 2
    command = argv[0]
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("Hook input must be a JSON object")
        if shutil.which("git") is None:
            blocked, message = False, "Skipped repository checks: Git is unavailable."
        else:
            configure_state()
            blocked, message = post_tool_use(payload) if command == "post-tool-use" else stop(payload)
    except (KeyError, OSError, RuntimeError, TypeError, ValueError) as error:
        blocked, message = False, f"Incomplete: Repository check unavailable: {error}"
        if command == "post-tool-use":
            message += ". Report this check failure in your final reply."
    print(json.dumps({"block": blocked, "message": message}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
