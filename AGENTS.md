# Work in this repository

This repository contains eight shared Java libraries. It does not contain a
standalone analyzer or a SonarQube server. Start with the module table in
[README.md](README.md). Read only the module instructions that apply to the task.

## Prepare and check

Use Mise for repository commands. [mise.toml](mise.toml) selects Java 21,
Maven 3.9, Python 3, the SonarQube CLI, GitHub CLI, and jq. Git and Bash are
host prerequisites; Windows requires Git Bash. No shell activation is needed.

At the start of each task, run `mise ls --local --current --missing --no-header`.
If it lists missing tools, run `mise install` yourself without asking for
confirmation. Do this before edits or checks, including changes to documentation.
After installation, run `mise doctor project`. If a later command reports a
missing project tool, repeat the tool check and install the missing tools.
Do not ask the user to run these commands.

If a Mise command fails, including a command run by a hook, run
`mise doctor project` once and read the failed checks.
If missing tools or an incomplete installation can explain the failure,
run `mise install` yourself. Then rerun the doctor and the failed command.
For build, test, configuration, or authentication failures, inspect the error
and fix the cause. Do not report a missing tool and stop without trying setup.

Honor an explicit instruction that forbids installation. If Mise is unavailable,
or installation or setup fails, report the cause and skip checks that need the
missing tools. Continue independent work and authorized commits and pushes.
Do not retry the same setup failure without investigating its cause.

Start with:

```sh
mise install
mise doctor project
mise run build
mise run lint
```

The native post-install hook runs `setup`. It checks all independent prerequisites
and reports missing access as warnings, without prompting or failing setup.
Use `mise run setup --interactive` in a terminal for optional sign-in prompts.
CI and non-terminal sessions never prompt. Use `mise doctor project --json` for
machine-readable results and a failing exit status when requirements are missing.
Plain `mise doctor` checks Mise itself. `mise tasks` lists supported commands;
`mise run test --help` explains test selection. `mise ls` shows tool versions.
Run `mise run test:tooling` after changing tasks, setup, or doctor checks.

Doctor checks tool startup, root Maven validation, Git history for formatting,
and NEXT authentication, project access, and agent-analysis entitlement. It
suppresses probe output and prints repair hints. Probes have time limits and
failed prerequisites block dependent checks. Root validation can use a cached
parent: it does not prove access to every uncached module or test dependency.
Doctor does not build the project, run tests or analysis, or start a server.
The NEXT connection uses `SONARQUBE_NEXT_TOKEN`, `SONARQUBE_CLI_TOKEN`, or saved
CLI credentials. A Cloud token does not grant NEXT access. Use `mise run analyze`
for deep agent analysis when the doctor confirms the required access.

Mise also installs JFrog CLI for the existing CI package tasks. Local unit tests
do not require publishing credentials, npm, NuGet, Vault, or a SonarQube server.
Configure the approved Repox mirror at
`https://repox.jfrog.io/artifactory/sonarsource` and reader credentials in your
user Maven settings. The parent POM is `org.sonarsource.parent:parent`.
An HTTP 401/403 or an unresolved private parent is an access problem; do not
replace the parent or disable checks. Never print settings or copy CI tokens.
The optional [.devcontainer](.devcontainer) installs Mise and runs `mise install`
after creation. It does not copy host Maven credentials; configure approved
access inside the container. Setup reports missing access without blocking.

## Select work and tests

```sh
mise run test recognizers
mise run test regex-parsing EmptyGroupFinderTest
mise run test regex-parsing
mise run build
mise run sbom
mise run lint:reports -- -pl recognizers -am
```

`test` accepts an optional module and class or method. A focused selection first
installs that module and its dependencies with tests skipped, then runs the
selection in its module. A missing class or method must fail; do not disable
missing-test checks. Run the full module suite after a focused test.
`build` cleans output and installs artifacts with tests and the CI `coverage`
profile. It runs through Maven `verify`, including JAR-size and coverage checks.
`verify` performs the same verification without cleaning or installing; use
`mise run verify -- -pl <module> -am` to select modules.
`test` retains incremental output. Use `mise run clean` to remove output.
Tasks select the root directory, including when called from a module. Tasks
without declared arguments, such as `verify`, forward extra Maven arguments
after `--`. No fixed build duration is guaranteed.

The [build workflow](.github/workflows/build.yml) calls Mise for formatting,
inventory, source preservation, task tests, and repository-owned package
operations. The shared Maven action owns CI versioning, build execution,
signing, analysis, and Maven deployment. It has no custom build-command input;
`mise run ci:configure` supplies its build options from the same definition
used by local verification. Shared analysis and promotion actions retain their
service-specific logic. `mise tasks` lists package tasks with `ci:` and
`release:` prefixes. These operations require their authorized CI workflows and
credentials; setup and doctor do not exercise publication or DigiCert signing.
The [release workflow](.github/workflows/release.yml) installs Node 26 and .NET 10
through the relevant Mise publish tasks. Local Java work does not need them.

The CycloneDX goal records resolved dependencies under `target/`. CI retains
these files with the build output. `-C` enables strict artifact checksums; it
does not lock transitive artifact content. The organization SCA check and NEXT
quality gate provide the existing dependency checks. NEXT checks new malware,
licensing, and vulnerability measures. The separate GitHub dependency-review
workflow is not used because this repository does not support that service.

For a failure, read the first Maven `ERROR` and the module's
`target/surefire-reports/`. Maven prints the test counts and elapsed time. Keep
the command, JDK, revision, failing sample, and relevant log. Correct the cause
before repeating a failed run. Run the same Maven test command twice when a
local repeat check is useful; a repeated pass does not prove determinism.
Inspect `git diff` before and after a build. CI uses `mise run check:source` to
reject changes to tracked source files under `src/`.

Use `git diff --name-only origin/master` to identify changed modules. Read their
POMs and use Maven `-amd` to include reactor consumers, for example
`mise run verify -- -pl regex-parsing -am -amd`. Identify external analyzer
consumers
separately; they are outside this reactor.

## Follow the source contracts

Use two spaces in Java and XML. Use the existing
package names and `*Test` class names. Keep imports explicit. Match package
directories. Maven rejects reactor dependency cycles. `.editorconfig` gives
editor defaults. Use Mise to format Java implementation and test code:

```sh
mise run lint --fix
mise run lint
```

Spotless uses the Eclipse formatter with the shared
[SonarSource profile](https://github.com/SonarSource/sonar-developer-toolset/blob/master/eclipse/sonar-formatter.xml).
Spotless loads that profile from its canonical URL. It selects only
`src/main/java/**/*.java` and `src/test/java/**/*.java` in each module. Test
resources can contain deliberate invalid source and whitespace; they are
excluded. Generated files under `target/` are also excluded. Keep unrelated
formatting out of a behavior change. Spotless uses `origin/master` as its
ratchet reference. It
checks whole Java files added or modified since the common ancestor with that
reference. Fetch `origin/master` before a local run. Existing unchanged files
are outside the ratchet, so the source tree does not need a bulk reformat.
CI fetches full Git history and runs the same check after the shared Maven
build has configured private artifact access. Formatting violations fail the
build job. Run the apply goal locally before pushing a Java change.

New public APIs affect many analyzers. Prefer the existing API and a small
implementation change. Keep `internal` implementation classes behind their
public entry points. Preserve package null contracts from `package-info.java`.
Do not add a dependency to a shared library without checking its consumers and
JAR size. Compiler checks and tests check types; NEXT analysis supplies broader
null, dead-code, duplication, and size feedback. Do not claim that the local
checks replace this analysis.

`mise run lint:reports` produces local PMD and duplicate-code reports under
each selected module's `target/site/`. It supplies feedback for unused private code, class
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
each PR small enough to explain and test.

Before completion, run the affected module suites, `mise run build`, and inspect
the full diff. Required CI and NEXT checks must pass before
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
external service unless the task authorizes that service. Stop and remove a
real credential if one is found, then follow the
organization's credential-rotation process.
