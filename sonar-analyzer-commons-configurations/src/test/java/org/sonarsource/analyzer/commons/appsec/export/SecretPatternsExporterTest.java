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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the exporter's own responsibilities: turning {@link org.sonarsource.analyzer.commons.appsec.SecretClassifier}
 * into a well-formed, portable JSON document and writing it to disk. The regex-translation behaviour lives in
 * {@link RegexTranslator} and is exercised by {@code RegexTranslatorTest}; the JSON-content mirroring is covered by
 * {@code SecretPatternsJsonTest}.
 */
class SecretPatternsExporterTest {

  @Test
  void mainShouldWriteJsonToTheGivenPathCreatingMissingParents(@TempDir Path tempDir) throws IOException {
    // The parent directory does not exist yet, so main() must create it (as it does under target/ at build time).
    Path output = tempDir.resolve("generated-resources").resolve("secret-patterns.json");

    SecretPatternsExporter.main(new String[] {output.toString()});

    assertThat(output).exists();
    assertThat(Files.readString(output)).isEqualTo(SecretPatternsExporter.toJson());
  }

  @Test
  void mainShouldFailWhenNoOutputPathIsGiven() {
    assertThatThrownBy(() -> SecretPatternsExporter.main(new String[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Usage");
  }

  @Test
  void jsonShouldBeWellFormedAndPrettyPrinted() throws ParseException {
    String json = SecretPatternsExporter.toJson();

    // Parses back to a single JSON object.
    assertThat(new JSONParser().parse(json)).isInstanceOf(JSONObject.class);
    // Deterministic artifact that diffs cleanly: pretty-printed (indented, multi-line) and newline-terminated.
    assertThat(json)
      .startsWith("{\n")
      .contains("\n  \"")
      .endsWith("}\n");
    assertThat(json.lines().count()).isGreaterThan(1);
  }

  @Test
  void jsonShouldExposeTheExpectedTopLevelStructure() throws ParseException {
    JSONObject root = parse();

    assertThat(root).containsOnlyKeys("description", "match", "patternGroups", "exactMatchGroups");
    assertThat(root.get("description")).asString().contains("Do not edit by hand");
    assertThat((JSONArray) root.get("patternGroups")).isNotEmpty();
    assertThat((JSONArray) root.get("exactMatchGroups")).isNotEmpty();
  }

  @Test
  void everyExportedPatternShouldBeFullyTranslatedAndCompile() throws ParseException {
    JSONArray patternGroups = (JSONArray) parse().get("patternGroups");

    assertThat(patternGroups).isNotEmpty();
    for (Object groupObj : patternGroups) {
      JSONObject group = (JSONObject) groupObj;
      assertThat(group).containsKeys("category", "patterns");
      for (Object patternObj : (JSONArray) group.get("patterns")) {
        String pattern = (String) patternObj;
        // The emitted regex is already portable: re-translating it is a no-op (fixed point).
        assertThat(RegexTranslator.toPortableRegex(pattern))
          .as("exported pattern is not fully translated: %s", pattern)
          .isEqualTo(pattern);
        // ...and it is a regex the .NET-portable form still compiles as Java too.
        assertThat(SecretPatternsExporter.compilePortable(pattern))
          .as("exported pattern does not compile: %s", pattern)
          .isNotNull();
      }
    }
  }

  private static JSONObject parse() throws ParseException {
    return (JSONObject) new JSONParser().parse(SecretPatternsExporter.toJson());
  }
}
