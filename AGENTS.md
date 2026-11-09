# Work in this repository

This repository contains eight shared Java libraries. It does not contain a
standalone analyzer or a SonarQube server. Start with the module table in
[README.md](README.md). Read only the module instructions that apply to the task.

## Prepare and check

Use Git, JDK 21 or later, and Maven 3.9 or later for the Java build.
`mise.toml` selects the existing Java 21.0 and Maven 3.9 release lines.
Activate your chosen tools, then run these commands from the repository root:

```sh
mvn --version
mvn -B -ntp -C validate
mvn -B -ntp -C -Pcoverage verify
```

`mvn --version` reports the JVM used by Maven. That JVM can differ from `java`
on PATH. Maven checks the minimum JDK through the parent POM. `validate` also
checks parent POM and build-extension resolution; it does not resolve every
test dependency. The parent POM is `org.sonarsource.parent:parent`. Local tests
do not require Vault, a SonarQube server, npm, NuGet, or publishing credentials.
If an artifact returns HTTP 401 or 403, check your Maven mirror and server
settings. Keep those settings outside Git. Do not print tokens or copy CI
deployer credentials. The optional `.devcontainer` uses the Maven 3 image with
JDK 21. Its image and Debian packages can receive updates.

## Select work and tests

```sh
mvn -B -ntp -C -pl recognizers -am test
mvn -B -ntp -C -pl regex-parsing -am install -DskipTests
mvn -B -ntp -C -pl regex-parsing test -Dtest=EmptyGroupFinderTest -DfailIfNoTests=true
mvn -B -ntp -C -pl regex-parsing -am test
mvn -B -ntp -C install
mvn -B -ntp -C org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
mvn -B -ntp -C -pl recognizers -am compile pmd:pmd pmd:cpd
```

`-pl` selects module directory names. `-am` also builds their reactor
dependencies. For a named test, first install the selected module and its
upstream modules with `-DskipTests`, then run the test in the selected module
without `-am`. Use `-DfailIfNoTests=true` on the selected-module command so an empty method
selection also fails. Do not set `surefire.failIfNoSpecifiedTests=false`. Run the full module
suite after the focused test. The install preparation skips tests; it does not
replace the test command.

`test` runs unit tests. `verify` also checks packages and the existing JAR size
limits. `-Pcoverage verify` checks the coverage floor. `install` puts artifacts
in the local Maven cache. These commands retain `target/` for incremental work.
Use `mvn clean` only when stale outputs are a possible cause. No fixed build
duration is guaranteed.

The CycloneDX goal records resolved dependencies under `target/`. CI retains
these files with the build output. `-C` enables strict artifact checksums; it
does not lock transitive artifact content. Dependency review rejects new high
and critical advisories when GitHub dependency review is enabled.

For a failure, read the first Maven `ERROR` and the module's
`target/surefire-reports/`. Maven prints the test counts and elapsed time. Keep
the command, JDK, revision, failing sample, and relevant log. Correct the cause
before repeating a failed run. Run the same Maven test command twice when a
local repeat check is useful; a repeated pass does not prove determinism.
Inspect `git diff` before and after a build. CI uses a separate Git check to
reject changes to tracked source files under `src/`.

Use `git diff --name-only origin/master` to identify changed modules. Read their
POMs and use Maven `-amd` to include reactor consumers, for example
`mvn -pl regex-parsing -am -amd test`. Identify external analyzer consumers
separately; they are outside this reactor.

## Follow the source contracts

Use two spaces in Java and XML. Use the existing
package names and `*Test` class names. Keep imports explicit. Match package
directories. Maven rejects reactor dependency cycles. `.editorconfig` gives
editor defaults. Use the Maven formatter for Java implementation and test code:

```sh
mvn com.diffplug.spotless:spotless-maven-plugin:apply
mvn com.diffplug.spotless:spotless-maven-plugin:check
```

Spotless uses the Eclipse formatter with the shared
[SonarSource profile](https://github.com/SonarSource/sonar-developer-toolset/blob/master/eclipse/sonar-formatter.xml).
Spotless loads that profile from its canonical URL. It selects only
`src/main/java/**/*.java` and `src/test/java/**/*.java` in each module. Test
resources can contain deliberate invalid source and whitespace; they are
excluded. Generated files under `target/` are also excluded. To format only
files in a change, add `'-DspotlessFiles=.*MyClass\.java'`. This option accepts
comma-separated Java regular expressions matched against absolute file paths;
quote the patterns in the shell. Keep unrelated formatting out of a
behavior change. The goals run only when requested; the existing source tree
has not been reformatted, and formatting is not a new default CI gate.

New public APIs affect many analyzers. Prefer the existing API and a small
implementation change. Keep `internal` implementation classes behind their
public entry points. Preserve package null contracts from `package-info.java`.
Do not add a dependency to a shared library without checking its consumers and
JAR size. Compiler checks and tests check types; NEXT analysis supplies broader
null, dead-code, duplication, and size feedback. Do not claim that the local
checks replace this analysis.

`pmd:pmd pmd:cpd` produces local PMD and duplicate-code reports under each selected
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
each PR small enough to explain and test.

Before completion, run the affected module suites, `mvn -B -ntp -C -Pcoverage
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
external service unless the task authorizes that service. Stop and remove a
real credential if one is found, then follow the
organization's credential-rotation process.
