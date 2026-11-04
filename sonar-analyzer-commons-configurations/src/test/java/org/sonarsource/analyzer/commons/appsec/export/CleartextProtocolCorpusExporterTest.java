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
import java.util.Locale;
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
  void knownIdentifierDoesNotMakeAConnectionSafe(String url) {
    assertThat(CleartextProtocolFilter.isKnownIdentifier(url)).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isFalse();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("notIdentifierUrls")
  void notIdentifierUrl(String url) {
    assertThat(CleartextProtocolFilter.isKnownIdentifier(url)).isFalse();
  }

  @Test
  void everyIdentifierAuthorityHasARepresentativeUrl() {
    var authorities = knownIdentifierUrls()
      .map(String::strip)
      .map(url -> url.substring("http://".length()).split("[/?#]", 2)[0].toLowerCase(Locale.ROOT))
      .collect(Collectors.toSet());
    assertThat(CleartextProtocolFilter.exportIdentifierUrls())
      .hasSize(29)
      .containsOnlyKeys(authorities);
  }

  static Stream<String> knownIdentifierUrls() {
    return CleartextProtocolCorpusExporter.URL_SAMPLES.get("identifierUrls").stream();
  }

  static Stream<String> notIdentifierUrls() {
    return CleartextProtocolCorpusExporter.URL_SAMPLES.get("nonIdentifierUrls").stream();
  }

  static Stream<String> safeUrls() {
    return CleartextProtocolCorpusExporter.URL_SAMPLES.get("safeUrls").stream();
  }

  static Stream<String> unsafeUrls() {
    return CleartextProtocolCorpusExporter.URL_SAMPLES.get("unsafeUrls").stream();
  }

  @Test
  void jsonShouldMatchTheJavaSamples() {
    JsonObject corpus = JsonParser.parseString(CleartextProtocolCorpusExporter.toJson()).getAsJsonObject();
    assertThat(corpus.keySet()).containsExactly("identifierUrls", "nonIdentifierUrls", "safeUrls", "unsafeUrls");
    CleartextProtocolCorpusExporter.URL_SAMPLES.forEach((group, samples) -> {
      assertThat(samples).isNotEmpty();
      assertThat(corpus.getAsJsonArray(group).asList().stream().map(JsonElement::getAsString))
        .containsExactlyElementsOf(samples);
    });
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
