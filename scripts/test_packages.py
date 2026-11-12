"""Exercise package task failures and artifact identity with offline CLI stubs."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent


class PackageTasksTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="mise-packages-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.log = self.root / "calls.jsonl"
        binaries = self.root / "bin"
        binaries.mkdir()
        client = '''#!/usr/bin/env python3
import json, os, sys
with open(os.environ['PACKAGE_TEST_LOG'], 'a') as log:
    log.write(json.dumps([os.path.basename(sys.argv[0]), *sys.argv[1:]]) + '\\n')
if sys.argv[1:3] == ['rt', 'search']:
    print(os.environ.get('PACKAGE_TEST_FOUND', '1'))
sys.exit(int(os.environ.get('PACKAGE_TEST_EXIT', '0')))
'''
        for name in ("jf", "npm", "dotnet", "smctl", "jsign", "rm"):
            executable = binaries / name
            executable.write_text(client)
            executable.chmod(0o755)
        self.env = {**os.environ, "PATH": str(binaries) + os.pathsep + os.environ["PATH"],
                    "CI": "true", "PACKAGE_TEST_LOG": str(self.log), "PROJECT_VERSION": "2.36.0.1234",
                    "GITHUB_REPOSITORY": "SonarSource/sonar-analyzer-commons", "BUILD_NUMBER": "1234",
                    "ARTIFACTORY_ACCESS_TOKEN": "fixture", "PROMOTER_ACCESS_TOKEN": "fixture",
                    "RELEASE_VERSION": "2.36.0.1234", "GITHUB_OUTPUT": str(self.root / "output")}

    def run_script(self, name, expected=0):
        result = subprocess.run(["bash", str(SCRIPTS / (name + ".sh"))], cwd=self.root,
                                env=self.env, text=True, capture_output=True, check=False)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return result

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def archive(self, suffix):
        directory = self.root / "sonar-analyzer-commons-configurations/target"
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / ("sonar-analyzer-commons-configurations-2.36.0.1234-" + suffix)
        path.write_bytes(b"fixture archive")
        return path

    def test_ci_operations_refuse_local_execution(self):
        self.env["CI"] = "false"
        for path in sorted(SCRIPTS.glob("*.sh")):
            if path.stem.startswith(("ci-", "release-")):
                with self.subTest(task=path.stem):
                    self.run_script(path.stem, expected=2)
        self.assertEqual([], self.calls())

    def test_prepare_nuget_requires_archive_and_preserves_bytes_and_identity(self):
        self.run_script("ci-nuget-prepare", expected=1)
        archive = self.archive("nupkg.zip")
        self.run_script("ci-nuget-prepare")
        staged = self.root / "nuget-staging/SonarAnalyzer.CommonsConfigurations.2.36.0.1234.nupkg"
        self.assertEqual(archive.read_bytes(), staged.read_bytes())
        self.assertEqual("dir=nuget-staging\nid=SonarAnalyzer.CommonsConfigurations\nversion=2.36.0.1234\n",
                         (self.root / "output").read_text())

    def test_npm_deployment_keeps_the_maven_archive_and_converts_the_feed_version(self):
        self.run_script("ci-npm-deploy", expected=1)
        self.archive("npm.tar.gz")
        self.run_script("ci-npm-deploy")
        upload = next(call for call in self.calls() if call[1:3] == ["rt", "upload"])
        self.assertTrue(upload[-2].endswith("-2.36.0.1234-npm.tar.gz"))
        self.assertEqual("sonarsource-npm-public-qa/@sonarsource/analyzer-commons-configurations/-/analyzer-commons-configurations-2.36.0-1234.tgz", upload[-1])
        self.env["PACKAGE_TEST_EXIT"] = "23"
        self.run_script("ci-npm-deploy", expected=23)

    def test_nuget_promotion_fails_when_no_artifact_arrives(self):
        self.env["GITHUB_EVENT_NAME"] = "pull_request"
        self.env["PACKAGE_TEST_FOUND"] = "0"
        self.run_script("ci-nuget-promote", expected=1)
        promote = next(call for call in self.calls() if call[1:3] == ["rt", "build-promote"])
        self.assertIn("sonarsource-nuget-dev", promote)
        self.assertIn("--status=it-passed-pr", promote)
        self.env["GITHUB_EVENT_NAME"] = "push"
        self.env["PACKAGE_TEST_FOUND"] = "1"
        self.run_script("ci-nuget-promote")
        promote = [call for call in self.calls() if call[1:3] == ["rt", "build-promote"]][-1]
        self.assertIn("sonarsource-nuget-public", promote)

    def test_release_download_uses_the_requested_build_and_release_feed(self):
        self.run_script("release-nuget-download")
        download = next(call for call in self.calls() if call[1:3] == ["rt", "download"])
        self.assertIn("--build=sonar-analyzer-commons_nuget/1234", download)
        self.assertIn("sonarsource-nuget-releases/*.nupkg", download)

    def test_npm_publish_requires_an_archive_and_preserves_publication_options(self):
        self.run_script("release-npm-publish", expected=1)
        directory = self.root / "npm-release"
        directory.mkdir()
        (directory / "fixture.tgz").write_bytes(b"fixture")
        self.run_script("release-npm-publish")
        publish = next(call for call in self.calls() if call[0] == "npm")
        self.assertEqual(["npm", "publish", "./npm-release/fixture.tgz", "--registry=https://registry.npmjs.org/",
                          "--provenance", "--access", "public", "--tag", "latest"], publish)


if __name__ == "__main__":
    unittest.main()
