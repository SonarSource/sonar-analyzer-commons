"""Check credential redaction and contribution-policy decisions."""

import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("repository_checks", Path(__file__).resolve().parents[1] / "repository_checks.py")
checks = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checks)


class RepositoryChecksTest(unittest.TestCase):
    """Verify positive cases and ordinary source counterexamples."""

    def test_credential_formats(self):
        for sample in ("ghp_" + "a" * 36, "github_pat_" + "a" * 80, "AKIA" + "A" * 16,
                       "-----BEGIN " + "PRIVATE KEY-----"):
            self.assertEqual(checks.credential_lines("safe\n" + sample + "\nsafe"), [2])

    def test_short_examples_are_not_credentials(self):
        self.assertEqual(checks.credential_lines("ghp_example\nAKIA_ID\npublic key"), [])

    def test_jira_and_conventional_titles(self):
        self.assertTrue(checks.title_valid("ACOMMONS-123 Correct range handling"))
        self.assertTrue(checks.title_valid("chore: Improve local checks"))
        self.assertFalse(checks.title_valid("Update stuff"))
        self.assertFalse(checks.title_valid("ACOMMONS-123"))

    def test_pr_size_counts_additions_and_deletions(self):
        self.assertEqual(checks.changed_line_count("10\t3\tfile.java\n2\t4\tname with spaces.md\n"), 19)
        with self.assertRaisesRegex(ValueError, "Binary"):
            checks.changed_line_count("-\t-\tarchive.zip\n")


if __name__ == "__main__":
    unittest.main()
