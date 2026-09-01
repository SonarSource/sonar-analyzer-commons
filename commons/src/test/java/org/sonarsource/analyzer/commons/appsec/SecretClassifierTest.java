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

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The samples themselves live in {@link SecretClassifier} (main scope) so the build can publish them as
 * {@code secret-exclusion-corpus.json}; see {@link SecretClassifier#exportKnownNonSecretSamples()}. This test stays
 * the gate that keeps that corpus complete and correctly categorized: it is what fails when a pattern is added
 * without a sample, or when a sample is suppressed by a group other than the one it is declared under.
 */
class SecretClassifierTest {

  static final List<String> KNOWN_NON_SECRETS = SecretClassifier.exportKnownNonSecretSamples().stream()
    .flatMap(group -> group.values().stream())
    .toList();

  private static Stream<String> samplesOf(SecretClassifier.Category category) {
    return SecretClassifier.exportKnownNonSecretSamples().stream()
      .filter(group -> group.category().equals(category.name()))
      .flatMap(group -> group.values().stream());
  }

  static Stream<String> fakeValueSamples() {
    return samplesOf(SecretClassifier.Category.FAKE_VALUE);
  }

  static Stream<String> secretSamples() {
    return samplesOf(SecretClassifier.Category.SECRET);
  }

  static Stream<String> placeholderSamples() {
    return samplesOf(SecretClassifier.Category.PLACEHOLDER);
  }

  static Stream<String> encryptedSamples() {
    return samplesOf(SecretClassifier.Category.ENCRYPTED);
  }

  static Stream<String> referenceSamples() {
    return samplesOf(SecretClassifier.Category.REFERENCE);
  }

  static Stream<String> structuredFormatSamples() {
    return samplesOf(SecretClassifier.Category.STRUCTURED_FORMAT);
  }

  @ParameterizedTest
  @MethodSource("fakeValueSamples")
  void shouldBeSuppressedByFakeValueCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.FAKE_VALUE);
  }

  @ParameterizedTest
  @MethodSource("secretSamples")
  void shouldBeSuppressedBySecretCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.SECRET);
  }

  @ParameterizedTest
  @MethodSource("placeholderSamples")
  void shouldBeSuppressedByPlaceholderCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.PLACEHOLDER);
  }

  @ParameterizedTest
  @MethodSource("encryptedSamples")
  void shouldBeSuppressedByEncryptedCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.ENCRYPTED);
  }

  @ParameterizedTest
  @MethodSource("referenceSamples")
  void shouldBeSuppressedByReferenceCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.REFERENCE);
  }

  @ParameterizedTest
  @MethodSource("structuredFormatSamples")
  void shouldBeSuppressedByStructuredFormatCategory(String value) {
    assertThat(SecretClassifier.classify(value)).isEqualTo(SecretClassifier.Category.STRUCTURED_FORMAT);
  }

  static Stream<String> knownNonSecrets() {
    return KNOWN_NON_SECRETS.stream();
  }

  @ParameterizedTest
  @MethodSource("knownNonSecrets")
  void shouldClassifyKnownNonSecrets(String value) {
    assertThat(SecretClassifier.isKnownNonSecret(value)).isTrue();
  }

  @Test
  void coverageShouldExerciseEveryPatternAndExactValue() {
    for (Pattern pattern : SecretClassifier.allPatterns()) {
      assertThat(KNOWN_NON_SECRETS)
        .as("no sample exercises pattern: %s", pattern.pattern())
        .anyMatch(sample -> pattern.matcher(sample).find());
    }
    for (String exact : SecretClassifier.exactMatchValues()) {
      assertThat(KNOWN_NON_SECRETS)
        .as("no sample exercises exact value: %s", exact)
        .anyMatch(sample -> sample.equalsIgnoreCase(exact));
    }
  }

  @Test
  void everyCategoryShouldContributeSamplesInDeclarationOrder() {
    List<SecretClassifier.SampleGroupView> groups = SecretClassifier.exportKnownNonSecretSamples();

    assertThat(groups)
      .extracting(SecretClassifier.SampleGroupView::category)
      .containsExactlyElementsOf(Stream.of(SecretClassifier.Category.values()).map(Enum::name).toList());
    assertThat(groups).allSatisfy(group -> assertThat(group.values()).as("no samples for %s", group.category()).isNotEmpty());
  }

  static Stream<String> secretCandidates() {
    return SecretClassifier.exportSecretCandidateSamples().stream();
  }

  @ParameterizedTest
  @MethodSource("secretCandidates")
  void shouldNotClassifySecretCandidatesAsNonSecrets(String value) {
    assertThat(SecretClassifier.isKnownNonSecret(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "${secret}",
    "Xk9Lm2Qp7Rs4Tv1Wz0"
  })
  void contextOverloadShouldMatchBareValueOverload(String value) {
    assertThat(SecretClassifier.isKnownNonSecret(value, SecretClassifier.Context.empty()))
      .isEqualTo(SecretClassifier.isKnownNonSecret(value));
  }

  @Test
  void shouldNotClassifyNullAsNonSecret() {
    assertThat(SecretClassifier.isKnownNonSecret(null)).isFalse();
    assertThat(SecretClassifier.isKnownNonSecret(null, SecretClassifier.Context.empty())).isFalse();
  }

  @Test
  void emptyContextShouldBeSingleton() {
    assertThat(SecretClassifier.Context.empty()).isSameAs(SecretClassifier.Context.empty());
  }
}
