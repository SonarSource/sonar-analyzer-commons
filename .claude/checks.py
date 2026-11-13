#!/usr/bin/env python3
"""Local push reports and PR status checks for coding agents."""

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((Path(__file__).parent / "checks.json").read_text())
STATE = Path(os.environ.get("CLAUDE_CHECK_STATE", Path(tempfile.gettempdir()) / "claude-repo-checks")) / CONFIG["repository"]
PUSH_REPORT_GUIDANCE = ("Local checks are advisory. If a Mise command failed, run mise doctor project once "
                        "and inspect its results and the failure log. If tools are missing or installation "
                        "is incomplete, run mise install yourself unless the user forbids installation. "
                        "Then rerun the doctor and the failed check. Report remaining failed or skipped checks "
                        "and their causes in your final reply. Do not block an authorized push on local checks.")


def run(command, *, cwd=ROOT, log=None, env=None):
    try:
        result = subprocess.run(command, cwd=cwd, env=env, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, check=False)
    except FileNotFoundError as error:
        result = subprocess.CompletedProcess(command, 127, str(error))
    if log is not None:
        log.write_text(result.stdout)
    return result


def git(*args):
    result = run(["git", *args])
    if result.returncode:
        raise RuntimeError(result.stdout.strip())
    return result.stdout.strip()


def record(name, command, result, tree, log):
    return {"name": name, "command": command, "exit_status": result.returncode,
            "checked_tree": tree, "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "log": str(log)}


def running_record(name, command, tree):
    return {"name": name, "command": command, "exit_status": None, "status": "running",
            "checked_tree": tree, "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "log": str(STATE / (name + ".log")), "pid": os.getpid()}


def write_results(tree, results, checks):
    STATE.mkdir(parents=True, exist_ok=True)
    path = STATE / "results.json"
    temporary = STATE / f"results-{os.getpid()}.tmp"
    temporary.write_text(json.dumps({"tree": tree, "expected_checks": checks, "checks": results}, indent=2) + "\n")
    temporary.replace(path)


def check_report(results):
    lines = ["Local checks performed:"]
    for entry in results:
        status = local_check_status(entry)
        lines.append(f"- {entry['name']}: {status}; command: {shlex.join(entry['command'])}; log: {entry['log']}")
    lines.append(f"Results: {STATE / 'results.json'}")
    lines.extend(mise_setup_guidance(results))
    return "\n".join(lines)


def mise_setup_guidance(results):
    """Request agent setup when a check lacks a selected Mise tool."""
    missing = sorted({tool for entry in results for tool in entry.get("missing_mise_tools", [])})
    if not missing:
        return []
    return [f"Mise tools are missing: {', '.join(missing)}. Run mise install yourself at the repository root. "
            "Then run mise doctor project and python3 .claude/checks.py verify-head. "
            "Do not ask the user to run setup. If the user forbids installation or setup fails, "
            "report the cause and keep affected checks skipped. Local checks do not block a push."]


def sonar_target():
    connected_mode = ROOT / ".sonarlint" / "connectedMode.json"
    if connected_mode.exists():
        data = json.loads(connected_mode.read_text())
        return data["sonarQubeUri"], data["projectKey"]
    return CONFIG["sonar_server"], CONFIG["sonar_project"]


def sonar_environment(server):
    token = os.environ.get("SONARQUBE_NEXT_TOKEN") if server == "https://next.sonarqube.com/sonarqube" else None
    if not token:
        return None
    return {**os.environ, "SONARQUBE_CLI_SERVER": server, "SONARQUBE_CLI_TOKEN": token}


def sonar_connection_matches(env):
    server, _ = sonar_target()
    connection = run(["sonar", "auth", "status", "--format", "json"], env=env)
    try:
        active = json.loads(connection.stdout)
    except json.JSONDecodeError:
        return False
    return connection.returncode == 0 and active.get("status") == "connected" and active.get("server") == server


def mise_environment():
    """Use the committed project tasks without inherited config or skip overrides."""
    return {**os.environ, "MISE_DEFAULT_CONFIG_FILENAME": "mise.toml", "MISE_ENV": "",
            "MISE_OVERRIDE_CONFIG_FILENAMES": "", "MISE_TASK_SKIP": "", "MISE_TASK_SKIP_DEPENDS": "false",
            "MISE_YES": "1", "MISE_AUTO_INSTALL": "false", "MISE_TASK_RUN_AUTO_INSTALL": "false"}


def skipped_check(name, command, tree, log, tools):
    reason = "tools unavailable: " + ", ".join(tools)
    log.write_text(reason + "\n")
    entry = record(name, command, subprocess.CompletedProcess(command, None, reason), tree, log)
    entry.update(status="skipped", reason=reason)
    return entry, ""


def probe_required_tools(command, required_tools, *, cwd, env, log):
    """Return missing tools and any failed Mise probe."""
    required_tools = required_tools or {}
    missing = [tool for tool in [command[0], *required_tools.get("commands", [])]
               if shutil.which(tool, path=(env or os.environ).get("PATH")) is None]
    mise_tools = required_tools.get("mise", [])
    if missing or command[0] != "mise" or not mise_tools:
        return missing, None
    probe = run(["mise", "ls", "--current", "--json"], cwd=cwd, env=env, log=log)
    if probe.returncode:
        return [], probe
    selected = json.loads(probe.stdout)
    missing = [tool for tool in mise_tools
               if not selected.get(tool) or not all(version["installed"] for version in selected[tool])]
    return missing, None


def execute_check(name, command, tree, *, cwd=ROOT, required_tools=None):
    STATE.mkdir(parents=True, exist_ok=True)
    log = STATE / (name + ".log")
    if command[0] == "sonar":
        server, project = sonar_target()
        command = [project if item == "{project}" else item for item in command]
        env = sonar_environment(server)
    elif command[0] == "mise":
        env = mise_environment()
    else:
        env = None
    missing, failed_probe = probe_required_tools(command, required_tools, cwd=cwd, env=env, log=log)
    if failed_probe is not None:
        return record(name, command, failed_probe, tree, log), f"Cannot inspect Mise tools; inspect {log}."
    if missing:
        entry, failure = skipped_check(name, command, tree, log, missing)
        mise_tools = [tool for tool in missing if tool in (required_tools or {}).get("mise", [])]
        if command[0] == "mise" and mise_tools:
            entry["missing_mise_tools"] = mise_tools
        return entry, failure
    if command[0] == "sonar" and not sonar_connection_matches(env):
        reason = f"{name} unavailable: authenticate the CLI for {server} and retry."
        result = subprocess.CompletedProcess(command, 125, reason)
        log.write_text(reason + "\n")
        return record(name, command, result, tree, log), reason
    result = run(command, cwd=cwd, log=log, env=env)
    failure = f"{name} failed (exit {result.returncode}); inspect {log}."
    return record(name, command, result, tree, log), failure if result.returncode else ""


def run_local_checks(tree, checks, *, cwd=ROOT, required_tools=None):
    results = []
    failures = []
    write_results(tree, results, checks)
    for name, command in checks.items():
        results.append(running_record(name, command, tree))
        write_results(tree, results, checks)
        try:
            entry, failure = execute_check(name, command, tree, cwd=cwd,
                                           required_tools=(required_tools or {}).get(name))
        except (KeyError, OSError, RuntimeError, TypeError, ValueError) as error:
            log = STATE / (name + ".log")
            failure = f"{name} unavailable: {error}; inspect {log}."
            log.write_text(failure + "\n")
            entry = record(name, command, subprocess.CompletedProcess(command, 1, failure), tree, log)
        results[-1] = entry
        write_results(tree, results, checks)
        if failure:
            failures.append(failure)
    return not failures, "\n".join([check_report(results), *failures])


def verify_head():
    sha = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    with tempfile.TemporaryDirectory(prefix="claude-head-checks-") as directory:
        checkout = Path(directory) / "checkout"
        added = run(["git", "worktree", "add", "--detach", str(checkout), sha])
        if added.returncode:
            return False, f"Could not create an isolated checkout for {sha}: {added.stdout.strip()}"
        try:
            committed_config = json.loads((checkout / ".claude" / "checks.json").read_text())
            passed, message = run_local_checks(tree, committed_config["local_checks"], cwd=checkout,
                                               required_tools=committed_config.get("local_check_tools"))
            return passed, message + f"\nChecked HEAD {sha}."
        finally:
            removed = run(["git", "worktree", "remove", "--force", str(checkout)])
            if removed.returncode:
                raise RuntimeError(f"Could not remove isolated checkout {checkout}: {removed.stdout.strip()}")


def git_push_tokens(tokens):
    """Recognize Git push after command wrappers and environment assignments."""
    position = 0
    while position < len(tokens) and (tokens[position] in ("env", "command", "exec")
                                     or re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*=.*", tokens[position])):
        position += 1
    if position + 1 < len(tokens) and Path(tokens[position]).name == "mise" and tokens[position + 1] in ("exec", "x"):
        try:
            separator = tokens.index("--", position + 2)
        except ValueError:
            return False
        return git_push_tokens(tokens[separator + 1:])
    if position == len(tokens) or Path(tokens[position]).name != "git":
        return False
    position += 1
    while position < len(tokens):
        option = tokens[position]
        if option in ("-c", "-C", "--git-dir", "--work-tree", "--namespace", "--config-env"):
            position += 2
        elif option.startswith("-"):
            position += 1
        else:
            return option == "push"
    return False


def is_push(command):
    """Recognize a push in a standalone or compound shell command."""
    separators = ";&|()\n"
    lexer = shlex.shlex(command, posix=True, punctuation_chars=separators)
    lexer.whitespace = " \t\r"
    lexer.whitespace_split = True
    try:
        words = list(lexer)
    except ValueError:
        return False
    segment = []
    for word in words:
        if word and all(character in separators for character in word):
            if git_push_tokens(segment):
                return True
            segment = []
        else:
            segment.append(word)
    return git_push_tokens(segment)


def post_tool_use(payload):
    """Check the committed HEAD after a shell command that includes a push."""
    command = payload.get("tool_input", {}).get("command", "")
    if not is_push(command):
        return True, ""
    try:
        _, message = verify_head()
    except Exception as error:
        message = f"Local checks unavailable: {error}."
    return True, message + "\n" + PUSH_REPORT_GUIDANCE


def check_snapshot(snapshot, sha):
    return pr_report(snapshot, sha)


def process_active(pid):
    if not isinstance(pid, int):
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def local_check_status(entry):
    if entry is None:
        return "pending (not run)"
    if entry.get("status") == "running":
        return "running" if process_active(entry.get("pid")) else "interrupted"
    if entry.get("status") == "skipped":
        return f"skipped ({entry['reason']})"
    if entry.get("exit_status") == 0:
        return "passed"
    if entry.get("exit_status") is None:
        return "pending (no result)"
    return f"failed (exit {entry['exit_status']})"


def local_detail(entry, command):
    detail = f"; command: {shlex.join(entry.get('command', command) if entry else command)}"
    return detail + (f"; log: {entry['log']}" if entry and entry.get("log") else "")


def append_local_item(items, name, status, current, recorded_plan):
    if items is not None:
        items.append(("local", name, status if current and recorded_plan else "failed (stale or missing results)"))


def local_report(local, tree, items=None):
    current = local is not None and local.get("tree") == tree
    entries = {entry.get("name"): entry for entry in local.get("checks", [])} if current else {}
    expected = local.get("expected_checks") if current else None
    recorded_plan = isinstance(expected, dict)
    expected = expected if recorded_plan else CONFIG["local_checks"]
    lines = ["Local checks:"]
    complete = current and recorded_plan and [entry.get("name") for entry in local["checks"]] == list(expected)
    for name, command in expected.items():
        entry = entries.get(name)
        status = local_check_status(entry)
        complete &= status == "passed"
        append_local_item(items, name, status, current, recorded_plan)
        lines.append(f"- {name}: {status}{local_detail(entry, command)}")
    if not current:
        reason = "Saved results are for another tree" if local else "No saved results"
        lines.append(f"{reason}; run python3 .claude/checks.py verify-head for this commit.")
    elif not recorded_plan:
        lines.append("Saved results lack the committed check list; run python3 .claude/checks.py verify-head.")
    lines.append(f"Results: {STATE / 'results.json'}")
    lines.extend(mise_setup_guidance(entries.values()))
    lines.append(PUSH_REPORT_GUIDANCE)
    return complete, "\n".join(lines)


def job_status(job, sha):
    if job.get("head_sha", sha) != sha:
        return "pending (different commit)"
    conclusion = job.get("conclusion")
    if conclusion == "success":
        return "passed"
    if conclusion:
        return f"failed ({conclusion})"
    if job.get("status") == "in_progress":
        return "running"
    return f"pending ({job.get('status') or 'not started'})"


def review_status(decision):
    if decision == "APPROVED":
        return "passed"
    if decision == "CHANGES_REQUESTED":
        return "failed (changes requested)"
    return "pending (approval required)"


def pr_report(snapshot, sha, error=None, items=None):
    items = [] if items is None else items
    lines = ["PR checks:"]
    if error or snapshot.get("head_sha") != sha:
        reason = error or "snapshot is for a different commit"
        lines.append(f"PR status unavailable for HEAD {sha}: {reason}")
        lines.append("- review: pending (status unavailable)")
        lines.extend(f"- {required.replace(chr(92), '')}: pending (status unavailable)"
                     for required in CONFIG["required_pr_jobs"])
        return False, "\n".join(lines)
    review = review_status(snapshot.get("review_decision"))
    items.append(("review", "review", review))
    lines.append(f"- review: {review}")
    complete = review == "passed"
    jobs = snapshot.get("jobs", [])
    for required in CONFIG["required_pr_jobs"]:
        matches = [job for job in jobs if re.fullmatch(required, job.get("name", ""))]
        if not matches:
            lines.append(f"- {required.replace(chr(92), '')}: pending (job not reported)")
            items.append(("job", required.replace(chr(92), ""), "pending (job not reported)"))
            complete = False
        for job in matches:
            status = job_status(job, sha)
            url = f"; {job['url']}" if job.get("url") else ""
            lines.append(f"- {job['name']}: {status}{url}")
            items.append(("job", job["name"], status))
            complete &= status == "passed"
    return complete, "\n".join(lines)


def run_matches_head(workflow_run, sha, pr_number):
    return any(pr.get("number") == pr_number and
               (workflow_run.get("head_sha") == sha or pr.get("head", {}).get("sha") == sha)
               for pr in workflow_run.get("pull_requests", []))


def fetch_snapshot(sha):
    pr = run(["gh", "pr", "view", "--json", "number,headRefOid,reviewDecision"])
    if pr.returncode:
        raise RuntimeError("PR unavailable; push the commit and open a PR before finalization.")
    data = json.loads(pr.stdout)
    if data["headRefOid"] != sha:
        raise RuntimeError("PR head differs from local HEAD; push the current commit first.")
    repo = run(["gh", "repo", "view", "--json", "nameWithOwner"])
    if repo.returncode:
        raise RuntimeError("Cannot identify GitHub repository; retry gh authentication.")
    owner = json.loads(repo.stdout)["nameWithOwner"]
    runs = run(["gh", "api", f"repos/{owner}/actions/runs?head_sha={sha}&event=pull_request&per_page=30"])
    if runs.returncode:
        raise RuntimeError("Cannot fetch PR workflow runs; retry GitHub access.")
    exact = [item for item in json.loads(runs.stdout)["workflow_runs"]
             if run_matches_head(item, sha, data["number"]) and item["name"] == CONFIG["workflow"]]
    if not exact:
        return {"head_sha": sha, "review_decision": data["reviewDecision"], "jobs": []}
    latest = max(exact, key=lambda item: (item["run_number"], item["run_attempt"]))
    response = run(["gh", "api", f"repos/{owner}/actions/runs/{latest['id']}/jobs?per_page=100"])
    if response.returncode:
        raise RuntimeError("Cannot fetch PR jobs; retry GitHub access.")
    return {"head_sha": sha, "review_decision": data["reviewDecision"],
            "jobs": [{"name": job["name"], "status": job.get("status"),
                      "conclusion": job["conclusion"], "head_sha": sha, "url": job.get("html_url")}
                     for job in json.loads(response.stdout)["jobs"]]}


def finalize(snapshot=None):
    state = collect_status(snapshot)
    return state["complete"], state["message"]


def collect_status(snapshot=None):
    sha = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    try:
        local = json.loads((STATE / "results.json").read_text())
    except (OSError, json.JSONDecodeError):
        local = None
    items = []
    local_complete, local_message = local_report(local, tree, items)
    snapshot_error = None
    if snapshot is None:
        try:
            snapshot = fetch_snapshot(sha)
        except (KeyError, OSError, RuntimeError, ValueError) as error:
            snapshot_error = str(error)
    pr_complete, pr_message = pr_report(snapshot, sha, snapshot_error, items)
    return {"sha": sha, "complete": local_complete and pr_complete, "pr_error": snapshot_error, "items": items,
            "message": f"HEAD {sha} (tree {tree})\n{local_message}\n{pr_message}"}


OVERRIDE_MARKER = "CI-OVERRIDE:"
WAITING_MARKER = "CI-WAITING:"
MAX_IDENTICAL_BLOCKS = 3


def classify(kind, status):
    """Map a status to the action it calls for."""
    if status == "passed":
        return "passed"
    if kind == "local":
        return "report"
    if kind == "review":
        return "human"
    if status.startswith(("failed", "interrupted")):
        return "fix"
    return "monitor"


def strip_reminders(text):
    return re.sub(r"<system-reminder>.*?</system-reminder>", "", text, flags=re.DOTALL)


def message_texts(entry):
    content = entry.get("message", {}).get("content", "")
    if isinstance(content, str):
        return [content]
    return [block.get("text", "") for block in content if isinstance(block, dict) and block.get("type") == "text"]


def transcript_lines(path_text):
    """Read a session transcript, accepting only .jsonl files under the Claude projects directory."""
    if not path_text:
        return []
    projects = (Path(os.environ.get("CLAUDE_CONFIG_DIR", Path.home() / ".claude")) / "projects").resolve()
    try:
        path = Path(path_text).resolve(strict=True)
        if path.suffix != ".jsonl" or not path.is_relative_to(projects):
            return []
        return path.read_text().splitlines()
    except OSError:
        return []


def read_transcript(path_text):
    """Return (user prompts typed this session, last assistant reply text)."""
    users, last_reply = [], ""
    lines = transcript_lines(path_text)
    for line in lines:
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("isMeta") or entry.get("isSidechain"):
            continue
        texts = [text for text in message_texts(entry) if text.strip()]
        if entry.get("type") == "user":
            users.extend(strip_reminders(text) for text in texts)
        elif entry.get("type") == "assistant" and texts:
            last_reply = "\n".join(texts)
    # Hook feedback may be recorded as user text; never let it authorize an override.
    users = [text for text in users if OVERRIDE_MARKER not in text and WAITING_MARKER not in text]
    return users, last_reply


def normalize(text):
    return " ".join(text.split()).casefold()


def override_quote(reply, users):
    """Return the quoted user words if the reply carries a valid CI-OVERRIDE marker."""
    for match in re.finditer(re.escape(OVERRIDE_MARKER) + r'\s*["\u201c](.+?)["\u201d]', reply):
        quote = normalize(match.group(1))
        if len(quote) >= 8 and any(quote in normalize(text) for text in users):
            return match.group(1)
    return None


def note_block(session, sha, digest):
    """Count consecutive identical blocks; return True once the loop guard should release the stop."""
    path = STATE / "stop-blocks.json"
    try:
        previous = json.loads(path.read_text())
    except (OSError, ValueError):
        previous = {}
    key = [session, sha, digest]
    count = previous.get("count", 0) + 1 if previous.get("key") == key else 1
    STATE.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"key": key, "count": count}))
    return count > MAX_IDENTICAL_BLOCKS


def stop_decision(payload, state, *, transcript=None):
    """Return (block, text). text is the block reason or the plain status report."""
    report = ("Complete: " if state["complete"] else "Incomplete: ") + state["message"]
    if state["complete"] or state["pr_error"]:
        # No PR for this exact HEAD: CI is not in play for this turn, so only report.
        return False, report
    actions = {}
    for kind, name, status in state["items"]:
        actions.setdefault(classify(kind, status), []).append(f"{name}: {status}")
    fix, monitor = actions.get("fix", []), actions.get("monitor", [])
    if not fix and not monitor:
        return False, report
    users, reply = read_transcript(payload.get("transcript_path")) if transcript is None else transcript
    reply = payload.get("last_assistant_message") or reply
    quote = override_quote(reply, users)
    if quote:
        return False, f"CI override accepted (user said: \"{quote}\").\n{report}"
    if not fix and WAITING_MARKER in reply:
        return False, f"CI still running; monitoring declared.\n{report}"
    digest = json.dumps([fix, monitor])
    if note_block(payload.get("session_id"), state["sha"], digest):
        return False, f"Loop guard: CI state unchanged after {MAX_IDENTICAL_BLOCKS} stop attempts.\n{report}"
    lines = [f"CI for HEAD {state['sha']} is not green. Before ending the turn, decide the next action."]
    if fix:
        lines += ["Failed (investigate logs, fix, commit, and push; rerun a job with gh only if it is clearly flaky):"]
        lines += [f"  - {item}" for item in fix]
    if monitor:
        lines += ["Still running or not reported yet (monitor until they finish):"] + [f"  - {item}" for item in monitor]
        lines.append(f"If nothing has failed and you have set up monitoring that will re-invoke you (Monitor tool, "
                     f"background gh run watch, ScheduleWakeup), end your reply with a line "
                     f"'{WAITING_MARKER} <what you are monitoring>'.")
    lines.append(f"Only if the user explicitly said in this session that CI can be ignored, end your reply with "
                 f"'{OVERRIDE_MARKER} \"<their exact words>\"'; the quote is checked against the user's messages.")
    lines += ["", state["message"]]
    return True, "\n".join(lines)


def stop(payload, *, transcript=None):
    try:
        state = collect_status()
    except (KeyError, OSError, RuntimeError, ValueError) as error:
        return False, f"Incomplete: Check unavailable: {error}. Retry after fixing the reported cause."
    return stop_decision(payload, state, transcript=transcript)


def load_snapshot(path_text):
    path = Path(path_text).resolve(strict=True)
    if not path.is_relative_to(ROOT):
        raise ValueError("Snapshot must be inside the repository")
    return json.loads(path.read_text())


def argument_parser():
    parser = argparse.ArgumentParser(
        description="Run repository checks for the committed HEAD and inspect PR validation.",
        epilog="Run 'checks.py COMMAND --help' for command-specific help.",
    )
    commands = parser.add_subparsers(dest="command", required=True, metavar="COMMAND")
    commands.add_parser("post-tool-use", help="report local checks after a Bash push command",
                        description="Read hook JSON from stdin and check HEAD after a command that includes a push.")
    commands.add_parser("verify-head", help="run required local checks in an isolated checkout of HEAD",
                        description="Run the required local checks against the committed HEAD and save their results.")
    finalize_command = commands.add_parser("finalize", help="check saved local results, PR jobs, and review",
                                          description="Check local results, required PR jobs, and review for HEAD.")
    finalize_command.add_argument("snapshot", nargs="?", metavar="SNAPSHOT.json",
                                  help="use a repository-local PR job snapshot instead of fetching GitHub")
    commands.add_parser("stop", help="Claude Code hook: block the stop while PR CI is failing or running",
                        description="Read Claude Code Stop hook JSON from stdin. Report status, and block the stop "
                                    "while the PR for HEAD has failing or running CI, unless the reply carries "
                                    "a valid CI-WAITING or CI-OVERRIDE marker.")
    return parser


def read_stop_payload():
    if sys.stdin.isatty():
        return {}
    try:
        payload = json.loads(sys.stdin.read() or "{}")
    except ValueError:
        return {}
    return payload if isinstance(payload, dict) else {}


def main(argv=None):
    args = argument_parser().parse_args(argv)
    payload = {}
    try:
        if args.command == "post-tool-use":
            data = json.load(sys.stdin)
            if not isinstance(data, dict):
                raise ValueError("Hook input must be a JSON object")
            payload = data
            passed, message = post_tool_use(payload)
        elif args.command == "verify-head":
            passed, message = verify_head()
        elif args.command == "finalize":
            snapshot = load_snapshot(args.snapshot) if args.snapshot else None
            passed, message = finalize(snapshot)
        else:
            block, text = stop(read_stop_payload())
            print(json.dumps({"decision": "block", "reason": text}) if block else text)
            return 0
    except Exception as error:
        passed, message = False, f"Check unavailable: {error}. Retry after fixing the reported cause."
    if args.command == "post-tool-use":
        if not passed:
            message = f"Local checks unavailable.\n{message}\n{PUSH_REPORT_GUIDANCE}"
        if message:
            event = "PostToolUseFailure" if payload.get("hook_event_name") == "PostToolUseFailure" else "PostToolUse"
            print(json.dumps({"hookSpecificOutput": {"hookEventName": event, "additionalContext": message}}))
        return 0
    if message:
        print(message, file=sys.stderr)
    return 0 if passed else 2


if __name__ == "__main__":
    sys.exit(main())
