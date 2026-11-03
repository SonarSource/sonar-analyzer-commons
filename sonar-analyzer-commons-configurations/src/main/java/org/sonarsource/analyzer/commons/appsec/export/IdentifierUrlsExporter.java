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
 * Generates the identifier URL inventory shared with non-JVM analyzers.
 */
public final class IdentifierUrlsExporter {

  private IdentifierUrlsExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: IdentifierUrlsExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  /**
   * Converts the Java identifier inventory to JSON text, with entries sorted by domain.
   * Example output for an empty inventory:
   * <pre>{@code
   * {
   * }
   * }</pre>
   * The caller writes the returned text to {@code identifier-urls.json}.
   */
  public static String toJson() {
    JsonObject root = new JsonObject();
    new TreeMap<>(CleartextProtocolFilter.exportIdentifierUrls()).forEach(root::addProperty);
    return JsonExports.render(root);
  }
}
