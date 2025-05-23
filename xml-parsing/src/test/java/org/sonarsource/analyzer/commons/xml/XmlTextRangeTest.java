/*
 * SonarSource Analyzers XML Parsing Commons
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
package org.sonarsource.analyzer.commons.xml;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlTextRangeTest {

  @Test(expected = IllegalArgumentException.class)
  public void testWrongLines() throws Exception {
    new XmlTextRange(42, 0, 1,0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWrongColumns() throws Exception {
    new XmlTextRange(2, 10, 2,5);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyRange() throws Exception {
    new XmlTextRange(2, 10, 2,10);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalLine() throws Exception {
    new XmlTextRange(0, 10, 2,10);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalColumn() throws Exception {
    new XmlTextRange(1, 5, 2,-2);
  }

  @Test
  public void testTextRangeBasedCreation() throws Exception {
    XmlTextRange start = new XmlTextRange(1, 2, 3, 4);
    XmlTextRange end = new XmlTextRange(5, 6, 7, 8);

    XmlTextRange range = new XmlTextRange(start, end);
    assertThat(range)
      .extracting("startLine", "startColumn", "endLine", "endColumn")
      .containsExactly(1, 2, 7, 8);
  }

  @Test
  public void testPositionBasedCreation() throws Exception {
    XmlFilePosition startContentPosition = new XmlFilePosition("__abcde");
    XmlFilePosition start = startContentPosition.shift(2);
    XmlFilePosition end = start.shift(3);

    // abc
    XmlTextRange range = new XmlTextRange(start, end, startContentPosition);
    assertThat(range)
      .extracting("startLine", "startColumn", "endLine", "endColumn")
      .containsExactly(1, 2, 1, 5);

    XmlTextRange startAsRange = new XmlTextRange(1, 2, 1, 4);
    range = new XmlTextRange(startAsRange, end, startContentPosition);
    assertThat(range)
      .extracting("startLine", "startColumn", "endLine", "endColumn")
      .containsExactly(1, 2, 1, 5);
  }
}
