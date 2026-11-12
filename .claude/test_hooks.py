"""Offline tests for the Claude Code adapter and transcript override boundary."""

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("hooks.py")
SPEC = importlib.util.spec_from_file_location("claude_hooks", SCRIPT)
hooks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hooks)


class HookTests(unittest.TestCase):
    def invoke(self, command, payload):
        with mock.patch.object(sys, "argv", ["hooks.py", command]), \
             mock.patch.object(sys, "stdin", io.StringIO(json.dumps(payload))), \
             contextlib.redirect_stdout(io.StringIO()) as output, \
             contextlib.redirect_stderr(io.StringIO()) as errors:
            code = hooks.main()
        return code, output.getvalue(), errors.getvalue()

    def test_successful_push_reports_to_claude(self):
        with mock.patch.object(hooks.checks, "pre_tool_use", return_value=(True, "maven_verify: passed")):
            code, output, errors = self.invoke("pre-tool-use", {"tool_input": {"command": "git push"}})
        self.assertEqual(0, code)
        self.assertEqual("", errors)
        self.assertEqual("maven_verify: passed", json.loads(output)["hookSpecificOutput"]["additionalContext"])

    def test_failed_push_blocks(self):
        with mock.patch.object(hooks.checks, "pre_tool_use", return_value=(False, "maven_verify failed")):
            code, output, errors = self.invoke("pre-tool-use", {"tool_input": {"command": "git push"}})
        self.assertEqual(2, code)
        self.assertEqual("", output)
        self.assertIn("maven_verify failed", errors)

    def test_stop_blocks_on_failed_ci(self):
        state = {"complete": False, "pr_error": None, "sha": "sha",
                 "items": [("job", "Build", "failed (failure)")], "message": "Build: failed"}
        with mock.patch.object(hooks.checks, "collect_status", return_value=state), \
             mock.patch.object(hooks.checks, "note_block", return_value=False):
            code, output, _ = self.invoke("stop", {"session_id": "session"})
        self.assertEqual(0, code)
        self.assertEqual("block", json.loads(output)["decision"])
        self.assertIn("Build: failed", json.loads(output)["reason"])

    def test_override_requires_user_words_in_claude_transcript(self):
        with tempfile.TemporaryDirectory() as directory:
            projects = Path(directory) / "projects" / "repo"
            projects.mkdir(parents=True)
            transcript = projects / "session.jsonl"
            transcript.write_text("\n".join(json.dumps(item) for item in [
                {"type": "user", "message": {"content": "Please ignore CI for this PR."}},
                {"type": "assistant", "message": {"content": [{"type": "text", "text":
                    'CI-OVERRIDE: "ignore CI for this PR"'}]}}]))
            state = {"complete": False, "pr_error": None, "message": "Build: failed"}
            with mock.patch.dict(os.environ, {"CLAUDE_CONFIG_DIR": directory}), \
                 mock.patch.object(hooks.checks, "collect_status", return_value=state):
                code, output, _ = self.invoke("stop", {"transcript_path": str(transcript)})
            self.assertEqual(0, code)
            self.assertIn("CI override accepted", output)
            self.assertEqual(([], ""), hooks.transcript_messages(str(Path(directory) / "outside.jsonl")))


if __name__ == "__main__":
    unittest.main()
