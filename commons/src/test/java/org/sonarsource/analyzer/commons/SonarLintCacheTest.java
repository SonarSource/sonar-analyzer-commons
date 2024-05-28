/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class SonarLintCacheTest {
  @Test
  public void read_non_existing_key() {
    var sonarLintCache = new SonarLintCache();
    assertThatThrownBy(() -> sonarLintCache.read("foo")).hasMessage("SonarLintCache does not contain key \"foo\"");
  }

  @Test
  public void write_and_read_existing_key() throws IOException {
    var sonarLintCache = new SonarLintCache();
    byte[] bytes = {42};
    sonarLintCache.write("foo", bytes);
    try (var value = sonarLintCache.read("foo")) {
      assertThat(value.readAllBytes()).isEqualTo(bytes);
    }

    sonarLintCache.write("bar", new ByteArrayInputStream(bytes));
    try (var value = sonarLintCache.read("bar")) {
      assertThat(value.readAllBytes()).isEqualTo(bytes);
    }
  }

  @Test
  public void contains() {
    var sonarLintCache = new SonarLintCache();
    assertThat(sonarLintCache.contains("foo")).isFalse();
    byte[] bytes = {42};
    sonarLintCache.write("foo", bytes);
    assertThat(sonarLintCache.contains("foo")).isTrue();
    assertThat(sonarLintCache.contains("bar")).isFalse();
  }

  @Test
  public void write_non_valid_input_stream() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Mockito.when(inputStream.readAllBytes()).thenThrow(IOException.class);

    SonarLintCache sonarLintCache = new SonarLintCache();
    assertThatThrownBy(() -> sonarLintCache.write("foo", inputStream)).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IOException.class);
  }

  @Test
  public void write_same_key() {
    var sonarLintCache = new SonarLintCache();
    byte[] bytes1 = {42};
    byte[] bytes2 = {0, 1, 2};
    sonarLintCache.write("foo", bytes1);
    assertThatThrownBy(() -> sonarLintCache.write("foo", bytes2)).hasMessage("Same key cannot be written to multiple times (foo)");
    assertThatThrownBy(() -> sonarLintCache.write("foo", new ByteArrayInputStream(bytes2))).hasMessage("Same key cannot be written to multiple times (foo)");
  }

  @Test
  public void copy_from_previous() {
    var sonarLintCache = new SonarLintCache();
    assertThatThrownBy(() -> sonarLintCache.copyFromPrevious("foo")).hasMessage("SonarLintCache does not allow to copy from previous.");
  }
}
