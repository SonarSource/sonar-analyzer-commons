#!/usr/bin/env python3
"""Run local repository checks and Maven commands without publishing artifacts."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import repository_checks

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def project_graph(root, include_tests=True):
    """Read reactor modules and their local dependencies from the POM files."""
    parent = ET.parse(root / "pom.xml").getroot()
    modules = [node.text for node in parent.findall("m:modules/m:module", NS)]
    projects = {module: ET.parse(root / module / "pom.xml").getroot() for module in modules}
    artifacts = {pom.findtext("m:artifactId", namespaces=NS): module for module, pom in projects.items()}
    graph = {}
    for module, pom in projects.items():
        graph[module] = {
            artifacts[artifact]
            for dependency in pom.findall("m:dependencies/m:dependency", NS)
            if (artifact := dependency.findtext("m:artifactId", namespaces=NS)) in artifacts
            and (include_tests or dependency.findtext("m:scope", namespaces=NS) != "test")
        }
    return graph


def affected_modules(graph, paths):
    """Select changed modules and all reactor modules that depend on them."""
    shared = {"pom.xml", "mise.toml", ".editorconfig"}
    if any(path in shared or path.startswith(("scripts/", ".github/")) for path in paths):
        return sorted(graph)
    affected = {path.split("/", 1)[0] for path in paths} & graph.keys()
    while True:
        expanded = affected | {module for module, dependencies in graph.items() if dependencies & affected}
        if expanded == affected:
            return sorted(affected)
        affected = expanded


def git(*arguments, root=ROOT):
    """Read Git output and retain file names without shell interpretation."""
    return subprocess.check_output(["git", "-C", str(root), *arguments])


def changed_paths(base, root=ROOT):
    """Return committed, staged, unstaged, and untracked changes from a base ref."""
    paths = git("diff", "--name-only", "-z", base, "--", root=root)
    paths += git("ls-files", "--others", "--exclude-standard", "-z", root=root)
    return sorted(set(os.fsdecode(path) for path in paths.split(b"\0") if path))


def tracked_state(root=None):
    """Hash tracked files to detect source changes made by a build."""
    root = root or ROOT
    state = {}
    for name in git("ls-files", "-z", root=root).split(b"\0"):
        if name:
            path = root / os.fsdecode(name)
            state[os.fsdecode(name)] = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
    return state


def boundary_errors(root, graph):
    """Check reactor cycles, Java package paths, and declared source dependencies."""
    errors = []
    owners = {}
    sources = []
    for module in graph:
        for path in (root / module / "src/main/java").rglob("*.java"):
            content = path.read_text(encoding="utf-8")
            package = re.search(r"^package ([\w.]+);", content, re.MULTILINE)
            if package:
                package = package.group(1)
                relative = path.relative_to(root / module / "src/main/java")
                if relative.parent.as_posix() != package.replace(".", "/"):
                    errors.append(f"{path.relative_to(root)}: package does not match its directory")
                owners[package + "." + path.stem] = module
            sources.append((module, path, content))
    for module, path, content in sources:
        for imported in re.findall(r"^import (?:static )?([\w.]+)(?:\.\*)?;", content, re.MULTILINE):
            owner = next((owners[name] for name in owners if imported == name or imported.startswith(name + ".")), None)
            if owner and owner != module and owner not in graph[module]:
                errors.append(f"{path.relative_to(root)}: import from {owner} requires a declared reactor dependency")
    def visit(module, stack):
        """Reject cycles in the local dependency graph."""
        if module in stack:
            errors.append("Reactor dependency cycle: " + " -> ".join([*stack, module]))
            return
        for dependency in graph[module]:
            visit(dependency, [*stack, module])
    for module in graph:
        visit(module, [])
    return sorted(set(errors))


def maven_prefix():
    """Use the repository tool versions when mise is available."""
    return ["mise", "exec", "--", "mvn"] if shutil.which("mise") else ["mvn"]


def doctor(resolve=False):
    """Check the actual Maven JVM and optionally resolve the parent POM."""
    try:
        version = subprocess.run([*maven_prefix(), "-version"], cwd=ROOT, text=True,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        print("Cannot start Maven. Run `mise trust` and `mise install`, then run this command again.", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError):
            print(error.stdout, file=sys.stderr)
        return 1
    print(version, end="")
    maven = re.search(r"Apache Maven (\d+)\.(\d+)", version)
    java = re.search(r"Java version: (\d+)[.,]", version)
    if not maven or tuple(map(int, maven.groups())) < (3, 9) or not java or int(java.group(1)) < 21:
        print("Use Maven 3.9 or later and JDK 21 or later. Maven can use a different JVM from `java` on PATH.", file=sys.stderr)
        return 1
    if resolve:
        return run_maven(["validate"], "doctor")
    print("Toolchain is ready. `doctor --resolve` also checks Maven parent resolution.")
    return 0


def report_state(module):
    """Identify current Surefire XML reports without accepting stale results."""
    return {path: (path.stat().st_mtime_ns, path.stat().st_size)
            for path in (ROOT / module / "target/surefire-reports").glob("TEST-*.xml")}


def completed_tests(module, before):
    """Count executed tests from reports written by the current Maven command."""
    count = 0
    for path, stamp in report_state(module).items():
        if before.get(path) != stamp:
            suite = ET.parse(path).getroot()
            count += int(suite.get("tests", "0")) - int(suite.get("skipped", "0"))
    return count


def artifact_hashes(module):
    """Hash packaged library and configuration outputs for a repeat build."""
    target = ROOT / module / "target"
    paths = [path for pattern in ("*.jar", "*.zip", "*.tar.gz", "generated-resources/*.json") for path in target.glob(pattern)]
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(paths) if not path.name.startswith("original-")}


def reproduce(module):
    """Compare two clean packages with commit-based archive and manifest times."""
    timestamp = git("show", "-s", "--format=%ct", "HEAD").decode().strip()
    manifest_time = git("show", "-s", "--format=%cI", "HEAD").decode().strip()
    options = ["-pl", module, "-am", "-DskipTests", "-Dproject.build.outputTimestamp=" + timestamp,
               "-Dtimestamp=" + manifest_time, "clean", "package"]
    outputs = []
    for iteration in range(2):
        status = run_maven(options, "reproduce-" + str(iteration + 1))
        if status:
            return status
        outputs.append(artifact_hashes(module))
    matched = bool(outputs[0]) and outputs[0] == outputs[1]
    report = ROOT / "target/agent/reproduce.json"
    report.write_text(json.dumps({"module": module, "matched": matched, "build_hashes": outputs}, indent=2) + "\n", encoding="utf-8")
    print("Repeat package byte comparison: " + ("equal" if matched else "different or no artifacts"))
    return int(not matched)


def run_maven(arguments, name, selected_module=None):
    """Run Maven, preserve its exit status, and save a small local result record."""
    before = tracked_state()
    reports = report_state(selected_module) if selected_module else {}
    started = time.monotonic()
    command = [*maven_prefix(), "-B", "-ntp", "-C", *arguments]
    print("Running: " + " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    after = tracked_state()
    drift = sorted(path for path in before if before[path] != after.get(path))
    status = result.returncode
    test_count = completed_tests(selected_module, reports) if selected_module else None
    if status == 0 and selected_module and not test_count:
        print(f"No tests executed in {selected_module}. Check the class or method selector.", file=sys.stderr)
        status = 1
    if drift:
        print("Build changed tracked files: " + ", ".join(drift), file=sys.stderr)
        status = status or 1
    report = ROOT / "target/agent" / (name + ".json")
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({"command": command, "exit_code": status,
                                  "duration_seconds": round(time.monotonic() - started, 3),
                                  "selected_module_executed_tests": test_count,
                                  "tracked_file_drift": drift}, indent=2) + "\n", encoding="utf-8")
    if status:
        print("Read the first Maven ERROR and */target/surefire-reports/. For 401/403, check your private Maven settings; do not print credentials.", file=sys.stderr)
    print(f"Result: {report.relative_to(ROOT)} (exit {status})", flush=True)
    return status


def whitespace_status(base, root=None):
    """Check added whitespace while preserving deliberate test-resource content."""
    return subprocess.run(["git", "diff", "--check", base, "--", ".",
                           ":(exclude,glob)**/src/test/resources/**"], cwd=root or ROOT).returncode


def checks(base="HEAD"):
    """Run fast offline checks before a Maven build."""
    errors = boundary_errors(ROOT, project_graph(ROOT, include_tests=False))
    for error in errors:
        print(error, file=sys.stderr)
    whitespace = whitespace_status(base)
    credentials = repository_checks.scan_changes(base)
    tests = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-v"], cwd=ROOT).returncode
    return int(bool(errors or whitespace or credentials or tests))


def main():
    """Parse a bounded local command and reject unknown reactor modules."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    diagnostic = commands.add_parser("doctor", help="Check Maven and its JVM without reading credentials")
    diagnostic.add_argument("--resolve", action="store_true")
    check = commands.add_parser("check", help="Run offline tooling tests, whitespace, and module boundary checks")
    check.add_argument("--base", default="HEAD", help="Compare committed and local whitespace changes with this ref")
    affected = commands.add_parser("affected", help="Print changed modules and reverse dependencies")
    affected.add_argument("base", help="Git base ref; local changes are included")
    for command in ("test", "verify", "build", "repeat", "sbom", "reproduce", "quality"):
        sub = commands.add_parser(command)
        sub.add_argument("modules", nargs="*", help="Reactor directory names; omit for all modules")
        if command in ("test", "repeat"):
            sub.add_argument("--test", help="Surefire class or class#method selector")
    args = parser.parse_args()
    if args.command == "doctor":
        return doctor(args.resolve)
    if args.command == "check":
        return checks(args.base)
    graph = project_graph(ROOT)
    if args.command == "affected":
        print(json.dumps(affected_modules(graph, changed_paths(args.base))))
        return 0
    invalid = sorted(set(args.modules) - graph.keys())
    if invalid:
        parser.error("Unknown module(s): " + ", ".join(invalid) + ". Use: " + ", ".join(graph))
    if doctor():
        return 1
    if args.command == "reproduce":
        if len(args.modules) != 1:
            parser.error("reproduce requires exactly one module")
        return reproduce(args.modules[0])
    options = ["-pl", ",".join(args.modules), "-am"] if args.modules else []
    if getattr(args, "test", None):
        if len(args.modules) != 1:
            parser.error("--test requires exactly one module so test execution can be checked")
        options += ["-Dtest=" + args.test, "-Dsurefire.failIfNoSpecifiedTests=false"]
    if args.command in ("test", "repeat"):
        for iteration in range(2 if args.command == "repeat" else 1):
            status = run_maven([*options, "test"], args.command + "-" + str(iteration + 1),
                               args.modules[0] if getattr(args, "test", None) else None)
            if status:
                return status
        return 0
    if args.command == "verify" and checks():
        return 1
    goals = {"verify": ["-Pcoverage", "verify"], "build": ["install"],
             "quality": ["compile", "pmd:pmd", "pmd:cpd"],
             "sbom": ["org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom"]}
    return run_maven([*options, *goals[args.command]], args.command)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"Repository command failed: {error}", file=sys.stderr)
        sys.exit(1)
