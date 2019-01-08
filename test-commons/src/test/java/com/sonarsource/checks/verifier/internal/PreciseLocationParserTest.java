/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2019 SonarSource SA
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
package com.sonarsource.checks.verifier.internal;

import com.sonarsource.checks.coverage.UtilityClass;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.sonarsource.checks.verifier.internal.PreciseLocationParser.parse;
import static org.assertj.core.api.Assertions.assertThat;

public class PreciseLocationParserTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void ignored_comment() throws Exception {
    List<PreciseLocation> locations = parse(42, 10, "bla bla");
    assertThat(locations).isEmpty();
  }

  @Test
  public void unexpected_character_exception() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Precise Location: unexpected character found at line 42 col 15 in:  ^^^ ERROR");
    parse(42, 10, " ^^^ ERROR");
  }

  @Test
  public void parse_primary() throws Exception {
    List<PreciseLocation> locations = parse(42, 10, "  ^^^");
    assertThat(locations).hasSize(1);
    PrimaryLocation location = (PrimaryLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(41:12,41:14)");
    assertThat(location.expectedAdditionalCount).isNull();

    locations = parse(2, 1, "^ 5");
    assertThat(locations).hasSize(1);
    location = (PrimaryLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(1:1,1:1)");
    assertThat(location.expectedAdditionalCount).isEqualTo(5);
  }

  @Test
  public void support_comment_with_newLine() throws Exception {
    List<PreciseLocation> locations = parse(42, 10, "  ^^^\n");
    assertThat(locations).hasSize(1);
    locations = parse(42, 10, "  ^^^\r");
    assertThat(locations).hasSize(1);
    locations = parse(42, 10, "  ^^^\r\n");
    assertThat(locations).hasSize(1);
  }

  @Test
  public void parse_secondary() throws Exception {
    List<PreciseLocation> locations = parse(42, 2, "    ^^< {{msg1}} ^^^^> 5 {{msg2}}");
    assertThat(locations).hasSize(2);
    SecondaryLocation location = (SecondaryLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(41:6,41:7)");
    assertThat(location.message).isEqualTo("msg1");
    assertThat(location.index).isNull();
    assertThat(location.primaryIsBefore).isTrue();

    location = (SecondaryLocation) locations.get(1);
    assertThat(location.range.toString()).isEqualTo("(41:19,41:22)");
    assertThat(location.message).isEqualTo("msg2");
    assertThat(location.index).isEqualTo(5);
    assertThat(location.primaryIsBefore).isFalse();
  }

  @Test
  public void parse_flow() throws Exception {
    List<PreciseLocation> locations = parse(42, 3, "^^< 1.2 ^^^^> 3.4 {{msg2}}");
    assertThat(locations).hasSize(2);
    FlowLocation location = (FlowLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(41:3,41:4)");
    assertThat(location.message).isNull();
    assertThat(location.flowIndex).isEqualTo(1);
    assertThat(location.indexInTheFlow).isEqualTo(2);
    assertThat(location.primaryIsBefore).isTrue();

    location = (FlowLocation) locations.get(1);
    assertThat(location.range.toString()).isEqualTo("(41:11,41:14)");
    assertThat(location.message).isEqualTo("msg2");
    assertThat(location.flowIndex).isEqualTo(3);
    assertThat(location.indexInTheFlow).isEqualTo(4);
    assertThat(location.primaryIsBefore).isFalse();
  }

  @Test
  public void line_adjustment() throws Exception {
    List<PreciseLocation> locations = parse(42, 3, "  ^^^@10  ^^^@-1<  ^^^@+2<1.1");
    assertThat(locations).hasSize(3);
    PrimaryLocation location = (PrimaryLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(10:5,10:7)");

    SecondaryLocation secondary = (SecondaryLocation) locations.get(1);
    assertThat(secondary.range.toString()).isEqualTo("(40:13,40:15)");

    FlowLocation flow = (FlowLocation) locations.get(2);
    assertThat(flow.range.toString()).isEqualTo("(43:22,43:24)");
  }

  @Test
  public void multiple_lines_format() throws Exception {
    List<PreciseLocation> locations = parse(42, 3, "  ^[sc=1;el=+1;ec=5]@-1 ^[el=+2;ec=5]<");
    assertThat(locations).hasSize(2);
    PrimaryLocation location = (PrimaryLocation) locations.get(0);
    assertThat(location.range.toString()).isEqualTo("(40:1,41:5)");

    SecondaryLocation secondary = (SecondaryLocation) locations.get(1);
    assertThat(secondary.range.toString()).isEqualTo("(41:27,43:5)");
  }

  @Test
  public void private_constructor() throws Exception {
    UtilityClass.assertGoodPractice(PreciseLocationParser.class);
  }
}
