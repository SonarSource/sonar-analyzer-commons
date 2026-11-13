#!/usr/bin/env python3
"""Adapt Tirion lifecycle events to the shared repository checks."""

import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]


def load_checks():
    """Load the shared runner and use separate state for this checkout."""
    spec = importlib.util.spec_from_file_location("repo_checks", ROOT / ".claude" / "checks.py")
    checks = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checks)
    checks.STATE = (ROOT / checks.git("rev-parse", "--git-path", "tirion-checks")).resolve()
    temporary = checks.STATE / "tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(temporary)
    return checks


def read_transcript(payload, checks):
    """Read user prompts and the final reply from Tirion's session log."""
    users, reply = [], payload.get("assistant_message") or ""
    if not payload.get("transcript_path"):
        return users, reply
    try:
        with Path(payload["transcript_path"]).open() as transcript:
            for line in transcript:
                try:
                    entry = json.loads(line)
                except ValueError:
                    continue
                text = entry.get("payload", {}).get("text", "")
                if not isinstance(text, str):
                    continue
                if entry.get("kind") == "user_message":
                    text = checks.strip_reminders(text)
                    if checks.OVERRIDE_MARKER not in text and checks.WAITING_MARKER not in text:
                        users.append(text)
                elif entry.get("kind") == "assistant_completed" and not payload.get("assistant_message"):
                    reply = text
    except OSError:
        pass
    return users, reply


def handle(payload, command, checks):
    """Run advisory push checks or apply the shared end-of-turn CI policy."""
    if command == "post-tool-use":
        if payload.get("tool_name") != "shell":
            return False, ""
        tool_input = payload.get("tool_input", {})
        if not checks.is_push(tool_input.get("command", "")):
            return False, ""
        directory = Path(payload.get("cwd") or ROOT)
        workdir = directory / (tool_input.get("workdir") or ".")
        repository = checks.run(["git", "rev-parse", "--show-toplevel"], cwd=workdir)
        if repository.returncode or Path(repository.stdout.strip()).resolve() != ROOT:
            return False, "Local checks unavailable: the push ran outside this checkout. " + checks.PUSH_REPORT_GUIDANCE
        _, message = checks.post_tool_use(payload)
        return False, message
    return checks.stop(payload, transcript=read_transcript(payload, checks))


def main(argv=None):
    """Read one Tirion event and return a structured hook response."""
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1 or argv[0] not in ("post-tool-use", "stop"):
        print("Usage: python3 .kolk/hooks.py {post-tool-use|stop}", file=sys.stderr)
        return 2
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("Hook input must be a JSON object")
        if shutil.which("git") is None:
            blocked, message = False, "Skipped repository checks: Git is unavailable."
        else:
            checks = load_checks()
            blocked, message = handle(payload, argv[0], checks)
    except Exception as error:
        blocked, message = False, f"Repository check unavailable: {error}. Report this failure in your final reply."
    output = {"decision": "deny" if blocked else "continue"}
    if message:
        output.update(additional_context=message, display_message=message.splitlines()[0])
        if blocked:
            output["reason"] = message
    print(json.dumps(output))
    return 0


if __name__ == "__main__":
    sys.exit(main())
