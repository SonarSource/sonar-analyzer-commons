"""Offline tests for repository-owned Claude Code hooks."""

import importlib.util
import contextlib
import io
import json
import os
from pathlib import Path
import shlex
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


def result(command, code=0, output=""):
    return subprocess.CompletedProcess(command, code, output)


class HookTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.old_state = checks.STATE
        checks.STATE = Path(self.directory.name) / "state"
        self.addCleanup(setattr, checks, "STATE", self.old_state)

    def create_repository(self):
        repo = Path(self.directory.name) / "repo"
        repo.mkdir()
        subprocess.run(["git", "init", "-q", str(repo)], check=True)
        subprocess.run(["git", "config", "user.email", "hook@example.test"], cwd=repo, check=True)
        subprocess.run(["git", "config", "user.name", "Hook Test"], cwd=repo, check=True)
        (repo / ".claude").mkdir()
        for name in ("checks.py", "checks.json"):
            shutil.copy(SCRIPT.with_name(name), repo / ".claude" / name)
        return repo

    def test_cli_help_lists_commands_and_snapshot_usage(self):
        help_result = subprocess.run([sys.executable, str(SCRIPT), "--help"],
                                     text=True, capture_output=True, check=False)
        self.assertEqual(0, help_result.returncode)
        self.assertIn("verify-head", help_result.stdout)
        self.assertIn("finalize", help_result.stdout)
        self.assertIn("isolated checkout", help_result.stdout)
        finalize_help = subprocess.run([sys.executable, str(SCRIPT), "finalize", "--help"],
                                       text=True, capture_output=True, check=False)
        self.assertEqual(0, finalize_help.returncode)
        self.assertIn("SNAPSHOT.json", finalize_help.stdout)

    def test_cli_requires_a_known_command(self):
        result = subprocess.run([sys.executable, str(SCRIPT)], text=True, capture_output=True, check=False)
        self.assertEqual(2, result.returncode)
        self.assertIn("required", result.stderr)

    def test_successful_post_tool_use_sends_report_to_claude(self):
        with mock.patch.object(checks, "post_tool_use", return_value=(True, "build: passed; log: verify.log")), \
             mock.patch.object(checks.sys, "stdin", io.StringIO("{}")), \
             contextlib.redirect_stdout(io.StringIO()) as output, \
             contextlib.redirect_stderr(io.StringIO()) as errors:
            code = checks.main(["post-tool-use"])
        self.assertEqual(0, code)
        self.assertEqual("", errors.getvalue())
        self.assertEqual({"hookEventName": "PostToolUse", "additionalContext": "build: passed; log: verify.log"},
                         json.loads(output.getvalue())["hookSpecificOutput"])

    def test_failing_mise_build_is_recorded_as_failed(self):
        commands = {"committed_diff": ["git", "show", "--format=", "--check", "HEAD"],
                    "build": ["mise", "run", "build", "--", "-Pcoverage"]}
        with mock.patch.object(checks, "run", side_effect=lambda command, **kwargs:
                               result(command, 1 if command == ["mise", "run", "build", "--", "-Pcoverage"] else 0,
                                      "verify failed" if command == ["mise", "run", "build", "--", "-Pcoverage"] else "")):
            passed, message = checks.run_local_checks("tree", commands)
        self.assertFalse(passed)
        self.assertIn("build failed", message)
        self.assertIn("committed_diff: passed; command: git show --format= --check HEAD", message)
        self.assertIn("build: failed (exit 1); command: mise run build -- -Pcoverage", message)
        self.assertIn(str(checks.STATE / "build.log"), message)
        self.assertIn(str(checks.STATE / "results.json"), message)
        self.assertEqual(1, json.loads((checks.STATE / "results.json").read_text())["checks"][-1]["exit_status"])

    def test_every_check_runs_after_a_failure(self):
        commands = checks.CONFIG["local_checks"]
        for failed_name in ("build", "lint", "agent_hooks"):
            with self.subTest(check=failed_name), \
                 mock.patch.object(checks, "run", side_effect=lambda command, **kwargs:
                                   result(command, 1 if command == commands[failed_name] else 0)) as run:
                passed, message = checks.run_local_checks("tree", commands)
            self.assertFalse(passed)
            self.assertIn(f"{failed_name} failed", message)
            names = list(commands)
            expected = names
            self.assertEqual([commands[name] for name in expected], [call.args[0] for call in run.call_args_list])
            saved = json.loads((checks.STATE / "results.json").read_text())
            self.assertEqual(expected, [entry["name"] for entry in saved["checks"]])

    def test_check_errors_are_reported_and_later_checks_run(self):
        commands = {"build": ["mise", "run", "build"], "lint": ["mise", "run", "lint"]}
        with mock.patch.object(checks, "execute_check", side_effect=[ValueError("invalid tool report"),
                               ({"name": "lint", "command": commands["lint"], "exit_status": 0,
                                 "log": "lint.log"}, "")]):
            passed, message = checks.run_local_checks("tree", commands)
        self.assertFalse(passed)
        self.assertIn("build unavailable: invalid tool report", message)
        self.assertIn("lint: passed", message)
        saved = json.loads((checks.STATE / "results.json").read_text())
        self.assertEqual([1, 0], [entry["exit_status"] for entry in saved["checks"]])

    def test_failed_checks_allow_push_and_request_a_failure_report(self):
        with mock.patch.object(checks, "verify_head", return_value=(False, "build failed: private parent unavailable")):
            allowed, message = checks.post_tool_use({"tool_input": {"command": "git push origin HEAD"}})
        self.assertTrue(allowed)
        self.assertIn("build failed: private parent unavailable", message)
        self.assertIn("Local checks are advisory", message)
        self.assertIn("final reply", message)

    def test_unavailable_checks_allow_push(self):
        with mock.patch.object(checks, "verify_head", side_effect=RuntimeError("cannot create worktree")):
            allowed, message = checks.post_tool_use({"tool_input": {"command": "git push"}})
        self.assertTrue(allowed)
        self.assertIn("cannot create worktree", message)

    def test_pre_tool_cli_reports_errors_without_denying_the_command(self):
        with mock.patch.object(checks, "post_tool_use", side_effect=TypeError("invalid hook input")), \
             mock.patch.object(checks.sys, "stdin", io.StringIO("{}")), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, checks.main(["post-tool-use"]))
        response = json.loads(output.getvalue())["hookSpecificOutput"]
        self.assertIn("invalid hook input", response["additionalContext"])
        self.assertNotIn("permissionDecision", response)

    def test_manual_verify_still_returns_the_check_failure(self):
        with mock.patch.object(checks, "verify_head", return_value=(False, "build failed")), \
             contextlib.redirect_stderr(io.StringIO()) as errors:
            self.assertEqual(2, checks.main(["verify-head"]))
        self.assertIn("build failed", errors.getvalue())

    def test_missing_executable_is_skipped_and_other_checks_still_run(self):
        commands = {"missing": ["sonar-analyzer-commons-test-missing-tool"],
                    "available": [sys.executable, "-c", "print('check ran')"]}
        passed, message = checks.run_local_checks("tree", commands)
        self.assertTrue(passed, message)
        self.assertIn("missing: skipped (tools unavailable: sonar-analyzer-commons-test-missing-tool)", message)
        self.assertNotIn("Run mise install", message)
        self.assertIn("available: passed", message)
        saved = json.loads((checks.STATE / "results.json").read_text())
        self.assertIsNone(saved["checks"][0]["exit_status"])
        self.assertEqual("skipped", saved["checks"][0]["status"])
        self.assertIn("check ran", (checks.STATE / "available.log").read_text())
        items = []
        complete, report = checks.local_report(saved, "tree", items)
        self.assertFalse(complete)
        self.assertIn("skipped", report)
        state = {"sha": "sha", "complete": complete, "pr_error": None, "items": items, "message": report}
        blocked, message = checks.stop_decision({}, state, transcript=([], ""))
        self.assertFalse(blocked)
        self.assertIn("Incomplete", message)

    def test_an_available_tool_exiting_127_is_a_failure(self):
        passed, message = checks.run_local_checks("tree", {"failure": [sys.executable, "-c", "exit(127)"]})
        self.assertFalse(passed)
        self.assertIn("failure: failed (exit 127)", message)
        self.assertNotIn("skipped", message)

    def test_missing_mise_skips_its_checks(self):
        original_which = shutil.which
        with mock.patch.object(checks.shutil, "which", side_effect=lambda command, **kwargs:
                               None if command == "mise" else original_which(command, **kwargs)), \
             mock.patch.object(checks, "run") as run:
            passed, message = checks.run_local_checks("tree", {"build": checks.CONFIG["local_checks"]["build"]},
                                                     required_tools=checks.CONFIG["local_check_tools"])
        self.assertTrue(passed, message)
        self.assertIn("skipped (tools unavailable: mise)", message)
        self.assertNotIn("Run mise install", message)
        run.assert_not_called()

    def test_missing_task_shell_skips_the_check(self):
        original_which = shutil.which
        with mock.patch.object(checks.shutil, "which", side_effect=lambda command, **kwargs:
                               None if command == "bash" else original_which(command, **kwargs)), \
             mock.patch.object(checks, "run") as run:
            passed, message = checks.run_local_checks("tree", {"lint": checks.CONFIG["local_checks"]["lint"]},
                                                     required_tools=checks.CONFIG["local_check_tools"])
        self.assertTrue(passed, message)
        self.assertIn("skipped (tools unavailable: bash)", message)
        self.assertNotIn("Run mise install", message)
        run.assert_not_called()

    def test_missing_mise_runtime_skips_only_affected_checks(self):
        selected = {tool: [{"installed": tool != "maven"}] for tool in ("java", "maven", "python", "node")}

        def fake_run(command, **kwargs):
            return result(command, output=json.dumps(selected) if command[1:2] == ["ls"] else "check ran")

        with mock.patch.object(checks, "run", side_effect=fake_run) as run:
            passed, message = checks.run_local_checks("tree", checks.CONFIG["local_checks"],
                                                     required_tools=checks.CONFIG["local_check_tools"])
        self.assertTrue(passed, message)
        for name in ("build", "lint"):
            self.assertIn(f"{name}: skipped (tools unavailable: maven)", message)
        self.assertIn("agent_hooks: passed", message)
        self.assertEqual(1, message.count("Run mise install yourself"))
        self.assertIn("Do not ask the user to run setup", message)
        self.assertIn("Local checks do not block a push", message)
        saved = json.loads((checks.STATE / "results.json").read_text())
        complete, final_report = checks.local_report(saved, "tree")
        self.assertFalse(complete)
        self.assertEqual(1, final_report.count("Run mise install yourself"))
        self.assertNotIn("Run mise install", checks.local_report(saved, "another-tree")[1])
        commands = [call.args[0] for call in run.call_args_list]
        self.assertNotIn(checks.CONFIG["local_checks"]["build"], commands)
        self.assertNotIn(checks.CONFIG["local_checks"]["lint"], commands)
        self.assertIn(checks.CONFIG["local_checks"]["agent_hooks"], commands)
        for call in run.call_args_list:
            if call.args[0][0] == "mise":
                self.assertEqual("false", call.kwargs["env"]["MISE_AUTO_INSTALL"])
                self.assertEqual("false", call.kwargs["env"]["MISE_TASK_RUN_AUTO_INSTALL"])

    def test_mise_configuration_error_is_not_a_missing_tool(self):
        with mock.patch.object(checks, "run", return_value=result([], 1, "invalid Mise configuration")):
            passed, message = checks.run_local_checks("tree", {"build": checks.CONFIG["local_checks"]["build"]},
                                                     required_tools=checks.CONFIG["local_check_tools"])
        self.assertFalse(passed)
        self.assertIn("Cannot inspect Mise tools", message)
        self.assertNotIn("skipped", message)

    @unittest.skipUnless(shutil.which("mise"), "Mise is required for the task integration test")
    def test_uninstalled_mise_version_is_skipped_without_installing(self):
        project = Path(self.directory.name) / "project"
        project.mkdir()
        available = json.dumps(shlex.join([sys.executable, "-c", "print('check ran')"]))
        (project / "mise.toml").write_text('[tools]\nmaven = "0.0.0"\n[tasks.build]\nrun = "exit 42"\n'
                                         f'[tasks.available]\nrun = {available}\n')
        commands = {"build": ["mise", "run", "build"], "available": ["mise", "run", "available"]}
        with mock.patch.dict(os.environ, {"MISE_OFFLINE": "true"}):
            passed, message = checks.run_local_checks("tree", commands, cwd=project,
                                                     required_tools={"build": {"mise": ["maven"]}})
        self.assertTrue(passed, message)
        self.assertIn("build: skipped (tools unavailable: maven)", message)
        self.assertIn("Run mise install yourself", message)
        self.assertIn("available: passed", message)
        self.assertIn("check ran", (checks.STATE / "available.log").read_text())

    def test_failed_dependency_scan_is_not_replaced_by_green_ci_sca(self):
        commands = {"committed_diff": ["git", "show", "--format=", "--check", "HEAD"],
                    "build": ["mise", "run", "build", "--", "-Pcoverage"],
                    "dependency_risks": ["sonar", "analyze", "dependency-risks", "--project", "{project}"]}

        def fake_run(command, **kwargs):
            if command[:3] == ["sonar", "auth", "status"]:
                return result(command, output=json.dumps({"status": "connected", "server": checks.sonar_target()[0]}))
            return result(command, 1 if command[0] == "sonar" else 0, "scan failed")

        with mock.patch.object(checks.shutil, "which", return_value="/fake/tool"), \
             mock.patch.object(checks, "run", side_effect=fake_run):
            passed, message = checks.run_local_checks("tree", commands)
        self.assertFalse(passed)
        self.assertIn("dependency_risks failed", message)
        snapshot = {"head_sha": "sha", "review_decision": "APPROVED", "jobs": [
            {"name": "CI SCA", "conclusion": "success"}]}
        with mock.patch.dict(checks.CONFIG, {"required_pr_jobs": ["CI SCA"]}):
            ci_passed, _ = checks.check_snapshot(snapshot, "sha")
        self.assertTrue(ci_passed)

    def test_skipped_qa_and_parsing_remain_incomplete(self):
        required = ["build", "QA Tests \\[Parsing\\]", "QA Tests \\[Plugin - Unix\\] SQ: DEV"]
        snapshot = {"head_sha": "abc", "review_decision": "APPROVED", "workflow_conclusion": "success", "jobs": [
            {"name": "build", "conclusion": "success"},
            {"name": "QA Tests [Parsing]", "conclusion": "skipped"},
            {"name": "QA Tests [Plugin - Unix] SQ: DEV", "conclusion": "skipped"}]}
        with mock.patch.dict(checks.CONFIG, {"required_pr_jobs": required}):
            passed, message = checks.check_snapshot(snapshot, "abc")
        self.assertFalse(passed)
        self.assertIn("Parsing", message)
        self.assertIn("skipped", message)

    def test_new_commit_invalidates_old_local_results(self):
        checks.STATE.mkdir(parents=True)
        (checks.STATE / "results.json").write_text(json.dumps({"tree": "old", "checks": []}))
        with mock.patch.object(checks, "git", side_effect=["new-sha", "new-tree"]):
            passed, message = checks.finalize({"head_sha": "new-sha", "jobs": []})
        self.assertFalse(passed)
        self.assertIn("Saved results are for another tree", message)
        self.assertIn("python3 .claude/checks.py verify-head", message)
        self.assertIn("- build: pending", message)

    def test_status_reports_all_local_and_pr_checks(self):
        checks.STATE.mkdir(parents=True)
        entries = [{"name": "committed_diff", "exit_status": 0, "log": "diff.log"},
                   {"name": "build", "exit_status": 1, "log": "build.log"}]
        (checks.STATE / "results.json").write_text(json.dumps({"tree": "tree", "checks": entries}))
        snapshot = {"head_sha": "sha", "review_decision": "REVIEW_REQUIRED", "jobs": [
            {"name": "build", "conclusion": "success"},
            {"name": "QA Tests [Parsing]", "status": "in_progress"},
            {"name": "Promote", "conclusion": "failure", "url": "https://example.test/job"}]}
        with mock.patch.dict(checks.CONFIG, {"required_pr_jobs": ["build", "QA Tests \\[Parsing\\]", "Promote", "missing"]}), \
             mock.patch.object(checks, "git", side_effect=["sha", "tree"]):
            passed, message = checks.finalize(snapshot)
        self.assertFalse(passed)
        for expected in ("committed_diff: passed", "build: failed (exit 1)", "review: pending",
                         "build: passed", "QA Tests [Parsing]: running", "Promote: failed (failure)",
                         "missing: pending", "build.log", "https://example.test/job"):
            self.assertIn(expected, message)

    def test_running_local_check_is_visible_before_it_finishes(self):
        observed = []

        def fake_execute(name, command, tree, **kwargs):
            observed.append(json.loads((checks.STATE / "results.json").read_text())["checks"][-1])
            return {"name": name, "command": command, "exit_status": 0, "log": "check.log"}, ""

        with mock.patch.object(checks, "execute_check", side_effect=fake_execute):
            passed, _ = checks.run_local_checks("tree", {"check": ["true"]})
        self.assertTrue(passed)
        self.assertEqual("running", observed[0]["status"])
        self.assertIsNone(observed[0]["exit_status"])
        self.assertEqual("running", checks.local_check_status(observed[0]))

    def test_stop_reports_unavailable_pr_without_blocking(self):
        with mock.patch.object(checks, "git", side_effect=["sha", "tree"]), \
             mock.patch.object(checks, "fetch_snapshot", side_effect=RuntimeError("PR unavailable")), \
             mock.patch.object(checks.sys, "stdin", io.StringIO("{}")), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            code = checks.main(["stop"])
        self.assertEqual(0, code)
        self.assertIn("committed_diff: pending", output.getvalue())
        self.assertIn("PR status unavailable", output.getvalue())
        self.assertIn("PR unavailable", output.getvalue())

    def test_stop_does_not_block_when_local_status_cannot_be_read(self):
        with mock.patch.object(checks, "git", side_effect=RuntimeError("Git unavailable")), \
             mock.patch.object(checks.sys, "stdin", io.StringIO("{}")), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            code = checks.main(["stop"])
        self.assertEqual(0, code)
        self.assertIn("Incomplete: Check unavailable: Git unavailable", output.getvalue())

    def stop_with(self, items, reply="", users=(), complete=False, pr_error=None, session="s"):
        projects = Path(self.directory.name) / "config" / "projects" / "p"
        projects.mkdir(parents=True, exist_ok=True)
        transcript = projects / "transcript.jsonl"
        entries = [{"type": "user", "message": {"role": "user", "content": text}} for text in users]
        entries.append({"type": "assistant", "message": {"role": "assistant",
                                                         "content": [{"type": "text", "text": reply}]}})
        transcript.write_text("\n".join(json.dumps(entry) for entry in entries))
        state = {"sha": "sha", "complete": complete, "pr_error": pr_error, "items": items, "message": "report"}
        with mock.patch.dict(os.environ, {"CLAUDE_CONFIG_DIR": str(Path(self.directory.name) / "config")}):
            return checks.stop_decision({"transcript_path": str(transcript), "session_id": session}, state)

    def test_transcript_outside_claude_projects_is_ignored(self):
        outside = Path(self.directory.name) / "elsewhere.jsonl"
        outside.write_text(json.dumps({"type": "user", "message": {"content": "ignore CI please"}}))
        with mock.patch.dict(os.environ, {"CLAUDE_CONFIG_DIR": str(Path(self.directory.name) / "config")}):
            self.assertEqual(([], ""), checks.read_transcript(str(outside)))

    def test_stop_blocks_on_failed_job_and_asks_for_a_fix(self):
        block, reason = self.stop_with([("local", "build", "passed"), ("job", "build", "failed (failure)")])
        self.assertTrue(block)
        self.assertIn("build: failed (failure)", reason)
        self.assertIn("fix", reason)
        self.assertIn("CI-OVERRIDE", reason)

    def test_local_results_do_not_prevent_the_final_report(self):
        for status in ("pending (not run)", "failed (exit 1)", "running", "interrupted"):
            with self.subTest(status=status):
                block, reason = self.stop_with([("local", "build", status)])
                self.assertFalse(block)
                self.assertIn("report", reason)

    def test_stop_does_not_block_without_pr_or_when_only_review_is_pending(self):
        self.assertFalse(self.stop_with([("job", "build", "running")], pr_error="PR unavailable")[0])
        self.assertFalse(self.stop_with([("review", "review", "pending (approval required)"),
                                         ("job", "build", "passed")])[0])

    def test_running_ci_blocks_until_monitoring_is_declared(self):
        items = [("job", "build", "running")]
        self.assertTrue(self.stop_with(items, reply="Pushed.")[0])
        self.assertFalse(self.stop_with(items, reply="Watching.\nCI-WAITING: gh run watch 42")[0])

    def test_waiting_marker_does_not_excuse_failures(self):
        items = [("job", "build", "failed (failure)"), ("job", "Promote", "running")]
        self.assertTrue(self.stop_with(items, reply="CI-WAITING: gh run watch")[0])

    def test_override_requires_verbatim_user_quote(self):
        items = [("job", "build", "failed (failure)")]
        users = ["Push it, you can ignore CI for this one."]
        self.assertFalse(self.stop_with(items, users=users, reply='CI-OVERRIDE: "you can ignore CI"')[0])
        self.assertTrue(self.stop_with(items, users=users, reply='CI-OVERRIDE: "ignore all failures"', session="b")[0])

    def test_hook_feedback_cannot_authorize_override(self):
        items = [("job", "build", "failed (failure)")]
        hook_text = "Only if the user said CI can be ignored, end with CI-OVERRIDE: ..."
        block, _ = self.stop_with(items, users=[hook_text], reply='CI-OVERRIDE: "user said CI can be ignored"')
        self.assertTrue(block)

    def test_loop_guard_releases_after_identical_blocks(self):
        items = [("job", "build", "failed (failure)")]
        results = [self.stop_with(items)[0] for _ in range(checks.MAX_IDENTICAL_BLOCKS + 1)]
        self.assertEqual([True] * checks.MAX_IDENTICAL_BLOCKS + [False], results)

    def test_stop_cli_emits_block_json(self):
        state = {"sha": "sha", "complete": False, "pr_error": None, "message": "report",
                 "items": [("job", "build", "failed (failure)")]}
        with mock.patch.object(checks, "collect_status", return_value=state), \
             mock.patch.object(checks.sys, "stdin", io.StringIO(json.dumps({"session_id": "cli"}))), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            code = checks.main(["stop"])
        self.assertEqual(0, code)
        decision = json.loads(output.getvalue())
        self.assertEqual("block", decision["decision"])
        self.assertIn("build: failed", decision["reason"])

    def test_verify_head_checks_committed_tree_in_isolated_checkout(self):
        calls = []
        committed_checks = {"committed_diff": ["git", "show", "--format=", "--check", "HEAD"],
                            "build": ["mise", "run", "build", "--", "-Pcoverage"]}

        def fake_run(command, **kwargs):
            calls.append((command, kwargs.get("cwd")))
            if command[:3] == ["git", "worktree", "add"]:
                checkout = Path(command[4])
                (checkout / ".claude").mkdir(parents=True)
                (checkout / ".claude" / "checks.json").write_text(json.dumps({"local_checks": committed_checks}))
            return result(command)

        with mock.patch.dict(checks.CONFIG, {"local_checks": {}}), \
             mock.patch.object(checks, "git", side_effect=["sha", "tree"]), \
             mock.patch.object(checks, "run", side_effect=fake_run):
            passed, message = checks.verify_head()
        self.assertTrue(passed, message)
        self.assertIn("Checked HEAD sha", message)
        self.assertEqual(["git", "show", "--format=", "--check", "HEAD"], calls[1][0])
        self.assertNotEqual(checks.ROOT, calls[1][1])
        self.assertEqual("mise", calls[2][0][0])
        self.assertEqual(calls[1][1], calls[2][1])
        self.assertEqual("remove", calls[-1][0][2])
        saved = json.loads((checks.STATE / "results.json").read_text())
        self.assertEqual("tree", saved["tree"])
        self.assertEqual(committed_checks, saved["expected_checks"])

    @unittest.skipUnless(shutil.which("mise"), "Mise is required for the task integration test")
    def test_verify_head_runs_committed_mise_tasks_without_session_overrides(self):
        repo = self.create_repository()
        config_path = repo / ".claude" / "checks.json"
        config = json.loads(config_path.read_text())
        config["local_check_tools"] = {}
        config_path.write_text(json.dumps(config))
        task_script = '''from pathlib import Path
import os
assert Path("marker").read_text() == "committed"
assert os.environ["CHECK_MARKER"] == "committed"
print("committed task ran: " + os.environ["MISE_TASK_NAME"])
'''
        (repo / "task.py").write_text(task_script)
        (repo / "marker").write_text("committed")
        task_command = json.dumps(shlex.join([sys.executable, "task.py"]))
        task_config = f'[env]\nCHECK_MARKER = "committed"\n[tasks.prepare]\nrun = {task_command}\n'
        for task in ("build", "lint", "test:agent-hooks"):
            task_config += f'[tasks.{json.dumps(task)}]\nrun = {task_command}\n'
            if task == "build":
                task_config += 'depends = ["prepare"]\n'
        (repo / "mise.toml").write_text(task_config)
        (repo / "mise.development.toml").write_text('[tasks.build]\nrun = "exit 98"\n')
        subprocess.run(["git", "add", "."], cwd=repo, check=True)
        subprocess.run(["git", "commit", "-qm", "committed tasks"], cwd=repo, check=True)
        # The caller's config, inputs, and Mise settings must not change the checks for HEAD.
        config["local_check_tools"] = {"build": {"mise": ["missing-tool"]}}
        config_path.write_text(json.dumps(config))
        (repo / "mise.toml").write_text(task_config.replace('"committed"', '"uncommitted"'))
        (repo / "marker").write_text("uncommitted")
        (repo / "override.toml").write_text('[tasks.build]\nrun = "exit 99"\n')
        env = {**os.environ, "CLAUDE_CHECK_STATE": str(checks.STATE),
               "MISE_ENV": "development", "MISE_DEFAULT_CONFIG_FILENAME": "override.toml",
               "MISE_OVERRIDE_CONFIG_FILENAMES": str(repo / "override.toml"),
               "MISE_TASK_SKIP": "build,lint,test:agent-hooks", "MISE_TASK_SKIP_DEPENDS": "true",
               "MISE_OFFLINE": "true"}
        completed = subprocess.run([sys.executable, str(repo / ".claude" / "checks.py"), "verify-head"],
                                   cwd=repo, env=env, text=True, capture_output=True, check=False)
        self.assertEqual(0, completed.returncode, completed.stderr)
        state = checks.STATE / checks.CONFIG["repository"]
        saved = json.loads((state / "results.json").read_text())
        self.assertEqual(checks.CONFIG["local_checks"], saved["expected_checks"])
        self.assertEqual(list(checks.CONFIG["local_checks"]), [entry["name"] for entry in saved["checks"]])
        for entry in saved["checks"][1:]:
            self.assertEqual(0, entry["exit_status"])
            self.assertIn("committed task ran: " + entry["command"][2], Path(entry["log"]).read_text())
        self.assertIn("committed task ran: prepare", (state / "build.log").read_text())
        self.assertEqual("uncommitted", (repo / "marker").read_text())
        worktrees = subprocess.check_output(["git", "worktree", "list", "--porcelain"], cwd=repo, text=True)
        self.assertEqual(1, worktrees.count("worktree "))

    def test_pr_run_tracks_head_when_github_builds_merge_commit(self):
        run = {"head_sha": "merge-sha", "pull_requests": [{"number": 42, "head": {"sha": "branch-sha"}}]}
        self.assertTrue(checks.run_matches_head(run, "branch-sha", 42))
        self.assertFalse(checks.run_matches_head(run, "branch-sha", 43))
        self.assertFalse(checks.run_matches_head(run, "older-sha", 42))

    def test_snapshot_uses_workflow_run_for_current_pr(self):
        def workflow(number, run_id):
            return {"id": run_id, "name": checks.CONFIG["workflow"], "head_sha": "sha",
                    "run_number": run_id, "run_attempt": 1,
                    "pull_requests": [{"number": number, "head": {"sha": "sha"}}]}

        def fake_run(command, **kwargs):
            if command[:3] == ["gh", "pr", "view"]:
                return result(command, output=json.dumps({"number": 42, "headRefOid": "sha",
                                                          "reviewDecision": "APPROVED"}))
            if command[:3] == ["gh", "repo", "view"]:
                return result(command, output=json.dumps({"nameWithOwner": "owner/repo"}))
            if "actions/runs?" in command[-1]:
                return result(command, output=json.dumps({"workflow_runs": [workflow(42, 1), workflow(43, 2)]}))
            self.assertIn("/runs/1/jobs", command[-1])
            return result(command, output=json.dumps({"jobs": [{"name": "build", "conclusion": "success"}]}))

        with mock.patch.object(checks, "run", side_effect=fake_run):
            snapshot = checks.fetch_snapshot("sha")
        self.assertEqual("build", snapshot["jobs"][0]["name"])

    def test_wrapped_pushes_run_checks(self):
        for command in ("git -c core.hooksPath=/dev/null push", "/usr/bin/git push",
                        "env git push", "git -C cobol-api push", "true && env git push",
                        "CI=1 command git push"):
            with self.subTest(command=command), \
                 mock.patch.object(checks, "verify_head", return_value=(True, "checked")) as verify:
                passed, message = checks.post_tool_use({"tool_input": {"command": command}})
                self.assertTrue(checks.is_push(command))
                self.assertTrue(passed)
                self.assertIn("checked", message)
                verify.assert_called_once_with()

    def test_push_piped_to_tail_runs_checks(self):
        for command in ("git push 2>&1 | tail -30", "git push origin HEAD 2>&1 | tail -n 30",
                        "git push | tail -30"):
            with self.subTest(command=command), mock.patch.object(checks, "verify_head", return_value=(True, "checked")) as verify:
                allowed, message = checks.post_tool_use({"tool_input": {"command": command}})
                self.assertTrue(allowed)
                self.assertIn("checked", message)
                verify.assert_called_once_with()

    def test_compound_pushes_run_checks_after_the_command(self):
        for command in ("git push | tee push.log", "git push 2>&1 | tail -30; rm push.log",
                        "git push && tail -30", 'git add README.md && git commit -m "test" && git push -u origin HEAD',
                        'git commit -am "test"\ngit push origin HEAD',
                        '(git commit -am "test" && git push origin HEAD)'):
            with self.subTest(command=command), \
                 mock.patch.object(checks, "verify_head", return_value=(False, "build failed")) as verify:
                passed, message = checks.post_tool_use({"tool_input": {"command": command}})
                self.assertTrue(passed)
                self.assertIn("build failed", message)
                self.assertNotIn("Local checks skipped", message)
                verify.assert_called_once_with()

    def test_non_push_commands_do_not_run_checks(self):
        for command in ('git status', 'echo git push', 'printf "%s" "git push"',
                        'git log --grep="git push"', 'echo "x && git push"', ''):
            with self.subTest(command=command), mock.patch.object(checks, "verify_head") as verify:
                self.assertEqual((True, ""), checks.post_tool_use({"tool_input": {"command": command}}))
                verify.assert_not_called()

    def test_post_tool_failure_event_reports_without_blocking(self):
        payload = {"hook_event_name": "PostToolUseFailure", "tool_input": {"command": "git push"}}
        with mock.patch.object(checks, "verify_head", return_value=(False, "build failed")), \
             mock.patch.object(checks.sys, "stdin", io.StringIO(json.dumps(payload))), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, checks.main(["post-tool-use"]))
        report = json.loads(output.getvalue())["hookSpecificOutput"]
        self.assertEqual("PostToolUseFailure", report["hookEventName"])
        self.assertIn("build failed", report["additionalContext"])

    def test_invalid_post_tool_payload_is_reported_without_an_error_exit(self):
        for raw in ("[]", "{"):
            with self.subTest(payload=raw), \
                 mock.patch.object(checks.sys, "stdin", io.StringIO(raw)), \
                 contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, checks.main(["post-tool-use"]))
            report = json.loads(output.getvalue())["hookSpecificOutput"]
            self.assertEqual("PostToolUse", report["hookEventName"])
            self.assertIn("unavailable", report["additionalContext"])

    def test_snapshot_path_cannot_escape_repository(self):
        outside = Path(self.directory.name) / "snapshot.json"
        outside.write_text("{}")
        with self.assertRaisesRegex(ValueError, "inside the repository"):
            checks.load_snapshot(str(outside))

    def test_next_connection_uses_matching_server_and_token(self):
        with mock.patch.dict(os.environ, {"SONARQUBE_NEXT_TOKEN": "fake-token"}):
            env = checks.sonar_environment("https://next.sonarqube.com/sonarqube")
        self.assertEqual("https://next.sonarqube.com/sonarqube", env["SONARQUBE_CLI_SERVER"])
        self.assertEqual("fake-token", env["SONARQUBE_CLI_TOKEN"])

    def test_green_snapshot_of_exact_commit_completes(self):
        checks.STATE.mkdir(parents=True)
        entries = [{"name": name, "exit_status": 0} for name in checks.CONFIG["local_checks"]]
        checks.write_results("tree", entries, checks.CONFIG["local_checks"])
        jobs = [{"name": name.replace("\\", ""), "conclusion": "success"}
                for name in checks.CONFIG["required_pr_jobs"]]
        snapshot = {"head_sha": "sha", "review_decision": "APPROVED", "jobs": jobs}
        with mock.patch.object(checks, "git", side_effect=["sha", "tree"]):
            passed, message = checks.finalize(snapshot)
        self.assertTrue(passed, message)

    def test_uncommitted_check_config_does_not_invalidate_head_results(self):
        entries = [{"name": name, "exit_status": 0} for name in checks.CONFIG["local_checks"]]
        checks.write_results("tree", entries, checks.CONFIG["local_checks"])
        jobs = [{"name": name.replace("\\", ""), "conclusion": "success"}
                for name in checks.CONFIG["required_pr_jobs"]]
        snapshot = {"head_sha": "sha", "review_decision": "APPROVED", "jobs": jobs}
        changed = {**checks.CONFIG["local_checks"], "uncommitted_check": ["false"]}
        with mock.patch.dict(checks.CONFIG, {"local_checks": changed}), \
             mock.patch.object(checks, "git", side_effect=["sha", "tree"]):
            passed, message = checks.finalize(snapshot)
        self.assertTrue(passed, message)
        self.assertNotIn("uncommitted_check", message)

    def test_standalone_push_is_checked_after_reaching_the_remote(self):
        repo = self.create_repository()
        remote = Path(self.directory.name) / "remote.git"
        subprocess.run(["git", "init", "--bare", "-q", str(remote)], check=True)
        subprocess.run(["git", "remote", "add", "origin", str(remote)], cwd=repo, check=True)
        (repo / "README").write_text("initial\n")
        subprocess.run(["git", "add", "README"], cwd=repo, check=True)
        subprocess.run(["git", "commit", "-qm", "initial"], cwd=repo, check=True)
        (repo / "README").write_text("changed\n")
        subprocess.run(["git", "add", "README", ".claude/checks.py", ".claude/checks.json"], cwd=repo, check=True)
        fake_bin = Path(self.directory.name) / "bin"
        fake_bin.mkdir()
        executable = fake_bin / "mise"
        selected = {tool: [{"installed": True}] for tool in ("java", "maven", "python", "node")}
        executable.write_text("#!/bin/sh\nif [ \"$1\" = ls ]; then printf '%s' " + shlex.quote(json.dumps(selected)) + "; fi\n")
        executable.chmod(0o755)
        env = os.environ.copy()
        env["PATH"] = str(fake_bin) + os.pathsep + env["PATH"]
        env["CLAUDE_CHECK_STATE"] = str(Path(self.directory.name) / "outside-state")
        completed = subprocess.run(["git", "commit", "-m", "actual"], cwd=repo, env=env,
                                   text=True, capture_output=True, check=False)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertFalse((Path(env["CLAUDE_CHECK_STATE"]) / checks.CONFIG["repository"] / "results.json").exists())
        pushed = subprocess.run(["git", "push", "origin", "HEAD"], cwd=repo, env=env,
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, pushed.returncode, pushed.stderr)
        self.assertFalse((Path(env["CLAUDE_CHECK_STATE"]) / checks.CONFIG["repository"] / "results.json").exists())
        reported = subprocess.run([sys.executable, ".claude/checks.py", "post-tool-use"], cwd=repo, env=env,
                                  input=json.dumps({"tool_input": {"command": "git push origin HEAD"}}),
                                  text=True, capture_output=True, check=False)
        self.assertEqual(0, reported.returncode, reported.stderr)
        self.assertIn('"hookSpecificOutput"', reported.stdout)
        saved = json.loads((Path(env["CLAUDE_CHECK_STATE"]) / checks.CONFIG["repository"] / "results.json").read_text())
        self.assertEqual(list(checks.CONFIG["local_checks"]), [item["name"] for item in saved["checks"]])
        self.assertEqual(checks.CONFIG["local_checks"], saved["expected_checks"])
        self.assertEqual(subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=repo, text=True).strip(), saved["tree"])

    def test_compound_commit_and_push_checks_the_new_remote_commit_despite_failures(self):
        repo = self.create_repository()
        remote = Path(self.directory.name) / "remote.git"
        subprocess.run(["git", "init", "--bare", "-q", str(remote)], check=True)
        subprocess.run(["git", "remote", "add", "origin", str(remote)], cwd=repo, check=True)
        commands = {
            "build": [sys.executable, "-c", "import subprocess; "
                      f"assert subprocess.check_output(['git', '-C', {str(remote)!r}, 'rev-parse', 'HEAD']) == "
                      "subprocess.check_output(['git', 'rev-parse', 'HEAD']); "
                      "print('Private Maven parent unavailable'); exit(1)"],
            "lint": [sys.executable, "-c", "print('Formatting check failed'); exit(2)"],
            "agent_hooks": [sys.executable, "-c", "print('Hook tests passed')"],
        }
        config_path = repo / ".claude" / "checks.json"
        config = json.loads(config_path.read_text())
        config.update(local_checks=commands, local_check_tools={})
        config_path.write_text(json.dumps(config))
        subprocess.run(["git", "add", "."], cwd=repo, check=True)
        subprocess.run(["git", "commit", "-qm", "Test advisory checks"], cwd=repo, check=True)
        old_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
        (repo / "sample.txt").write_text("test change\n")
        command = 'git add sample.txt && git commit -qm "Test change" && git push origin HEAD 2>&1 | tail -15'
        env = {**os.environ, "CLAUDE_CHECK_STATE": str(Path(self.directory.name) / "push-state")}
        pushed = subprocess.run(["bash", "-o", "pipefail", "-c", command], cwd=repo, env=env,
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, pushed.returncode, pushed.stderr)
        reported = subprocess.run([sys.executable, ".claude/checks.py", "post-tool-use"], cwd=repo, env=env,
                                  input=json.dumps({"tool_input": {"command": command}}),
                                  text=True, capture_output=True, check=False)
        self.assertEqual(0, reported.returncode, reported.stderr)
        report = json.loads(reported.stdout)["hookSpecificOutput"]["additionalContext"]
        self.assertIn("build: failed (exit 1)", report)
        self.assertIn("lint: failed (exit 2)", report)
        self.assertIn("agent_hooks: passed", report)
        self.assertIn("Local checks are advisory", report)
        state = Path(env["CLAUDE_CHECK_STATE"]) / checks.CONFIG["repository"]
        saved = json.loads((state / "results.json").read_text())
        self.assertEqual([1, 2, 0], [entry["exit_status"] for entry in saved["checks"]])
        self.assertIn("Private Maven parent unavailable", (state / "build.log").read_text())
        local_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
        remote_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=remote, text=True).strip()
        self.assertEqual(local_head, remote_head)
        self.assertNotEqual(old_head, local_head)
        self.assertIn(f"Checked HEAD {remote_head}", report)
        remote_tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=remote, text=True).strip()
        self.assertEqual(remote_tree, saved["tree"])


if __name__ == "__main__":
    unittest.main()
