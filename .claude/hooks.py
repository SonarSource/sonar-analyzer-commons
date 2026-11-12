#!/usr/bin/env python3
"""Claude Code adapter for the shared committed-HEAD checks in .codex/checks.py."""

import importlib.util
import json
import os
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".codex" / "checks.py"
SPEC = importlib.util.spec_from_file_location("repo_checks", SCRIPT)
checks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checks)


def message_texts(entry):
    content = entry.get("message", {}).get("content", "")
    if isinstance(content, str):
        return [content]
    return [part.get("text", "") for part in content
            if isinstance(part, dict) and part.get("type") == "text"]


def transcript_messages(path_text):
    """Read only Claude project transcripts, never arbitrary hook-supplied paths."""
    if not path_text:
        return [], ""
    projects = (Path(os.environ.get("CLAUDE_CONFIG_DIR", Path.home() / ".claude")) / "projects").resolve()
    try:
        path = Path(path_text).resolve(strict=True)
        if path.suffix != ".jsonl" or not path.is_relative_to(projects):
            return [], ""
        lines = path.read_text().splitlines()
    except OSError:
        return [], ""
    users, last_reply = [], ""
    for line in lines:
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("isMeta") or entry.get("isSidechain"):
            continue
        texts = [part for part in message_texts(entry) if part.strip()]
        if entry.get("type") == "user":
            user = re.sub(r"<system-reminder>.*?</system-reminder>", "", "\n".join(texts), flags=re.DOTALL)
            if user.strip():
                users.append(user)
        elif entry.get("type") == "assistant" and texts:
            last_reply = "\n".join(texts)
    return users, last_reply


def authorized_override(reply, users):
    latest = " ".join(users[-1].split()).casefold() if users else ""
    if not latest.startswith("ci-override:"):
        return None
    for match in re.finditer(r'CI-OVERRIDE:\s*["\u201c](.+?)["\u201d]', reply):
        quote = " ".join(match.group(1).split()).casefold()
        if len(quote) >= 8 and quote == latest:
            return match.group(1)
    return None


def pre_tool_use(payload):
    passed, message = checks.pre_tool_use(payload)
    if passed:
        if message:
            print(json.dumps({"hookSpecificOutput": {
                "hookEventName": "PreToolUse", "additionalContext": message}}))
        return 0
    print(message, file=sys.stderr)
    return 2


def stop(payload):
    try:
        state = checks.collect_status()
    except (KeyError, OSError, RuntimeError, ValueError) as error:
        print(json.dumps({"systemMessage": f"Incomplete: Check unavailable: {error}."}))
        return 0
    users, reply = transcript_messages(payload.get("transcript_path"))
    quote = authorized_override(reply, users)
    if quote and not state["complete"]:
        print(json.dumps({"systemMessage": f'CI override accepted (user said: "{quote}").\nIncomplete: {state["message"]}'}))
        return 0
    block, message = checks.stop_decision({**payload, "last_assistant_message": reply}, state)
    print(json.dumps({"decision": "block", "reason": message} if block else {"systemMessage": message}))
    return 0


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("pre-tool-use", "stop"):
        print("Usage: python3 .claude/hooks.py {pre-tool-use|stop}", file=sys.stderr)
        return 2
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("hook input must be a JSON object")
        return pre_tool_use(payload) if sys.argv[1] == "pre-tool-use" else stop(payload)
    except (KeyError, OSError, RuntimeError, ValueError) as error:
        if sys.argv[1] == "pre-tool-use":
            print(f"Repository check unavailable: {error}", file=sys.stderr)
            return 2
        print(json.dumps({"systemMessage": f"Incomplete: Check unavailable: {error}."}))
        return 0


if __name__ == "__main__":
    sys.exit(main())
