# Work in this repository

This repository contains eight shared Java libraries. It does not contain a
standalone analyzer or a SonarQube server. Start with the module table in
[README.md](README.md). Read only the module instructions that apply to the task.

## Prepare and check

Use Python 3.9 or later, Git, JDK 21 or later, and Maven 3.9 or later.
`mise.toml` selects the existing Java 21.0 and Maven 3.9 release lines. From the
repository root, run:

```sh
mise trust
mise install
python3 scripts/dev.py doctor --resolve
python3 scripts/dev.py test recognizers
```

`doctor` checks the JVM used by Maven. That JVM can differ from `java` on PATH.
The parent POM is `org.sonarsource.parent:parent`. Local tests do not require
Vault, a SonarQube server, npm, NuGet, or publishing credentials. If an artifact
returns HTTP 401 or 403, check your Maven mirror and server settings. Keep those
settings outside Git. Do not print tokens or copy CI deployer credentials.
The optional `.devcontainer` uses the Maven 3 image with JDK 21. It installs Git
and Python from Debian packages. The image and packages can receive updates.

## Select work and tests

```sh
python3 scripts/dev.py affected origin/master
python3 scripts/dev.py test commons --test TokenLocationTest
python3 scripts/dev.py test regex-parsing --test EmptyGroupFinderTest
python3 scripts/dev.py repeat recognizers --test CodeRecognizerTest
python3 scripts/dev.py verify
python3 scripts/dev.py build
python3 scripts/dev.py sbom
python3 scripts/dev.py reproduce recognizers
python3 scripts/dev.py quality recognizers
```

`affected` includes local edits and reverse dependencies, including test
dependencies. It gives a conservative module list. It cannot identify external
analyzer consumers. Module names are directory names. The Maven commands include
`-pl` and `-am` for selected modules. A class selector uses
`-Dsurefire.failIfNoSpecifiedTests=false` so upstream modules can have no matching
class. Always run the module suite after a focused test.
The tool also requires a fresh report with executed tests in the selected
module. A misspelled class or method fails instead of passing silently.

`test` runs unit tests. `verify` also runs the offline repository checks and
Maven verification, including the existing JAR size limits. `build` installs
artifacts in the local Maven cache. None of these commands deploys artifacts.
They keep `target/` for incremental builds. Use `mvn clean` only when stale
outputs are a possible cause. Cold dependency downloads take longer than a warm
build. No fixed build duration is guaranteed.

`sbom` records the resolved dependency graph in CycloneDX files under `target/`.
CI retains these files with the build output. Maven uses strict checksum checks,
but this is not a content lock for every transitive artifact. Dependency review
rejects new high and critical advisories when GitHub dependency review is enabled.
`reproduce` makes two clean packages of one module and compares their bytes. It
uses the commit time for archive timestamps and the inherited `Build-Time`
manifest field. It skips tests, so run tests separately first. A local match does
not guarantee that signing or publishing produces identical bytes.

The command writes its duration, exit status, and command to `target/agent/`.
It fails if Maven changes a tracked file. Read the first Maven `ERROR` and the
module's `target/surefire-reports/` after a test failure. Preserve the failing
sample, exact command, JDK, and revision. Do not rerun until green without
recording and correcting the cause. `repeat` runs a selected suite twice and
stops at the first failure. It is a local flake check, not proof of determinism.

## Follow the source contracts

Use two spaces in Java and XML, and four spaces in Python. Use the existing
package names and `*Test` class names. Keep imports explicit. Match package
directories. `scripts/dev.py check` rejects reactor cycles, undeclared local
source imports, package-directory errors, and added whitespace errors. It also
runs the command-tool tests. `.editorconfig` gives editor defaults. Test resources
can contain deliberate invalid source and whitespace; do not format them.
Use `check --base <ref>` to include committed changes in whitespace and
credential checks. CI supplies the PR base explicitly. With no base, the tool
checks local changes from `HEAD`.
To run these fast checks before a commit, opt in with
`git config --local core.hooksPath .githooks`. This is a repository-local setting;
the setup command does not change global Git configuration. The same checks run
in CI even when the local hook is not enabled.

New public APIs affect many analyzers. Prefer the existing API and a small
implementation change. Keep `internal` implementation classes behind their
public entry points. Preserve package null contracts from `package-info.java`.
Do not add a dependency to a shared library without checking its consumers and
JAR size. Compiler checks and tests check types; NEXT analysis supplies broader
null, dead-code, duplication, and size feedback. Do not claim that the local
checks replace this analysis.

`quality` produces local PMD and duplicate-code reports under each selected
module's `target/site/`. It supplies feedback for unused private code, class
names, large classes/methods, and duplicated token blocks. These reports are
not a new merge gate, and they do not prove null safety or API compatibility.
The VS Code extension recommendations enable EditorConfig and SonarQube for IDE
when installed. CI's `coverage` profile also checks a 60% line-coverage floor in
each module with measured code. A coverage floor does not prove that assertions
are useful; keep behavior assertions and counterexamples in the tests.

Start a new Java test from a small adjacent test. `CodeRecognizerTest` is a
simple JUnit 4 example. `EmptyGroupFinderTest` and its YAML file show the regex
finder pattern. Some modules use JUnit 4 and others use JUnit Jupiter; preserve
the module's dependency choice. Include a positive case, a negative case, and
relevant boundaries. Assert actual output and locations. Do not weaken expected
results to obtain a passing test. No generated test scaffold is required.

## Keep changes bounded

Work on a side branch. Use `fix/`, `feat/`, or `chore/` with a short description.
Use an `ACOMMONS-<number>` prefix in the commit and PR title when a ticket exists;
otherwise use a conventional title such as `chore: Improve local checks`.
Separate behavior changes from broad formatting or dependency updates. Keep
each PR small enough to explain and test. The readiness workflow checks the
title and reports changed line counts. Its size limit requires a maintainer's
`large-change-reviewed` label for an exceptional PR.

Before completion, run the affected module suites, `python3 scripts/dev.py
verify`, and inspect the full diff. Required CI and NEXT checks must pass before
merge. A local pass does not establish downstream compatibility or a green CI
result. If an API or common parser changes, identify affected consumers in the
PR. Do not merge, tag, sign, release, promote, publish, or force-push unless the
user explicitly requests that operation. CI performs those operations under its
own permissions. Never use release credentials for local tests.

## Edit the source of generated data

Do not commit `target/`, `dependency-reduced-pom.xml`, generated configuration
JSON, or generated npm/NuGet archives. Configuration JSON comes from
`SecretClassifier` and the exporter classes. Change those sources, then run
configuration-module tests and package verification. Packaging templates under
`sonar-analyzer-commons-configurations/src/main/packaging/` are source files;
their filtered copies under `target/packaging/` are generated.

The verifier corpus under `test-commons/src/test/resources/`, and regex finder
YAML files, are deliberate test data. Edit them only to test a stated behavior.
Rule JSON and HTML under `commons/src/test/resources/` are loader test fixtures,
not production rule descriptions. Do not regenerate all expected data for an
unrelated change. Marketing owns the marked root README section and logo URLs.

Use network access for required dependency resolution and source references.
Do not send repository source, test corpora, credentials, or build logs to an
external service unless the task authorizes that service. Repository checks
scan added lines for selected credential formats; they cannot detect every
secret. Stop and remove a real credential if one is found, then follow the
organization's credential-rotation process.
