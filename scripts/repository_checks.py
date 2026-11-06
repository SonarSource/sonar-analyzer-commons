"""Check added credential patterns and pull-request metadata without network access."""

import argparse
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CREDENTIAL_PATTERNS = (
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{70,}\b"),
    re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
)


def credential_lines(content):
    """Return matching line numbers without returning credential values."""
    return [number for number, line in enumerate(content.splitlines(), 1)
            if any(pattern.search(line) for pattern in CREDENTIAL_PATTERNS)]


def title_valid(title):
    """Accept Jira-prefixed or conventional contribution titles."""
    return bool(re.fullmatch(r"(?:ACOMMONS-\d+ .+|(?:feat|fix|chore|test|refactor|docs|build|ci|perf)(?:\([\w-]+\))?!?: .+)", title))


def changed_line_count(numstat):
    """Count text changes and reject an incomplete binary size estimate."""
    count = 0
    for line in numstat.splitlines():
        added, deleted, _ = line.split("\t", 2)
        if added == "-" or deleted == "-":
            raise ValueError("Binary changes require the large-change-reviewed label")
        count += int(added) + int(deleted)
    return count


def scan_changes(base):
    """Scan added lines and untracked text files without printing their content."""
    patch = subprocess.check_output(["git", "diff", "--no-ext-diff", "--unified=0", base, "--"], cwd=ROOT, text=True)
    added = "\n".join(line[1:] for line in patch.splitlines() if line.startswith("+") and not line.startswith("+++"))
    failures = len(credential_lines(added))
    untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=ROOT)
    for name in untracked.split(b"\0"):
        if name:
            path = ROOT / name.decode("utf-8", errors="surrogateescape")
            try:
                matches = credential_lines(path.read_text(encoding="utf-8"))
            except UnicodeError:
                continue
            if matches:
                print(f"Credential pattern in {path.relative_to(ROOT)} at lines {matches}", file=sys.stderr)
                failures += len(matches)
    if failures:
        print(f"Found {failures} added credential pattern(s). Values are hidden. Remove real credentials and arrange rotation.", file=sys.stderr)
    return int(bool(failures))


def main():
    """Check one explicit comparison base and optional PR title and size."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="HEAD")
    parser.add_argument("--title")
    parser.add_argument("--allow-large", action="store_true")
    args = parser.parse_args()
    status = scan_changes(args.base)
    if args.title is not None:
        if not title_valid(args.title):
            print("Use an ACOMMONS ticket prefix or a conventional PR title.", file=sys.stderr)
            status = 1
        changes = subprocess.check_output(["git", "diff", "--numstat", args.base, "--"], cwd=ROOT, text=True)
        try:
            size = changed_line_count(changes)
            print(f"PR changed lines: {size}")
            too_large = size > 800
        except ValueError as error:
            print(error, file=sys.stderr)
            too_large = True
        if too_large and not args.allow_large:
            print("Split this PR or obtain the large-change-reviewed label after maintainer review.", file=sys.stderr)
            status = 1
    return status


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Repository check failed: {error}", file=sys.stderr)
        sys.exit(1)
