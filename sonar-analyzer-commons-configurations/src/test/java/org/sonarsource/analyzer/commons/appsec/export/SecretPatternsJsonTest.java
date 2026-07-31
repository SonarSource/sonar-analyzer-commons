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

import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

import static org.assertj.core.api.Assertions.assertThat;

class SecretPatternsJsonTest {

  @Test
  void jsonShouldBeDeterministic() {
    assertThat(SecretPatternsExporter.toJson()).isEqualTo(SecretPatternsExporter.toJson());
  }

  @Test
  void jsonShouldDeclareMatchSemantics() throws ParseException {
    JSONObject root = parse();
    JSONObject match = (JSONObject) root.get("match");
    JSONObject regex = (JSONObject) match.get("regex");
    assertThat(regex)
      .containsEntry("semantics", "find")
      .containsEntry("caseInsensitive", Boolean.TRUE);
    assertThat((JSONObject) match.get("exact")).containsEntry("caseInsensitive", Boolean.TRUE);
    assertThat(root.get("description")).asString().contains("SecretClassifier");
  }

  @Test
  void patternGroupsShouldMirrorSecretClassifierWithPortableRegexes() throws ParseException {
    JSONArray groups = (JSONArray) parse().get("patternGroups");
    List<SecretClassifier.PatternGroupView> source = SecretClassifier.exportPatternGroups();
    assertThat(groups).hasSameSizeAs(source);
    for (int i = 0; i < source.size(); i++) {
      JSONObject group = (JSONObject) groups.get(i);
      SecretClassifier.PatternGroupView expected = source.get(i);
      assertThat(group).containsEntry("category", expected.category());
      JSONArray patterns = (JSONArray) group.get("patterns");
      assertThat(patterns).hasSameSizeAs(expected.regexes());
      for (int j = 0; j < expected.regexes().size(); j++) {
        assertThat(patterns.get(j)).isEqualTo(RegexTranslator.toPortableRegex(expected.regexes().get(j)));
      }
    }
  }

  @Test
  void exactMatchGroupsShouldMirrorSecretClassifier() throws ParseException {
    JSONArray groups = (JSONArray) parse().get("exactMatchGroups");
    List<SecretClassifier.ExactMatchGroupView> source = SecretClassifier.exportExactMatchGroups();
    assertThat(groups).hasSameSizeAs(source);
    for (int i = 0; i < source.size(); i++) {
      JSONObject group = (JSONObject) groups.get(i);
      SecretClassifier.ExactMatchGroupView expected = source.get(i);
      assertThat(group).containsEntry("category", expected.category());

      assertThat((JSONArray) group.get("values")).containsExactlyElementsOf(expected.values());
    }
  }

  private static JSONObject parse() throws ParseException {
    return (JSONObject) new JSONParser().parse(SecretPatternsExporter.toJson());
  }
}
