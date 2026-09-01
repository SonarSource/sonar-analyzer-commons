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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class JsonExports {

  /**
   * Pretty-printed so the generated artifacts diff cleanly, with HTML escaping disabled so regex metacharacters stay
   * readable rather than being emitted as {@code <} escapes.
   */
  static final Gson GSON = new GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();

  private JsonExports() {
  }

  static JsonArray toArray(List<String> values) {
    JsonArray array = new JsonArray();
    values.forEach(array::add);
    return array;
  }

  static void write(String path, String content) {
    Path output = Paths.get(path).toAbsolutePath();
    try {
      Files.createDirectories(output.getParent());
      Files.write(output, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
