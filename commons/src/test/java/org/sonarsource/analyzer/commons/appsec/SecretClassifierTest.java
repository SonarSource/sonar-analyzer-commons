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
 * Cases are grouped per {@link SecretClassifier.Category} so each group can be maintained alongside the matching
 * {@code PatternGroup} in {@link SecretClassifier}.
 */
class SecretClassifierTest {

  // One representative value per pattern / exact value, grouped by category.
  // Each value must be suppressed by the category it is listed under, not by an earlier group.
  // Adding a pattern without a sample here fails coverageShouldExerciseEveryPatternAndExactValue.

  static final List<String> FAKE_VALUE_SAMPLES = List.of(
    // Minimum length
    "", "abc",
    // Fake-word substrings
    "samplepassword", "EXAMPLE_SECRET", "deadbeef", "qwerty",
    // Templates whose placeholder names contain credential words — FAKE_VALUE wins before PLACEHOLDER
    "${secret}", "#{{secret}}", "$foo_bar",
    // Password-like values
    "password1234", "passwd",
    // Boolean / null / scalar literals
    "undefined", "true", "null",
    // "your..." prefix
    "yourpassword",
    // Same-character repetitions
    "abbbbc", "111111",
    // Masked value
    "1fj28...askn3i",
    // Other fake keywords
    "admin123", "vncpass", "super-secret-p4ssw0rd",
    // "secret" in "secretsmanager" triggers FAKE_VALUE before REFERENCE; kept here for REFERENCE ARN pattern coverage
    "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-pass");

  static final List<String> SECRET_SAMPLES = List.of(
    "hunter2", "letmein", "abc123",
    "changeme", "changeit", "unknown", "optional", "enabled", "disabled", "string", "random", "token");

  static final List<String> PLACEHOLDER_SAMPLES = List.of(
    "__api_key__",                        // double-underscore-wrapped
    "TODO: fill in", "FIXME: fill in",   // code-reminder prefix
    "${env_var}", "value-${env_var}",     // variable interpolation
    "#{{db_host}}",                       // hash-brace interpolation
    "((vault_ref))",                      // Concourse vars
    "$(get_key)",                         // shell command substitution
    "`get_key`",                          // backtick command substitution
    "$MY_VAR",                            // bare variable reference
    "{db_host}", "%{db_host}",            // template interpolation
    "{{db_host}}",                        // double-brace interpolation
    "System.getenv(\"DB_HOST\")",         // env access
    "process.env.HOST",                   // Node.js process.env
    "%GITHUB_TOKEN%",                     // %VAR% syntax
    "config['db_url']",                   // config access
    "Read-Host",                          // PowerShell
    "<db-host>",                          // short angle-bracket placeholder
    "<api_endpoint>",                     // long angle-bracket placeholder
    "(config_ref)",                       // parenthesised placeholder
    "[db_url]",                           // square-bracket placeholder
    "%(db_url)s",                         // Python format-string placeholder
    "@variables('host')");                // Azure Logic Apps expression

  static final List<String> ENCRYPTED_SAMPLES = List.of(
    "encrypted:YWJjZGVm",
    "{cipher}1e3faa2cdab2deae117dca102e52922a",
    "enc[QUJDRA==]",
    "ENC{QUJDRA==}", "%enc{QUJDRA==}", "ENC(QUJDRA==)");

  static final List<String> REFERENCE_SAMPLES = List.of(
    "op://vault/item/key",
    "VAULT[path/to/key access_token]");

  static final List<String> STRUCTURED_FORMAT_SAMPLES = List.of(
    "/var/keys/gsa-key.json",
    "v1.2.3", ">=1.0.0", "~1.4.5-alpha",                // semver variants
    "4.0.9(@types/node@22.13.4)");                       // peer-annotated lockfile version (non-semver)

  static final List<String> KNOWN_NON_SECRETS = Stream.of(
    FAKE_VALUE_SAMPLES, SECRET_SAMPLES, PLACEHOLDER_SAMPLES,
    ENCRYPTED_SAMPLES, REFERENCE_SAMPLES, STRUCTURED_FORMAT_SAMPLES)
    .flatMap(List::stream)
    .toList();

  static Stream<String> fakeValueSamples() {
    return FAKE_VALUE_SAMPLES.stream();
  }

  static Stream<String> secretSamples() {
    return SECRET_SAMPLES.stream();
  }

  static Stream<String> placeholderSamples() {
    return PLACEHOLDER_SAMPLES.stream();
  }

  static Stream<String> encryptedSamples() {
    return ENCRYPTED_SAMPLES.stream();
  }

  static Stream<String> referenceSamples() {
    return REFERENCE_SAMPLES.stream();
  }

  static Stream<String> structuredFormatSamples() {
    return STRUCTURED_FORMAT_SAMPLES.stream();
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

  @ParameterizedTest
  @ValueSource(strings = {
    "Xk9Lm2Qp7Rs4Tv1Wz0",
    "9f8e7d6c5b4a392817",
    "Tr0ub4dor&3xpl0!t",
    "__not_closed" // leading __ without closing __ should not match
  })
  void shouldNotClassifyRealisticTokensAsNonSecrets(String value) {
    assertThat(SecretClassifier.isKnownNonSecret(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // Credential words are matched only as whole values, so a value that merely contains one stays a candidate.
    "mytoken123",
    "this_should_remain_unknown"
  })
  void shouldNotExcludeValuesMerelyContainingCredentialWords(String value) {
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
