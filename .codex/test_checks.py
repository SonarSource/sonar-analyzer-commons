"""Offline tests for the repository Codex hook contract."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).with_name("checks.py")
SPEC = importlib.util.spec_from_file_location("checks", SCRIPT)
checks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checks)


class HookTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        old_state = checks.STATE
        checks.STATE = Path(self.directory.name) / "state"
        self.addCleanup(setattr, checks, "STATE", old_state)

    def test_help_and_config(self):
        result = subprocess.run([sys.executable, str(SCRIPT), "--help"], capture_output=True, text=True)
        self.assertEqual(0, result.returncode)
        self.assertIn("verify-head", result.stdout)
        self.assertIn("finalize", result.stdout)
        hook_config = json.loads((SCRIPT.parent / "hooks.json").read_text())
        self.assertEqual("^Bash$", hook_config["hooks"]["PreToolUse"][0]["matcher"])

    def test_state_is_scoped_to_checkout(self):
        paths = []
        for name in ("checkout-a", "checkout-b"):
            script = Path(self.directory.name) / name / ".codex" / "checks.py"
            script.parent.mkdir(parents=True)
            shutil.copy2(SCRIPT, script)
            shutil.copy2(SCRIPT.with_name("checks.json"), script.with_name("checks.json"))
            spec = importlib.util.spec_from_file_location(name, script)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            paths.append(module.STATE)
        self.assertNotEqual(*paths)
        self.assertEqual(paths[0].parent, paths[1].parent)

    def test_successful_push_reports_checks_in_codex_context(self):
        with mock.patch.object(checks, "verify_head", return_value=(True, "maven_verify: passed")), \
             mock.patch.object(sys, "stdin", io.StringIO('{"tool_name":"Bash","tool_input":{"command":"git push"}}')), \
             contextlib.redirect_stdout(io.StringIO()) as out:
            code = checks.main(["pre-tool-use"])
        self.assertEqual(0, code)
        self.assertEqual("maven_verify: passed", json.loads(out.getvalue())["hookSpecificOutput"]["additionalContext"])

    def test_failed_push_blocks_and_chained_push_is_rejected(self):
        with mock.patch.object(checks, "verify_head", return_value=(False, "maven_verify failed")):
            self.assertEqual((False, "maven_verify failed"), checks.pre_tool_use({"tool_input": {"command": "git push"}}))
        passed, message = checks.pre_tool_use({"tool_input": {"command": "echo hi && git push"}})
        self.assertFalse(passed)
        self.assertIn("separate Bash tool call", message)
        self.assertEqual((True, ""), checks.pre_tool_use({"tool_input": {"command": "git commit -m test"}}))

    def test_shell_wrapped_push_is_rejected(self):
        for command in ('sh -c "git push"', "bash -lc 'git push origin HEAD'",
                        "zsh -c 'echo ready && git push'", "bash -c 'sh -c \"git push\"'"):
            with self.subTest(command=command):
                passed, message = checks.pre_tool_use({"tool_input": {"command": command}})
                self.assertFalse(passed)
                self.assertIn("separate Bash tool call", message)

    def test_stop_reports_json_and_running_checks(self):
        state = {"complete": False, "pr_error": "PR unavailable", "message": "maven_verify: running", "items": []}
        with mock.patch.object(checks, "collect_status", return_value=state), \
             mock.patch.object(sys, "stdin", io.StringIO("{}")), \
             contextlib.redirect_stdout(io.StringIO()) as out:
            code = checks.main(["stop"])
        self.assertEqual(0, code)
        self.assertIn("maven_verify: running", json.loads(out.getvalue())["systemMessage"])


if __name__ == "__main__":
    unittest.main()
