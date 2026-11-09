"""Exercise source boundaries, change impact, and committed-file checks."""

import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
SPEC = importlib.util.spec_from_file_location("check_sources", Path(__file__).resolve().parents[1] / "check_sources.py")
source_checks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(source_checks)


class SourceChecksTest(unittest.TestCase):
    """Check repository rules with real source and Git fixtures."""

    def setUp(self):
        """Create an isolated directory without using the system temporary folder."""
        scratch = source_checks.ROOT / "target/tool-tests"
        scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def write(self, name, content):
        """Write one fixture file and return its path."""
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def repository(self):
        """Create a repository with one tracked source file."""
        self.write("source.txt", "original\n")
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Tool test", "-c",
                        "user.email=tool@example.invalid", "commit", "-qm", "Initial source"], check=True)

    def test_affected_includes_transitive_consumers(self):
        graph = {"core": set(), "consumer": {"core"}, "downstream": {"consumer"}, "other": set()}
        self.assertEqual(source_checks.affected_modules(graph, ["core/src/main/java/One.java"]), ["consumer", "core", "downstream"])

    def test_shared_build_change_selects_every_module(self):
        graph = {"core": set(), "other": set()}
        self.assertEqual(source_checks.affected_modules(graph, ["pom.xml"]), ["core", "other"])
        self.assertEqual(source_checks.affected_modules(graph, ["scripts/source_checks.py"]), ["core", "other"])

    def test_root_documentation_does_not_select_java_modules(self):
        self.assertEqual(source_checks.affected_modules({"core": set()}, ["README.md"]), [])

    def test_reads_test_dependencies_for_change_impact(self):
        self.write("pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>a</module><module>b</module></modules></project>')
        self.write("a/pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>lib-a</artifactId></project>')
        self.write("b/pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>lib-b</artifactId><dependencies><dependency><artifactId>lib-a</artifactId><scope>test</scope></dependency></dependencies></project>')
        self.assertEqual(source_checks.project_graph(self.root), {"a": set(), "b": {"a"}})
        self.assertEqual(source_checks.project_graph(self.root, include_tests=False), {"a": set(), "b": set()})

    def test_rejects_undeclared_cross_module_import(self):
        self.write("a/src/main/java/p/One.java", "package p;\npublic class One {}\n")
        self.write("b/src/main/java/q/Two.java", "package q;\nimport p.One;\nclass Two {}\n")
        errors = source_checks.boundary_errors(self.root, {"a": set(), "b": set()})
        self.assertTrue(any("declared reactor dependency" in error for error in errors))
        self.assertEqual(source_checks.boundary_errors(self.root, {"a": set(), "b": {"a"}}), [])

    def test_rejects_package_directory_mismatch(self):
        self.write("a/src/main/java/wrong/One.java", "package p;\npublic class One {}\n")
        self.assertTrue(any("package does not match" in error for error in source_checks.boundary_errors(self.root, {"a": set()})))

    def test_rejects_dependency_cycle(self):
        self.assertTrue(any("cycle" in error for error in source_checks.boundary_errors(self.root, {"a": {"b"}, "b": {"a"}})))

    def test_change_selection_includes_local_and_untracked_files(self):
        self.repository()
        self.write("source.txt", "changed\n")
        self.write("new.txt", "new\n")
        self.assertEqual(source_checks.changed_paths("HEAD", self.root), ["new.txt", "source.txt"])

    def test_committed_credentials_are_checked_against_explicit_base_and_redacted(self):
        self.repository()
        token = "ghp_" + "a" * 36
        self.write("source.txt", token + "\n")
        subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Tool test", "-c",
                        "user.email=tool@example.invalid", "commit", "-qm", "Add sample"], check=True)
        output = io.StringIO()
        with patch.object(source_checks.repository_checks, "ROOT", self.root), contextlib.redirect_stderr(output):
            self.assertEqual(source_checks.repository_checks.scan_changes("HEAD~1"), 1)
        self.assertNotIn(token, output.getvalue())
        self.assertIn("Values are hidden", output.getvalue())

    def test_committed_source_whitespace_fails_but_fixture_whitespace_is_allowed(self):
        self.repository()
        for path, expected in (("core/src/main/java/One.java", 2), ("core/src/test/resources/fixture.java", 0)):
            self.write(path, "deliberate trailing space \n")
            subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Tool test", "-c",
                            "user.email=tool@example.invalid", "commit", "-qm", "Add sample"], check=True)
            self.assertEqual(source_checks.whitespace_status("HEAD~1", self.root), expected)


if __name__ == "__main__":
    unittest.main()
