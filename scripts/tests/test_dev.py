"""Exercise repository command selection, boundaries, and real process failures."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
SPEC = importlib.util.spec_from_file_location("dev", Path(__file__).resolve().parents[1] / "dev.py")
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)


class RepositoryCommandsTest(unittest.TestCase):
    """Check behavior with temporary repositories inside the build directory."""

    def setUp(self):
        """Create an isolated directory without using the system temporary folder."""
        scratch = dev.ROOT / "target/tool-tests"
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
        self.assertEqual(dev.affected_modules(graph, ["core/src/main/java/One.java"]), ["consumer", "core", "downstream"])

    def test_shared_build_change_selects_every_module(self):
        graph = {"core": set(), "other": set()}
        self.assertEqual(dev.affected_modules(graph, ["pom.xml"]), ["core", "other"])
        self.assertEqual(dev.affected_modules(graph, ["scripts/dev.py"]), ["core", "other"])

    def test_root_documentation_does_not_select_java_modules(self):
        self.assertEqual(dev.affected_modules({"core": set()}, ["README.md"]), [])

    def test_reads_test_dependencies_for_change_impact(self):
        self.write("pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>a</module><module>b</module></modules></project>')
        self.write("a/pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>lib-a</artifactId></project>')
        self.write("b/pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>lib-b</artifactId><dependencies><dependency><artifactId>lib-a</artifactId><scope>test</scope></dependency></dependencies></project>')
        self.assertEqual(dev.project_graph(self.root), {"a": set(), "b": {"a"}})
        self.assertEqual(dev.project_graph(self.root, include_tests=False), {"a": set(), "b": set()})

    def test_rejects_undeclared_cross_module_import(self):
        self.write("a/src/main/java/p/One.java", "package p;\npublic class One {}\n")
        self.write("b/src/main/java/q/Two.java", "package q;\nimport p.One;\nclass Two {}\n")
        errors = dev.boundary_errors(self.root, {"a": set(), "b": set()})
        self.assertTrue(any("declared reactor dependency" in error for error in errors))
        self.assertEqual(dev.boundary_errors(self.root, {"a": set(), "b": {"a"}}), [])

    def test_rejects_package_directory_mismatch(self):
        self.write("a/src/main/java/wrong/One.java", "package p;\npublic class One {}\n")
        self.assertTrue(any("package does not match" in error for error in dev.boundary_errors(self.root, {"a": set()})))

    def test_rejects_dependency_cycle(self):
        self.assertTrue(any("cycle" in error for error in dev.boundary_errors(self.root, {"a": {"b"}, "b": {"a"}})))

    def test_change_selection_includes_local_and_untracked_files(self):
        self.repository()
        self.write("source.txt", "changed\n")
        self.write("new.txt", "new\n")
        self.assertEqual(dev.changed_paths("HEAD", self.root), ["new.txt", "source.txt"])

    def test_real_process_failure_is_preserved_and_recorded(self):
        self.repository()
        process = self.write("fake_maven.py", "import sys\nsys.exit(7)\n")
        with patch.object(dev, "ROOT", self.root), patch.object(dev, "maven_prefix", return_value=[sys.executable, str(process)]):
            self.assertEqual(dev.run_maven(["test"], "failure"), 7)
        record = json.loads((self.root / "target/agent/failure.json").read_text())
        self.assertEqual(record["exit_code"], 7)
        self.assertIn("test", record["command"])

    def test_source_drift_fails_a_successful_build(self):
        self.repository()
        process = self.write("fake_maven.py", "from pathlib import Path\nPath('source.txt').write_text('changed')\n")
        with patch.object(dev, "ROOT", self.root), patch.object(dev, "maven_prefix", return_value=[sys.executable, str(process)]):
            self.assertEqual(dev.run_maven(["verify"], "drift"), 1)
        record = json.loads((self.root / "target/agent/drift.json").read_text())
        self.assertEqual(record["tracked_file_drift"], ["source.txt"])

    def test_unknown_module_is_rejected_before_running_maven(self):
        with patch.object(sys, "argv", ["dev.py", "test", "typo"]), patch.object(dev, "doctor") as doctor, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                dev.main()
        self.assertEqual(error.exception.code, 2)
        doctor.assert_not_called()

    def test_missing_class_and_method_do_not_accept_stale_reports(self):
        self.repository()
        self.write("core/target/surefire-reports/TEST-old.xml", '<testsuite tests="12" skipped="0"/>')
        process = self.write("fake_maven.py", "pass\n")
        with patch.object(dev, "ROOT", self.root), patch.object(dev, "maven_prefix", return_value=[sys.executable, str(process)]):
            for selector in ("MissingTest", "ExistingTest#missing_method"):
                self.assertEqual(dev.run_maven(["-Dtest=" + selector, "test"], "missing", "core"), 1)

    def test_selected_module_requires_executed_tests(self):
        self.repository()
        process = self.write("fake_maven.py", "from pathlib import Path\np=Path('core/target/surefire-reports/TEST-new.xml')\np.parent.mkdir(parents=True, exist_ok=True)\np.write_text('<testsuite tests=\"3\" skipped=\"1\"/>')\n")
        with patch.object(dev, "ROOT", self.root), patch.object(dev, "maven_prefix", return_value=[sys.executable, str(process)]):
            self.assertEqual(dev.run_maven(["test"], "selected", "core"), 0)
        record = json.loads((self.root / "target/agent/selected.json").read_text())
        self.assertEqual(record["selected_module_executed_tests"], 2)

    def test_skipped_only_selection_fails(self):
        self.repository()
        process = self.write("fake_maven.py", "from pathlib import Path\np=Path('core/target/surefire-reports/TEST-new.xml')\np.parent.mkdir(parents=True, exist_ok=True)\np.write_text('<testsuite tests=\"1\" skipped=\"1\"/>')\n")
        with patch.object(dev, "ROOT", self.root), patch.object(dev, "maven_prefix", return_value=[sys.executable, str(process)]):
            self.assertEqual(dev.run_maven(["test"], "skipped", "core"), 1)

    def test_committed_credentials_are_checked_against_explicit_base_and_redacted(self):
        self.repository()
        token = "ghp_" + "a" * 36
        self.write("source.txt", token + "\n")
        subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Tool test", "-c",
                        "user.email=tool@example.invalid", "commit", "-qm", "Add sample"], check=True)
        output = io.StringIO()
        with patch.object(dev.repository_checks, "ROOT", self.root), contextlib.redirect_stderr(output):
            self.assertEqual(dev.repository_checks.scan_changes("HEAD~1"), 1)
        self.assertNotIn(token, output.getvalue())
        self.assertIn("Values are hidden", output.getvalue())

    def test_committed_source_whitespace_fails_but_fixture_whitespace_is_allowed(self):
        self.repository()
        for path, expected in (("core/src/main/java/One.java", 2), ("core/src/test/resources/fixture.java", 0)):
            self.write(path, "deliberate trailing space \n")
            subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Tool test", "-c",
                            "user.email=tool@example.invalid", "commit", "-qm", "Add sample"], check=True)
            self.assertEqual(dev.whitespace_status("HEAD~1", self.root), expected)


if __name__ == "__main__":
    unittest.main()
