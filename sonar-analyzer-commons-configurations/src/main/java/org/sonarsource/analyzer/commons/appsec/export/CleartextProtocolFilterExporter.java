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

  private CleartextProtocolFilterExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: CleartextProtocolFilterExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  /**
   * Converts the Java exclusions to JSON, sorting maps and sets for stable output.
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
    root.add("cleartextProtocols", JsonExports.toArray(CleartextProtocolFilter.getCleartextProtocols().stream().sorted().toList()));

    JsonObject hostPatterns = new JsonObject();
    new TreeMap<>(CleartextProtocolFilter.exportHostPatterns())
      .forEach((name, pattern) -> hostPatterns.addProperty(name, pattern.pattern()));
    root.add("hostPatterns", hostPatterns);

    root.add("safeUrlPrefixes", JsonExports.toArray(CleartextProtocolFilter.exportSafeUrlPrefixes().stream().sorted().toList()));

    JsonObject identifierUrls = new JsonObject();
    new TreeMap<>(CleartextProtocolFilter.exportIdentifierUrls()).forEach(identifierUrls::addProperty);
    root.add("identifierUrls", identifierUrls);

    return JsonExports.render(root);
  }
}
