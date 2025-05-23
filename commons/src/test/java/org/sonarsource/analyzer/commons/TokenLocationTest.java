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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenLocationTest {

  @Test
  public void single_line_token() throws Exception {
    TokenLocation location = new TokenLocation(42, 5, "abc");
    assertThat(location.startLine()).isEqualTo(42);
    assertThat(location.startLineOffset()).isEqualTo(5);
    assertThat(location.endLine()).isEqualTo(42);
    assertThat(location.endLineOffset()).isEqualTo(8);
  }

  @Test
  public void two_lines() throws Exception {
    TokenLocation location1 = new TokenLocation(42, 5, "ab\rc");
    assertThat(location1.endLine()).isEqualTo(43);
    assertThat(location1.endLineOffset()).isEqualTo(1);

    TokenLocation location2 = new TokenLocation(42, 5, "ab\nc");
    assertThat(location2.endLine()).isEqualTo(43);
    assertThat(location2.endLineOffset()).isEqualTo(1);

    TokenLocation location3 = new TokenLocation(42, 5, "ab\r\nc");
    assertThat(location3.endLine()).isEqualTo(43);
    assertThat(location3.endLineOffset()).isEqualTo(1);
  }

  @Test
  public void multiple_line_feeds() throws Exception {
    TokenLocation location = new TokenLocation(42, 5, "ab\nc\ndefg");
    assertThat(location.endLine()).isEqualTo(44);
    assertThat(location.endLineOffset()).isEqualTo(4);
  }

}
