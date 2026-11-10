/*
 * SonarSource Analyzers Commons Configurations
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
package org.sonarsource.analyzer.commons.appsec.export;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.analyzer.commons.appsec.CleartextProtocolFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CleartextProtocolCorpusExporterTest {

  private static final Pattern INVALID_IDENTIFIER_CHARACTERS = Pattern.compile("[\\s\\\\]");

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("safeUrls")
  void safeUrl(String url) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("unsafeUrls")
  void unsafeUrl(String url) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isFalse();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("knownIdentifierUrls")
  void exportedPatternsShouldRecognizeIdentifiersWithoutMakingConnectionsSafe(String url) {
    assertThat(matchesIdentifier(url, exportedIdentifierPatterns())).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isFalse();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("notIdentifierUrls")
  void exportedPatternsShouldRejectNonIdentifiers(String url) {
    assertThat(matchesIdentifier(url, exportedIdentifierPatterns())).isFalse();
  }

  @Test
  void everyIdentifierAuthorityHasARepresentativeUrl() {
    var authorities = knownIdentifierUrls()
      .map(String::strip)
      .map(url -> url.substring("http://".length()).split("[/?#]", 2)[0].toLowerCase(Locale.ROOT))
      .collect(Collectors.toSet());
    assertThat(exportedIdentifierPatterns())
      .hasSize(29)
      .containsOnlyKeys(authorities);
  }

  static Stream<String> knownIdentifierUrls() {
    return exportedSamples("identifierUrls");
  }

  static Stream<String> notIdentifierUrls() {
    return exportedSamples("nonIdentifierUrls");
  }

  static Stream<String> safeUrls() {
    return exportedSamples("safeUrls");
  }

  static Stream<String> unsafeUrls() {
    return exportedSamples("unsafeUrls");
  }

  @Test
  void exportedExclusionsShouldCoverSafeUrlsExceptParserOnlyCases() {
    var patterns = exportedHostPatterns();
    var prefixes = exportedSafeUrlPrefixes();

    // These cases are safe because of URL parsing/scheme handling, not an exported exclusion.
    // The complete safety classification is also checked in the consuming analyzer's runtime.
    assertThat(exportedSamples("safeUrls").filter(url -> !matchesExclusion(url, patterns, prefixes)))
      .containsExactly("wss://acme.com", "ircs://acme.com",
        "http://local-kubernetes-hostname/something", "http://local-kubernetes-hostname:8080/something", "ws://my-service",
        "http://my-service/api", "https://insecure.com", "not-a-url", "");
  }

  @Test
  void exportedExclusionsShouldNotSuppressUnsafeUrls() {
    var patterns = exportedHostPatterns();
    var prefixes = exportedSafeUrlPrefixes();

    exportedSamples("unsafeUrls").forEach(url ->
      assertThat(matchesExclusion(url, patterns, prefixes))
        .as("unsafe URL suppressed by exported exclusions: <%s>", url)
        .isFalse());
  }

  @Test
  void everyExportedExclusionShouldBeExercisedByTheCorpus() {
    var urls = exportedSamples("safeUrls").map(String::strip).toList();
    for (Pattern pattern : exportedHostPatterns()) {
      assertThat(urls)
        .as("no corpus sample exercises exported host pattern: %s", pattern.pattern())
        .anyMatch(url -> pattern.matcher(authority(url)).find());
    }
    for (String prefix : exportedSafeUrlPrefixes()) {
      assertThat(urls)
        .as("no corpus sample exercises exported URL prefix: %s", prefix)
        .anyMatch(url -> url.toLowerCase(Locale.ROOT).startsWith(prefix));
    }
  }

  private static Stream<String> exportedSamples(String group) {
    return JsonParser.parseString(CleartextProtocolCorpusExporter.toJson()).getAsJsonObject()
      .getAsJsonArray(group).asList().stream().map(JsonElement::getAsString);
  }

  private static List<Pattern> exportedHostPatterns() {
    var exclusions = JsonParser.parseString(CleartextProtocolFilterExporter.toJson()).getAsJsonObject();
    var match = exclusions.getAsJsonObject("match").getAsJsonObject("hostPatterns");
    assertThat(match.get("semantics").getAsString()).isEqualTo("find");
    int flags = match.get("caseInsensitive").getAsBoolean() ? Pattern.CASE_INSENSITIVE : 0;
    return exclusions.getAsJsonObject("hostPatterns").entrySet().stream()
      .map(entry -> Pattern.compile(entry.getValue().getAsString(), flags)).toList();
  }

  private static List<String> exportedSafeUrlPrefixes() {
    var exclusions = JsonParser.parseString(CleartextProtocolFilterExporter.toJson()).getAsJsonObject();
    var match = exclusions.getAsJsonObject("match").getAsJsonObject("safeUrlPrefixes");
    assertThat(match.get("semantics").getAsString()).isEqualTo("prefix");
    assertThat(match.get("target").getAsString()).isEqualTo("fullUrl");
    assertThat(match.get("caseInsensitive").getAsBoolean()).isTrue();
    return exclusions.getAsJsonArray("safeUrlPrefixes").asList().stream()
      .map(value -> value.getAsString().toLowerCase(Locale.ROOT)).toList();
  }

  private static Map<String, Pattern> exportedIdentifierPatterns() {
    var exclusions = JsonParser.parseString(CleartextProtocolFilterExporter.toJson()).getAsJsonObject();
    var match = exclusions.getAsJsonObject("match").getAsJsonObject("identifierUrls");
    var authorityMatch = match.getAsJsonObject("authority");
    assertThat(authorityMatch.get("semantics").getAsString()).isEqualTo("exact");
    assertThat(authorityMatch.get("caseInsensitive").getAsBoolean()).isTrue();
    var patternMatch = match.getAsJsonObject("pattern");
    assertThat(patternMatch.get("semantics").getAsString()).isEqualTo("find");
    assertThat(patternMatch.get("target").getAsString()).isEqualTo("pathQueryFragment");
    int flags = patternMatch.get("caseInsensitive").getAsBoolean() ? Pattern.CASE_INSENSITIVE : 0;
    Map<String, Pattern> patterns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    exclusions.getAsJsonObject("identifierUrls").entrySet()
      .forEach(entry -> patterns.put(entry.getKey(), Pattern.compile(entry.getValue().getAsString(), flags)));
    return patterns;
  }

  private static boolean matchesIdentifier(String url, Map<String, Pattern> patterns) {
    var stripped = url.strip();
    if (!stripped.regionMatches(true, 0, "http://", 0, 7) || INVALID_IDENTIFIER_CHARACTERS.matcher(stripped).find()) {
      return false;
    }
    // Preserve raw authority and suffix: no port removal, path normalization or escape decoding.
    var parts = stripped.substring(7).split("(?=[/?#])", 2);
    var pattern = patterns.get(parts[0]);
    var suffix = parts.length == 1 ? "/" : parts[1];
    if (!suffix.startsWith("/")) {
      suffix = "/" + suffix;
    }
    return pattern != null && pattern.matcher(suffix).find();
  }

  private static boolean matchesExclusion(String url, List<Pattern> patterns, List<String> prefixes) {
    var stripped = url.strip();
    var lower = stripped.toLowerCase(Locale.ROOT);
    return prefixes.stream().anyMatch(lower::startsWith)
      || patterns.stream().anyMatch(pattern -> pattern.matcher(authority(stripped)).find());
  }

  private static String authority(String url) {
    // Extract the corpus's raw authority, including template ports and noncanonical IP spellings.
    // This tests the exported exclusions without duplicating the filter's full URI parsing logic.
    int schemeEnd = url.indexOf("://");
    if (schemeEnd < 0) {
      return "";
    }
    var authority = url.substring(schemeEnd + 3).split("[/?#]", 2)[0];
    return authority.substring(authority.lastIndexOf('@') + 1);
  }

  @Test
  void jsonShouldExposeNonEmptySampleGroups() {
    JsonObject corpus = JsonParser.parseString(CleartextProtocolCorpusExporter.toJson()).getAsJsonObject();
    assertThat(corpus.keySet()).containsExactly("identifierUrls", "nonIdentifierUrls", "safeUrls", "unsafeUrls");
    for (String group : corpus.keySet()) {
      assertThat(corpus.getAsJsonArray(group).asList()).isNotEmpty();
    }
  }

  @Test
  void mainShouldWriteJsonToTheGivenPath(@TempDir Path tempDir) throws IOException {
    Path output = tempDir.resolve("generated-resources").resolve("cleartext-protocol-corpus.json");

    CleartextProtocolCorpusExporter.main(new String[] {output.toString()});

    assertThat(Files.readString(output)).isEqualTo(CleartextProtocolCorpusExporter.toJson());
  }

  @Test
  void mainShouldFailWhenNoOutputPathIsGiven() {
    assertThatThrownBy(() -> CleartextProtocolCorpusExporter.main(new String[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Usage");
  }
}
