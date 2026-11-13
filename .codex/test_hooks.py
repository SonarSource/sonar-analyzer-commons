"""Codex adapter contract tests."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("hooks.py")
SPEC = importlib.util.spec_from_file_location("codex_hooks", SCRIPT)
hooks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hooks)


class HookTests(unittest.TestCase):
    def invoke(self, command, payload):
        with mock.patch.object(sys, "argv", ["hooks.py", command]), \
             mock.patch.object(sys, "stdin", io.StringIO(json.dumps(payload))), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            code = hooks.main()
        return code, json.loads(output.getvalue())

    def test_success_reports_to_codex(self):
        payload = {"tool_name": "Bash", "tool_input": {"command": "git push"}}
        with mock.patch.object(hooks, "load_checks") as load:
            load.return_value.post_tool_use.return_value = True, "maven_verify passed"
            code, output = self.invoke("post-tool-use", payload)
        self.assertEqual(0, code)
        self.assertEqual("maven_verify passed", output["hookSpecificOutput"]["additionalContext"])

    def test_failure_is_reported_without_denying_push(self):
        payload = {"tool_name": "Bash", "tool_input": {"command": "git push"}}
        with mock.patch.object(hooks, "load_checks") as load:
            load.return_value.post_tool_use.return_value = False, "maven_verify failed"
            code, output = self.invoke("post-tool-use", payload)
        self.assertEqual(0, code)
        self.assertNotIn("permissionDecision", output["hookSpecificOutput"])
        self.assertIn("maven_verify failed", output["hookSpecificOutput"]["additionalContext"])

    def test_stop_reports_json(self):
        with mock.patch.object(hooks, "load_checks") as load:
            load.return_value.stop.return_value = False, "Incomplete: build running"
            code, output = self.invoke("stop", {})
        self.assertEqual(0, code)
        self.assertIn("build running", output["systemMessage"])

    def test_missing_checks_are_reported_without_denying_push(self):
        payload = {"tool_name": "Bash", "tool_input": {"command": "git push"}}
        with tempfile.TemporaryDirectory() as temporary:
            with mock.patch.object(hooks, "__file__", str(Path(temporary) / ".codex" / "hooks.py")):
                code, output = self.invoke("post-tool-use", payload)
        self.assertEqual(0, code)
        self.assertNotIn("permissionDecision", output["hookSpecificOutput"])
        self.assertIn("Repository check unavailable", output["hookSpecificOutput"]["additionalContext"])

    def test_malformed_checks_config_is_reported_without_denying_push(self):
        payload = {"tool_name": "Bash", "tool_input": {"command": "git push"}}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            checks_dir = root / ".claude"
            checks_dir.mkdir()
            (checks_dir / "checks.py").write_text(
                "import json\nfrom pathlib import Path\n"
                "json.loads((Path(__file__).parent / 'checks.json').read_text())\n")
            (checks_dir / "checks.json").write_text("{")
            with mock.patch.object(hooks, "__file__", str(root / ".codex" / "hooks.py")):
                code, output = self.invoke("post-tool-use", payload)
        self.assertEqual(0, code)
        self.assertNotIn("permissionDecision", output["hookSpecificOutput"])
        self.assertIn("Repository check unavailable", output["hookSpecificOutput"]["additionalContext"])

    def test_unexpected_exception_is_reported_without_denying_push(self):
        payload = {"tool_name": "Bash", "tool_input": {"command": "git push"}}
        with mock.patch.object(hooks, "load_checks") as load:
            load.return_value.post_tool_use.side_effect = TypeError("unexpected payload")
            code, output = self.invoke("post-tool-use", payload)
        self.assertEqual(0, code)
        self.assertNotIn("permissionDecision", output["hookSpecificOutput"])
        self.assertIn("unexpected payload", output["hookSpecificOutput"]["additionalContext"])

    def test_blocked_stop_uses_codex_monitoring_guidance(self):
        claude_reason = ("Still running or not reported yet (monitor until they finish):\n"
                         "If nothing has failed and you have set up monitoring that will re-invoke you (Monitor tool, "
                         "background gh run watch, ScheduleWakeup), end your reply with a line "
                         "'CI-WAITING: <what you are monitoring>'.\n"
                         "Only if the user explicitly said in this session that CI can be ignored, end your reply")
        with mock.patch.object(hooks, "load_checks") as load:
            load.return_value.stop.return_value = True, claude_reason
            code, output = self.invoke("stop", {})
        self.assertEqual(0, code)
        self.assertEqual("block", output["decision"])
        self.assertIn("gh run watch", output["reason"])
        self.assertIn("CI-WAITING:", output["reason"])
        self.assertNotIn("Monitor tool", output["reason"])
        self.assertNotIn("ScheduleWakeup", output["reason"])
        self.assertNotIn("Only if the user", output["reason"])


if __name__ == "__main__":
    unittest.main()
