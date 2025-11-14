/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.checks.verifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileContentTest {

  public static final Path MAIN_JS = Paths.get("src/test/resources/main.js");

  @Test
  public void constructor() {
    FileContent file = new FileContent(MAIN_JS);
    assertThat(file.getName()).isEqualTo("main.js");
    assertThat(file.getPath()).isEqualTo(MAIN_JS);
    assertThat(file.getFile()).isEqualTo(MAIN_JS.toFile());
    assertThat(file.getContent()).startsWith("function main()");
    assertThat(file.getLines()).containsExactly(
      "function main() {",
      "  alert('Hello'); // Noncompliant",
      "}",
      "");
  }

  @Test
  public void invalid_file_path() {
    Path path = Paths.get("bad/invalid.js");

    assertThatThrownBy(() -> { new FileContent(path); })
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("Failed to read '" + path.toString() + "':");
  }

}
