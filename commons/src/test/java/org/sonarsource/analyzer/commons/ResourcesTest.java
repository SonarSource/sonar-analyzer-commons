/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons;

import java.io.IOException;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {

  @Test
  public void read_resource() throws Exception {
    assertThat(Resources.toString("org/sonarsource/analyzer/commons/ResourcesTest.txt", UTF_8)).isEqualTo("hello" + System.lineSeparator());
  }

  @Test
  public void read_resource_with_absolute() throws Exception {
    assertThat(Resources.toString("/org/sonarsource/analyzer/commons/ResourcesTest.txt", UTF_8)).isEqualTo("hello" + System.lineSeparator());
  }

  @Test(expected = IOException.class)
  public void read_invalid_resource() throws Exception {
    Resources.toString("invalid/path.txt", UTF_8);
  }

}
