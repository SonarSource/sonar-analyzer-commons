#!/usr/bin/env python3
"""Check repository source boundaries and identify affected modules."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
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


def whitespace_status(base, root=None):
    """Check added whitespace while preserving deliberate test-resource content."""
    return subprocess.run(["git", "diff", "--check", base, "--", ".",
                           ":(exclude,glob)**/src/test/resources/**"], cwd=root or ROOT).returncode


def checks(base="HEAD"):
    """Check source boundaries, changed whitespace, and added credentials."""
    errors = boundary_errors(ROOT, project_graph(ROOT, include_tests=False))
    for error in errors:
        print(error, file=sys.stderr)
    whitespace = whitespace_status(base)
    credentials = repository_checks.scan_changes(base)
    return int(bool(errors or whitespace or credentials))


def main():
    """Run independent source checks or print the modules affected by changes."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("check", help="Check source boundaries, whitespace, and added credentials")
    check.add_argument("--base", default="HEAD", help="Git base for committed and local changes")
    affected = commands.add_parser("affected", help="Print changed modules and reverse dependencies")
    affected.add_argument("base", help="Git base; local changes are included")
    args = parser.parse_args()
    if args.command == "check":
        return checks(args.base)
    print(json.dumps(affected_modules(project_graph(ROOT), changed_paths(args.base))))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.CalledProcessError, ET.ParseError) as error:
        print(f"Repository check failed: {error}", file=sys.stderr)
        sys.exit(1)
