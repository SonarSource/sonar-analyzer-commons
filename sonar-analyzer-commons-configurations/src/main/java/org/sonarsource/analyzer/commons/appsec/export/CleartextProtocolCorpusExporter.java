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

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Exports the regression resource shared with the Commons filter tests.
 * This module's tests verify the samples against the Java filter before they are exported.
 */
public final class CleartextProtocolCorpusExporter {

  private CleartextProtocolCorpusExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: CleartextProtocolCorpusExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  public static String toJson() {
    try (var reader = new InputStreamReader(Objects.requireNonNull(CleartextProtocolCorpusExporter.class
      .getResourceAsStream("/org/sonarsource/analyzer/commons/appsec/cleartext-protocol-corpus.json")), StandardCharsets.UTF_8)) {
      return JsonExports.render(JsonParser.parseReader(reader).getAsJsonObject());
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read the shared cleartext protocol corpus", e);
    }
  }
}
