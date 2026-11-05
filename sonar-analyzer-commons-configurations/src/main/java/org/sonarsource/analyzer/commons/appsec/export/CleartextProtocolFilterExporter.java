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

import com.google.gson.JsonObject;
import java.util.TreeMap;
import org.sonarsource.analyzer.commons.appsec.CleartextProtocolFilter;

/**
 * Generates the cleartext URL exclusions and identifier inventory shared with non-JVM analyzers.
 */
public final class CleartextProtocolFilterExporter {

  private static final String SEMANTICS = "semantics";
  private static final String CASE_INSENSITIVE = "caseInsensitive";

  private CleartextProtocolFilterExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: CleartextProtocolFilterExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  /**
   * Converts the Java exclusions to JSON, translating regexes and sorting maps and sets for stable output.
   * For example, an inventory entry for {@code identifier.invalid} with pattern {@code ^/schema}
   * becomes this entry in the {@code identifierUrls} object:
   * <pre>{@code
   * {
   *   "identifier.invalid": "^/schema"
   * }
   * }</pre>
   * The caller writes the complete document to {@code cleartext-protocol-exclusions.json}.
   */
  public static String toJson() {
    JsonObject root = new JsonObject();

    JsonObject hostMatch = new JsonObject();
    hostMatch.addProperty(SEMANTICS, "find");
    hostMatch.addProperty(CASE_INSENSITIVE, true);
    JsonObject prefixMatch = new JsonObject();
    prefixMatch.addProperty(SEMANTICS, "prefix");
    prefixMatch.addProperty(CASE_INSENSITIVE, true);
    prefixMatch.addProperty("target", "fullUrl");
    JsonObject identifierAuthorityMatch = new JsonObject();
    identifierAuthorityMatch.addProperty(SEMANTICS, "exact");
    identifierAuthorityMatch.addProperty(CASE_INSENSITIVE, true);
    JsonObject identifierPatternMatch = new JsonObject();
    identifierPatternMatch.addProperty(SEMANTICS, "find");
    identifierPatternMatch.addProperty(CASE_INSENSITIVE, false);
    identifierPatternMatch.addProperty("target", "pathQueryFragment");
    JsonObject identifierMatch = new JsonObject();
    identifierMatch.add("authority", identifierAuthorityMatch);
    identifierMatch.add("pattern", identifierPatternMatch);
    JsonObject match = new JsonObject();
    match.add("hostPatterns", hostMatch);
    match.add("safeUrlPrefixes", prefixMatch);
    match.add("identifierUrls", identifierMatch);
    root.add("match", match);

    root.add("cleartextProtocols", JsonExports.toArray(CleartextProtocolFilter.getCleartextProtocols().stream().sorted().toList()));

    JsonObject hostPatterns = new JsonObject();
    new TreeMap<>(CleartextProtocolFilter.exportHostPatterns())
      .forEach((name, pattern) -> hostPatterns.addProperty(name, RegexTranslator.toPortableRegex(pattern.pattern())));
    root.add("hostPatterns", hostPatterns);

    root.add("safeUrlPrefixes", JsonExports.toArray(CleartextProtocolFilter.exportSafeUrlPrefixes().stream().sorted().toList()));

    JsonObject identifierUrls = new JsonObject();
    new TreeMap<>(CleartextProtocolFilter.exportIdentifierUrls())
      .forEach((authority, pattern) -> identifierUrls.addProperty(authority, RegexTranslator.toPortableRegex(pattern)));
    root.add("identifierUrls", identifierUrls);

    return JsonExports.render(root);
  }
}
