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
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.analyzer.commons.appsec.CleartextProtocolFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierUrlsExporterTest {

  @Test
  void jsonShouldMatchTheEmptyInventory() {
    JsonObject inventory = JsonParser.parseString(IdentifierUrlsExporter.toJson()).getAsJsonObject();

    assertThat(inventory.keySet()).isEmpty();
    assertThat(CleartextProtocolFilter.exportIdentifierUrls()).isEmpty();
  }

  @Test
  void mainShouldWriteJsonToTheGivenPath(@TempDir Path tempDir) throws IOException {
    Path output = tempDir.resolve("generated-resources").resolve("identifier-urls.json");

    IdentifierUrlsExporter.main(new String[] {output.toString()});

    assertThat(Files.readString(output)).isEqualTo(IdentifierUrlsExporter.toJson());
  }

  @Test
  void mainShouldFailWhenNoOutputPathIsGiven() {
    assertThatThrownBy(() -> IdentifierUrlsExporter.main(new String[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Usage");
  }
}
