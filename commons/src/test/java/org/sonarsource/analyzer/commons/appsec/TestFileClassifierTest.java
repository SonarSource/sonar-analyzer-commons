/*
 * SonarSource Analyzers Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.appsec;

import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Configuration;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;
import org.sonar.scanner.plugin.api.impl.config.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileClassifierTest {

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private static final String[] GLOBS = {
    "**/test/**", "**/tests/**",
    "**/*Test.java", "**/*Tests.java", "**/*Spec.java", "**/*IT.java"
  };

  private static Configuration config(String... keyValues) {
    var settings = new MapSettings();
    for (int i = 0; i < keyValues.length; i += 2) {
      settings.setProperty(keyValues[i], keyValues[i + 1]);
    }
    return settings.asConfig();
  }

  private static TestFileClassifier classifier(Configuration config) {
    return TestFileClassifier.of(config, GLOBS);
  }

  private static InputFile file(String relativePath) {
    return new TestInputFileBuilder("module", relativePath).build();
  }

  // A context flavour an analyzer would supply, carrying structural info its detector reads.
  private static final class AnnotatedContext implements TestFileClassifier.Context {
    final boolean hasTestImport;

    AnnotatedContext(boolean hasTestImport) {
      this.hasTestImport = hasTestImport;
    }
  }

  private static final Predicate<TestFileClassifier.Context> IMPORTS_TEST_FRAMEWORK =
    context -> context instanceof AnnotatedContext && ((AnnotatedContext) context).hasTestImport;

  @ParameterizedTest
  @ValueSource(strings = {
    "src/main/java/FooTest.java",
    "a/FooTests.java",
    "a/FooSpec.java",
    "a/FooIT.java",
    "src/test/java/Foo.java",
    "module/tests/Foo.java"
  })
  void shouldMatchTestPathsWhenTestSourcesNotConfigured(String path) {
    assertThat(classifier(config()).looksLikeTestFile(file(path))).isTrue();
  }

  @Test
  void shouldFallBackToGenericTestDirectoriesWhenNoPatternsRegistered() {
    var classifier = TestFileClassifier.of(config()); // no globs registered
    assertThat(classifier.looksLikeTestFile(file("src/Test/java/Foo.java"))).isTrue();
    assertThat(classifier.looksLikeTestFile(file("a/Tests/Foo.java"))).isTrue();
    assertThat(classifier.looksLikeTestFile(file("src/test/java/Foo.java"))).isTrue();
    assertThat(classifier.looksLikeTestFile(file("a/tests/Foo.java"))).isTrue();
    assertThat(classifier.looksLikeTestFile(file("a/__tests__/Foo.js"))).isTrue();
    // fallback matches directories only, not test-named files
    assertThat(classifier.looksLikeTestFile(file("src/main/java/FooTest.java"))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "src/main/java/Foo.java",
    "src/main/java/audit.java" // case-sensitive: "audit.java" must not match "*IT.java"
  })
  void shouldNotMatchNonTestPaths(String path) {
    assertThat(classifier(config()).looksLikeTestFile(file(path))).isFalse();
  }

  @Test
  void shouldDisableHeuristicWhenTestSourcesConfigured() {
    assertThat(classifier(config("sonar.tests", "src/test")).looksLikeTestFile(file("src/main/java/FooTest.java"))).isFalse();
  }

  @Test
  void shouldNotDisableHeuristicWhenOnlyInclusionsOrExclusionsConfigured() {
    // sonar.test.inclusions/exclusions only refine an existing test-source set; they do not declare one
    var testFile = file("src/main/java/FooTest.java");
    assertThat(classifier(config("sonar.test.inclusions", "**/*Test.java")).looksLikeTestFile(testFile)).isTrue();
    assertThat(classifier(config("sonar.test.exclusions", "**/generated/**")).looksLikeTestFile(testFile)).isTrue();
  }

  @Test
  void shouldIgnoreBlankTestSourcesProperty() {
    assertThat(classifier(config("sonar.tests", "  ")).looksLikeTestFile(file("a/FooTest.java"))).isTrue();
  }

  @Test
  void shouldDisableHeuristicOnOptOutProperty() {
    var classifier = classifier(config(TestFileClassifier.HEURISTIC_DISABLED_KEY, "true"));
    assertThat(classifier.looksLikeTestFile(file("a/FooTest.java"))).isFalse();
  }

  @Test
  void contextOverloadShouldMatchConvenienceOverload() {
    var classifier = classifier(config());
    var testFile = file("a/FooTest.java");
    assertThat(classifier.looksLikeTestFile(testFile, TestFileClassifier.Context.empty()))
      .isEqualTo(classifier.looksLikeTestFile(testFile));
  }

  @Test
  void detectorShouldClassifyFromContextWhenNoPathMatches() {
    var classifier = TestFileClassifier.of(config(), IMPORTS_TEST_FRAMEWORK);
    var mainFile = file("src/main/java/Foo.java");
    assertThat(classifier.looksLikeTestFile(mainFile, new AnnotatedContext(true))).isTrue();
    assertThat(classifier.looksLikeTestFile(mainFile, new AnnotatedContext(false))).isFalse();
    // empty context (e.g. a path-only call): the detector cannot classify
    assertThat(classifier.looksLikeTestFile(mainFile)).isFalse();
  }

  @Test
  void detectorAndPatternsShouldBeCombined() {
    var classifier = TestFileClassifier.of(config(), IMPORTS_TEST_FRAMEWORK, GLOBS);
    // path pattern hit, no structural info needed
    assertThat(classifier.looksLikeTestFile(file("a/FooTest.java"))).isTrue();
    // no path hit, detector hit
    assertThat(classifier.looksLikeTestFile(file("src/main/java/Foo.java"), new AnnotatedContext(true))).isTrue();
  }

  @Test
  void detectorShouldBeGatedByConfiguredTestSources() {
    var classifier = TestFileClassifier.of(config("sonar.tests", "src/test"), IMPORTS_TEST_FRAMEWORK, GLOBS);
    assertThat(classifier.looksLikeTestFile(file("src/main/java/Foo.java"), new AnnotatedContext(true))).isFalse();
  }

  @Test
  void shouldWarnOnceWhenHeuristicFirstClassifiesATestFile() {
    var classifier = classifier(config());
    classifier.looksLikeTestFile(file("a/FooTest.java"));
    classifier.looksLikeTestFile(file("b/BarTest.java"));
    classifier.looksLikeTestFile(file("c/Main.java")); // no match, no extra warning

    assertThat(logTester.logs(Level.WARN)).hasSize(1);
    assertThat(logTester.logs(Level.WARN).get(0)).contains("sonar.tests");
  }

  @Test
  void shouldNotWarnWhenTestSourcesAreConfigured() {
    var classifier = classifier(config("sonar.tests", "src/test"));
    classifier.looksLikeTestFile(file("a/FooTest.java"));

    assertThat(logTester.logs(Level.WARN)).isEmpty();
  }
}
