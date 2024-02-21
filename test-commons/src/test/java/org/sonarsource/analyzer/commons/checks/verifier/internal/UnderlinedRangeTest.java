/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnderlinedRangeTest {

  @Test
  public void constructor() {
    UnderlinedRange range = new UnderlinedRange(1, 2, 3, 4);
    assertThat(range).hasToString("(1:2,3:4)");
  }

  @Test
  public void comparison() {
    UnderlinedRange a;
    UnderlinedRange b;

    a = new UnderlinedRange(100, 5, 110, 10);
    b = new UnderlinedRange(100, 5, 110, 10);
    assertThat(a)
      .isEqualTo(b)
      .isNotEqualTo(new Object())
      .isEqualByComparingTo(b)
      .hasSameHashCodeAs(b);
    assertThat(b)
      .isEqualTo(a)
      .isEqualByComparingTo(a);

    b = new UnderlinedRange(101, 5, 110, 10);
    assertThat(a)
      .isLessThan(b)
      .isNotEqualTo(b);
    assertThat(b)
      .isGreaterThan(a)
      .isNotEqualTo(a);
    assertThat(a.hashCode())
      .isNotEqualTo(b.hashCode());

    b = new UnderlinedRange(100, 7, 110, 10);
    assertThat(a)
      .isLessThan(b);
    assertThat(b)
      .isGreaterThan(a);

    b = new UnderlinedRange(100, 5, 120, 10);
    assertThat(a)
      .isLessThan(b);
    assertThat(b)
      .isGreaterThan(a);

    b = new UnderlinedRange(100, 5, 110, 15);
    assertThat(a)
      .isLessThan(b);
    assertThat(b)
      .isGreaterThan(a);
  }

  @Test
  public void underline() {
    assertUnderline(0, new UnderlinedRange(1, 1, 1, 2),
      "",
      "^^");

    UnderlinedRange range = new UnderlinedRange(5, 10, 5, 15);

    assertUnderline(0, range,
      "",
      "         ^^^^^^");

    assertUnderline(2,range,
      "XX   ^^^",
      "XX   ^^^   ^^^^^^");

    assertUnderline(2,range,
      "XX     ^^^",
      "XX     ^^^ ^^^^^^");

    assertUnderline(2,range,
      "XX     ^^^<",
      "XX     ^^^<^^^^^^");

    assertUnderline(2,range,
      "XX      ^^^",
      "XX      ^^^ ^[sc=10;ec=15]");

    UnderlinedRange multilineRange = new UnderlinedRange(5, 1, 6, 5);
    assertUnderline(2,multilineRange,
      "XX",
      "XX^[el=+1;ec=5]");
    assertUnderline(2,multilineRange,
      "XXXX",
      "XXXX^[sc=1;el=+1;ec=5]");
  }

  private void assertUnderline(int indent, UnderlinedRange range, String exitingLn, String expected) {
    StringBuilder line = new StringBuilder();
    line.append(exitingLn);
    range.underline(indent, line);
    assertThat(line).hasToString(expected);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void out_of_bounds_line() throws Exception {
    new UnderlinedRange(0, 10, 5, 15);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void out_of_bounds_column() throws Exception {
    new UnderlinedRange(5, 0, 5, 15);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void out_of_bounds_end_line() throws Exception {
    new UnderlinedRange(5, 10, 4, 15);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void out_of_bounds_end_column() throws Exception {
    new UnderlinedRange(5, 10, 5, 9);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void multiline_out_of_bounds_end_column() throws Exception {
    new UnderlinedRange(5, 10, 6, 0);
  }

}
