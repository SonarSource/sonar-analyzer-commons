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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.analyzer.commons.appsec.CleartextProtocolFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CleartextProtocolFilterExporterTest {

  @Test
  void jsonShouldMatchAllJavaExclusions() {
    JsonObject root = JsonParser.parseString(CleartextProtocolFilterExporter.toJson()).getAsJsonObject();

    assertThat(root.keySet()).containsExactly("cleartextProtocols", "hostPatterns", "safeUrlPrefixes", "identifierUrls");
    assertThat(root.getAsJsonArray("cleartextProtocols").asList().stream().map(JsonElement::getAsString))
      .containsExactlyElementsOf(CleartextProtocolFilter.getCleartextProtocols().stream().sorted().toList());
    assertThat(root.getAsJsonArray("safeUrlPrefixes").asList().stream().map(JsonElement::getAsString))
      .containsExactlyElementsOf(CleartextProtocolFilter.exportSafeUrlPrefixes().stream().sorted().toList());

    JsonObject hostPatterns = root.getAsJsonObject("hostPatterns");
    assertThat(hostPatterns.keySet()).containsExactly("documentation", "internal", "namespace");
    CleartextProtocolFilter.exportHostPatterns().forEach((name, pattern) -> {
      assertThat(hostPatterns.get(name).getAsString()).isEqualTo(pattern.pattern());
      assertThat(pattern.flags()).isEqualTo(Pattern.CASE_INSENSITIVE);
    });

    JsonObject identifiers = root.getAsJsonObject("identifierUrls");
    assertThat(identifiers.keySet())
      .hasSize(29)
      .containsExactlyElementsOf(CleartextProtocolFilter.exportIdentifierUrls().keySet().stream().sorted().toList());
    CleartextProtocolFilter.exportIdentifierUrls()
      .forEach((authority, pattern) -> assertThat(identifiers.get(authority).getAsString()).isEqualTo(pattern));
  }

  @Test
  void mainShouldWriteJsonToTheGivenPath(@TempDir Path tempDir) throws IOException {
    Path output = tempDir.resolve("generated-resources").resolve("cleartext-protocol-exclusions.json");

    CleartextProtocolFilterExporter.main(new String[] {output.toString()});

    assertThat(Files.readString(output)).isEqualTo(CleartextProtocolFilterExporter.toJson());
  }

  @Test
  void mainShouldFailWhenNoOutputPathIsGiven() {
    assertThatThrownBy(() -> CleartextProtocolFilterExporter.main(new String[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Usage");
  }
}
