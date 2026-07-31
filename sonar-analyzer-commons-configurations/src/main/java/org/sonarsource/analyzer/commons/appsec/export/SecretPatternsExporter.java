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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

/**
 * Generates the machine-readable JSON export of {@link SecretClassifier}'s secret-exclusion patterns, the single
 * source of truth shared with non-JVM analyzers (SonarJS, sonar-dotnet, …).
 *
 * <p>The JVM classifier keeps its possessive quantifiers for performance; on export they are rewritten to plain
 * greedy quantifiers ({@code X++} &rarr; {@code X+}) so a single regex compiles in every consumer — .NET, JavaScript
 * and Swift. JavaScript supports neither possessive quantifiers nor atomic groups, so atomicity is dropped rather
 * than preserved as {@code (?>X+)}; this is match-preserving for the current patterns. See
 * {@link RegexTranslator#toPortableRegex(String)}.
 *
 * <p>Output is deterministic and pretty-printed so the artifact diffs cleanly. Run via {@code exec:java} at build time;
 * {@code main} takes the target file path as its single argument.
 */
public final class SecretPatternsExporter {

  private static final String DESCRIPTION = "Secret-exclusion patterns generated from SecretClassifier in " +
    "sonar-analyzer-commons. Do not edit by hand. Regexes are applied case-insensitively with \"find\" " +
    "(search-anywhere) semantics; exact values are matched in full, case-insensitively.";

  private static final Gson GSON = new GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();

  private SecretPatternsExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: SecretPatternsExporter <output-json-path>");
    }
    Path output = Paths.get(args[0]).toAbsolutePath();
    try {
      Files.createDirectories(output.getParent());
      Files.write(output, toJson().getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Builds the JSON document from the classifier's exported patterns, translating each regex to a .NET-portable form.
   */
  public static String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("description", DESCRIPTION);

    JsonObject regex = new JsonObject();
    regex.addProperty("semantics", "find");
    regex.addProperty("caseInsensitive", true);
    JsonObject exact = new JsonObject();
    exact.addProperty("caseInsensitive", true);
    JsonObject match = new JsonObject();
    match.add("regex", regex);
    match.add("exact", exact);
    root.add("match", match);

    JsonArray patternGroups = new JsonArray();
    for (SecretClassifier.PatternGroupView group : SecretClassifier.exportPatternGroups()) {
      JsonObject json = new JsonObject();
      json.addProperty("category", group.category());
      json.add("patterns", toArray(translate(group.regexes())));
      patternGroups.add(json);
    }
    root.add("patternGroups", patternGroups);

    JsonArray exactMatchGroups = new JsonArray();
    for (SecretClassifier.ExactMatchGroupView group : SecretClassifier.exportExactMatchGroups()) {
      JsonObject json = new JsonObject();
      json.addProperty("category", group.category());
      json.add("values", toArray(group.values()));
      exactMatchGroups.add(json);
    }
    root.add("exactMatchGroups", exactMatchGroups);

    return GSON.toJson(root) + "\n";
  }

  private static JsonArray toArray(List<String> values) {
    JsonArray array = new JsonArray();
    values.forEach(array::add);
    return array;
  }

  private static List<String> translate(List<String> regexes) {
    return regexes.stream().map(RegexTranslator::toPortableRegex).toList();
  }

  /** Visible for testing: confirms the exporter's output is a compilable Java regex too. */
  static Pattern compilePortable(String regex) {
    return Pattern.compile(RegexTranslator.toPortableRegex(regex), Pattern.CASE_INSENSITIVE);
  }

}
