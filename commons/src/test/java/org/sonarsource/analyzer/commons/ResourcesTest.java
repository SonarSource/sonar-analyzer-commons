/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {

  @Test
  public void read_resource() throws Exception {
    assertThat(Resources.toString("org/sonarsource/analyzer/commons/ResourcesTest.txt", UTF_8)).isEqualTo("hello\n");
  }

  @Test
  public void read_resource_with_provider() throws Exception {
    assertThat(Resources.toString(
      Resources.class.getClassLoader()::getResourceAsStream,
      "org/sonarsource/analyzer/commons/ResourcesTest.txt",
      UTF_8)).isEqualTo("hello\n");
  }

  @Test
  public void read_resource_with_provider_helper() throws Exception {
    Function<String, InputStream> provider = Resources.resourceProvider(Resources.class);
    assertThat(Resources.toString(
      provider,
      "/org/sonarsource/analyzer/commons/ResourcesTest.txt",
      UTF_8)).isEqualTo("hello\n");
  }

  @Test
  public void resource_provider_can_return_null() throws Exception {
    Function<String, InputStream> provider = Resources.resourceProvider(Resources.class);
    assertThat(provider.apply("omvalid/path.txt")).isNull();
  }

  @Test(expected = IOException.class)
  public void read_invalid_resource() throws Exception {
    Resources.toString("invalid/path.txt", UTF_8);
  }

}
