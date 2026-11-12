#!/usr/bin/env python3
"""Deterministic Codex push and PR gates. See HOOKS.md."""

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((Path(__file__).parent / "checks.json").read_text())
CHECKOUT_ID = hashlib.sha256(os.fsencode(ROOT)).hexdigest()[:16]
STATE = (Path(os.environ.get("CODEX_CHECK_STATE", Path(tempfile.gettempdir()) / "codex-repo-checks"))
         / CONFIG["repository"] / CHECKOUT_ID)


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
        status = "passed" if entry["exit_status"] == 0 else f"failed (exit {entry['exit_status']})"
        lines.append(f"- {entry['name']}: {status}; command: {shlex.join(entry['command'])}; log: {entry['log']}")
    lines.append(f"Results: {STATE / 'results.json'}")
    return "\n".join(lines)


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


def execute_check(name, command, tree, *, cwd=ROOT, retry="retry the push"):
    STATE.mkdir(parents=True, exist_ok=True)
    log = STATE / (name + ".log")
    if command[0] == "sonar":
        server, project = sonar_target()
        command = [project if item == "{project}" else item for item in command]
        env = sonar_environment(server)
    else:
        env = None
    if command[0] == "sonar" and not sonar_connection_matches(env):
        reason = f"{name} unavailable: authenticate the CLI for {server} and retry."
        result = subprocess.CompletedProcess(command, 125, reason)
        log.write_text(reason + "\n")
        return record(name, command, result, tree, log), reason
    result = run(command, cwd=cwd, log=log, env=env)
    failure = f"{name} failed (exit {result.returncode}); inspect {log}, fix it, and {retry}."
    return record(name, command, result, tree, log), failure if result.returncode else ""


def run_local_checks(tree, checks, *, cwd=ROOT, retry="retry the push"):
    results = []
    write_results(tree, results, checks)
    for name, command in checks.items():
        results.append(running_record(name, command, tree))
        write_results(tree, results, checks)
        entry, failure = execute_check(name, command, tree, cwd=cwd, retry=retry)
        results[-1] = entry
        write_results(tree, results, checks)
        if failure:
            return False, check_report(results) + "\n" + failure
    return True, check_report(results)


def verify_head():
    sha = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    with tempfile.TemporaryDirectory(prefix="codex-head-checks-") as directory:
        checkout = Path(directory) / "checkout"
        added = run(["git", "worktree", "add", "--detach", str(checkout), sha])
        if added.returncode:
            return False, f"Could not create an isolated checkout for {sha}: {added.stdout.strip()}"
        try:
            committed_config = json.loads((checkout / ".codex" / "checks.json").read_text())
            passed, message = run_local_checks(tree, committed_config["local_checks"], cwd=checkout,
                                               retry="rerun python3 .codex/checks.py verify-head")
            return passed, message + (f"\nRequired local checks passed for HEAD {sha}." if passed else "")
        finally:
            removed = run(["git", "worktree", "remove", "--force", str(checkout)])
            if removed.returncode:
                raise RuntimeError(f"Could not remove isolated checkout {checkout}: {removed.stdout.strip()}")


def git_push_tokens(tokens):
    for index, token in enumerate(tokens):
        if Path(token).name != "git":
            continue
        position = index + 1
        while position < len(tokens):
            option = tokens[position]
            if option in ("-c", "-C", "--git-dir", "--work-tree", "--namespace", "--config-env"):
                position += 2
            elif option.startswith("-"):
                position += 1
            else:
                if option == "push":
                    return True
                break
    return False


def push_words(command):
    """Return the push arguments for a standalone push or a push piped to tail."""
    if "\n" in command:
        return None
    lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|<>")
    lexer.whitespace_split = True
    try:
        tokens = list(lexer)
    except ValueError:
        return None
    if tokens[:2] != ["git", "push"]:
        return None
    if "|" in tokens:
        if tokens.count("|") != 1:
            return None
        index = tokens.index("|")
        push, output = tokens[:index], tokens[index + 1:]
        if push[-3:] == ["2", ">&", "1"]:
            push = push[:-3]
        if not (len(output) == 2 and output[0] == "tail" and re.fullmatch(r"-\d+", output[1])
                or len(output) == 3 and output[:2] == ["tail", "-n"] and output[2].isdigit()):
            return None
    else:
        push = tokens
    return push if not any(token in (";", "&", "&&", "|", "||", ">", ">>", "<", ">&") for token in push) else None


def is_push(command):
    try:
        words = shlex.split(command)
    except ValueError:
        return False, False
    if not words:
        return False, False
    if Path(words[0]).name in ("sh", "bash", "zsh"):
        for index, word in enumerate(words[1:], 1):
            if word.startswith("-") and "c" in word[1:] and index + 1 < len(words):
                attempted, _ = is_push(words[index + 1])
                if attempted:
                    return True, False
                break
    standalone = push_words(command) is not None
    for segment in re.split(r"[;&|\n]+", command):
        try:
            tokens = shlex.split(segment)
        except ValueError:
            continue
        if git_push_tokens(tokens):
            return True, standalone
    return False, False


def safe_push_args(words):
    args = [word for word in words[2:] if word not in ("-u", "--set-upstream")]
    if not args:
        return True
    if len(args) == 2 and not args[0].startswith("-"):
        return args[1] == "HEAD" or args[1] == git("symbolic-ref", "--quiet", "--short", "HEAD")
    return False


def pre_tool_use(payload):
    command = payload.get("tool_input", {}).get("command", "")
    attempted, standalone = is_push(command)
    if not attempted:
        return True, ""
    if not standalone:
        return False, "Run git push in a separate Bash tool call, then retry."
    words = push_words(command)
    if not safe_push_args(words):
        return False, "Push the current branch with standalone git push or git push <remote> HEAD; other push options are unsupported."
    return verify_head()


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
        lines.append(f"{reason}; run python3 .codex/checks.py verify-head for this commit.")
    elif not recorded_plan:
        lines.append("Saved results lack the committed check list; run python3 .codex/checks.py verify-head.")
    lines.append(f"Results: {STATE / 'results.json'}")
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


WAITING_MARKER = "CI-WAITING:"
MAX_IDENTICAL_BLOCKS = 3


def classify(kind, status):
    """Map a status to the action it calls for: passed, fix, monitor, or human."""
    if status == "passed":
        return "passed"
    if kind == "review":
        return "human"
    if status.startswith(("failed", "interrupted")) or (kind == "local" and status != "running"):
        return "fix"
    return "monitor"


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


def stop_decision(payload, state):
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
    reply = payload.get("last_assistant_message") or ""
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
    lines += ["", state["message"]]
    return True, "\n".join(lines)


def stop(payload):
    try:
        state = collect_status()
    except (KeyError, OSError, RuntimeError, ValueError) as error:
        return False, f"Incomplete: Check unavailable: {error}. Retry after fixing the reported cause."
    return stop_decision(payload, state)


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
    commands.add_parser("pre-tool-use", help="Codex hook: check a Bash push attempt from JSON on stdin",
                        description="Read Codex hook JSON from stdin and validate a Bash push attempt.")
    commands.add_parser("verify-head", help="run required local checks in an isolated checkout of HEAD",
                        description="Run the required local checks against the committed HEAD and save their results.")
    finalize_command = commands.add_parser("finalize", help="check saved local results, PR jobs, and review",
                                          description="Check local results, required PR jobs, and review for HEAD.")
    finalize_command.add_argument("snapshot", nargs="?", metavar="SNAPSHOT.json",
                                  help="use a repository-local PR job snapshot instead of fetching GitHub")
    commands.add_parser("stop", help="Codex hook: block the stop while PR CI is failing or running",
                        description="Read Codex Stop hook JSON from stdin. Report status, and block the stop "
                                    "while the PR for HEAD has failing or running CI, unless the reply carries "
                                    "a CI-WAITING marker.")
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
    try:
        if args.command == "pre-tool-use":
            passed, message = pre_tool_use(json.load(sys.stdin))
        elif args.command == "verify-head":
            passed, message = verify_head()
        elif args.command == "finalize":
            snapshot = load_snapshot(args.snapshot) if args.snapshot else None
            passed, message = finalize(snapshot)
        else:
            block, text = stop(read_stop_payload())
            print(json.dumps({"decision": "block", "reason": text} if block else {"systemMessage": text}))
            return 0
    except (KeyError, OSError, RuntimeError, ValueError) as error:
        passed, message = False, f"Check unavailable: {error}. Retry after fixing the reported cause."
    if args.command == "pre-tool-use" and passed and message:
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": message}}))
        return 0
    if message:
        print(message, file=sys.stderr)
    return 0 if passed else 2


if __name__ == "__main__":
    sys.exit(main())
