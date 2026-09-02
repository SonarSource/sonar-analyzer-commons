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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the corpus export itself, and - more importantly - that the two published artifacts agree: the patterns in
 * {@code secret-patterns.json} must reproduce the classification recorded in {@code secret-exclusion-corpus.json}
 * <em>after</em> translation to their portable form, which is the form every consumer actually compiles.
 */
class SecretExclusionCorpusExporterTest {

  @Test
  void mainShouldWriteJsonToTheGivenPathCreatingMissingParents(@TempDir Path tempDir) throws IOException {
    // The parent directory does not exist yet, so main() must create it (as it does under target/ at build time).
    Path output = tempDir.resolve("generated-resources").resolve("secret-exclusion-corpus.json");

    SecretExclusionCorpusExporter.main(new String[] {output.toString()});

    assertThat(output).exists();
    assertThat(Files.readString(output)).isEqualTo(SecretExclusionCorpusExporter.toJson());
  }

  @Test
  void mainShouldFailWhenNoOutputPathIsGiven() {
    assertThatThrownBy(() -> SecretExclusionCorpusExporter.main(new String[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Usage");
  }

  @Test
  void jsonShouldBeDeterministic() {
    assertThat(SecretExclusionCorpusExporter.toJson()).isEqualTo(SecretExclusionCorpusExporter.toJson());
  }

  @Test
  void jsonShouldBeWellFormedAndPrettyPrinted() throws ParseException {
    String json = SecretExclusionCorpusExporter.toJson();

    assertThat(new JSONParser().parse(json)).isInstanceOf(JSONObject.class);
    assertThat(json)
      .startsWith("{\n")
      .contains("\n  \"")
      .endsWith("}\n");
  }

  @Test
  void jsonShouldExposeTheExpectedTopLevelStructure() throws ParseException {
    JSONObject root = parseCorpus();

    assertThat(root).containsOnlyKeys("description", "knownNonSecrets", "secretCandidates");
    assertThat(root.get("description")).asString().contains("Do not edit by hand");
    assertThat((JSONArray) root.get("knownNonSecrets")).isNotEmpty();
    assertThat((JSONArray) root.get("secretCandidates")).isNotEmpty();
  }

  @Test
  void knownNonSecretsShouldMirrorSecretClassifierSamplesWithTheirCategory() throws ParseException {
    List<JSONObject> expected = new ArrayList<>();
    for (SecretClassifier.Category category : SecretClassifier.Category.values()) {
      for (String value : SecretClassifier.exportKnownNonSecretSamples(category)) {
        JSONObject sample = new JSONObject();
        sample.put("value", value);
        sample.put("category", category.name());
        expected.add(sample);
      }
    }

    assertThat((JSONArray) parseCorpus().get("knownNonSecrets")).containsExactlyElementsOf(expected);
  }

  @Test
  void secretCandidatesShouldMirrorSecretClassifier() throws ParseException {
    assertThat((JSONArray) parseCorpus().get("secretCandidates"))
      .containsExactlyElementsOf(SecretClassifier.exportSecretCandidateSamples());
  }

  @Test
  void everyExportedPatternAndExactValueShouldBeExercisedByTheCorpus() throws ParseException {
    List<String> knownNonSecrets = knownNonSecretValues();

    for (Pattern pattern : exportedPatterns()) {
      assertThat(knownNonSecrets)
        .as("no corpus sample exercises exported pattern: %s", pattern.pattern())
        .anyMatch(sample -> pattern.matcher(sample).find());
    }
    for (String exact : exportedExactValues()) {
      assertThat(knownNonSecrets)
        .as("no corpus sample exercises exported exact value: %s", exact)
        .anyMatch(sample -> sample.equalsIgnoreCase(exact));
    }
  }

  /**
   * The contract consumers rely on, checked against the exported (portable) regexes rather than the classifier's own:
   * a translation that changed a match would otherwise only surface in a downstream repository.
   */
  @Test
  void exportedPatternsShouldReproduceTheCorpusClassification() throws ParseException {
    List<Pattern> patterns = exportedPatterns();
    List<String> exactValues = exportedExactValues();

    for (String sample : knownNonSecretValues()) {
      assertThat(isSuppressed(sample, patterns, exactValues))
        .as("corpus known non-secret is not suppressed by the exported patterns: <%s>", sample)
        .isTrue();
    }
    for (Object candidate : (JSONArray) parseCorpus().get("secretCandidates")) {
      String sample = (String) candidate;
      assertThat(isSuppressed(sample, patterns, exactValues))
        .as("corpus secret candidate is suppressed by the exported patterns: <%s>", sample)
        .isFalse();
    }
  }

  private static boolean isSuppressed(String sample, List<Pattern> patterns, List<String> exactValues) {
    return exactValues.stream().anyMatch(sample::equalsIgnoreCase)
      || patterns.stream().anyMatch(pattern -> pattern.matcher(sample).find());
  }

  private static List<String> knownNonSecretValues() throws ParseException {
    List<String> values = new ArrayList<>();
    for (Object sample : (JSONArray) parseCorpus().get("knownNonSecrets")) {
      values.add((String) ((JSONObject) sample).get("value"));
    }
    return values;
  }

  /** The patterns exactly as published, compiled the way the export declares they must be applied. */
  private static List<Pattern> exportedPatterns() throws ParseException {
    List<Pattern> patterns = new ArrayList<>();
    for (Object group : (JSONArray) parsePatterns().get("patternGroups")) {
      for (Object pattern : (JSONArray) ((JSONObject) group).get("patterns")) {
        patterns.add(Pattern.compile((String) pattern, Pattern.CASE_INSENSITIVE));
      }
    }
    return patterns;
  }

  private static List<String> exportedExactValues() throws ParseException {
    List<String> values = new ArrayList<>();
    for (Object group : (JSONArray) parsePatterns().get("exactMatchGroups")) {
      for (Object value : (JSONArray) ((JSONObject) group).get("values")) {
        values.add((String) value);
      }
    }
    return values;
  }

  private static JSONObject parseCorpus() throws ParseException {
    return (JSONObject) new JSONParser().parse(SecretExclusionCorpusExporter.toJson());
  }

  private static JSONObject parsePatterns() throws ParseException {
    return (JSONObject) new JSONParser().parse(SecretPatternsExporter.toJson());
  }
}
