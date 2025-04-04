/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.internal.PerLineLocationWriter;
import org.sonarsource.analyzer.commons.checks.verifier.internal.PreciseLocation;
import org.sonarsource.analyzer.commons.checks.verifier.internal.PrimaryLocation;
import org.sonarsource.analyzer.commons.checks.verifier.internal.SecondaryLocation;
import org.sonarsource.analyzer.commons.checks.verifier.internal.UnderlinedRange;

import static org.assertj.core.api.Assertions.assertThat;

public class PerLineLocationWriterTest {

  @Test
  public void no_underlined_range() {
    assertWriter(Collections.emptyList(),
      "042: int length = 300;",
      "042: int length = 300;\n");
  }

  @Test
  public void one_underlined_range() {
    assertWriter(Collections.singletonList(
      new PrimaryLocation(new UnderlinedRange(42, 1, 42, 3), 0)),
      "042: int length = 300;",
      "042: int length = 300;\n" +
      "042: ^^^ 0\n");
  }

  @Test
  public void several_underlined_range_in_wrong_order() {
    assertWriter(Arrays.asList(
      new SecondaryLocation(new UnderlinedRange(42, 5, 42, 10), true, null, null),
      new SecondaryLocation(new UnderlinedRange(42, 12, 42, 12), true, null, "msg1"),
      new PrimaryLocation(new UnderlinedRange(42, 1, 42, 3), null),
      new SecondaryLocation(new UnderlinedRange(42, 14, 42, 16), true, null, "msg2")),
      "042: int length = 300;",
      "042: int length = 300;\n" +
      "042: ^^^ ^^^^^^<^< {{msg1}}\n" +
      "042:              ^^^< {{msg2}}\n");
  }

  @Test
  public void conflict_with_two_consecutive_primary() {
    assertWriter(Arrays.asList(
      new PrimaryLocation(new UnderlinedRange(42, 1, 42, 6), null),
      new PrimaryLocation(new UnderlinedRange(42, 7, 42, 8), null)),
      "042: length++;",
      "042: length++;\n" +
      "042: ^^^^^^\n" +
      "042:       ^^\n");
  }

  private void assertWriter(List<PreciseLocation> locations, String codeLine, String expected) {
    String lineNumber = codeLine.substring(0, 5);
    String code = codeLine.substring(5);
    PerLineLocationWriter writer = new PerLineLocationWriter(lineNumber, code);
    locations.forEach(writer::add);
    StringBuilder out = new StringBuilder();
    UnderlinedRange primaryRange = locations.stream()
      .filter(location -> location instanceof PrimaryLocation)
      .findFirst()
      .map(location->location.range)
      .orElse(null);
    writer.write(out, primaryRange);
    assertThat(out).hasToString(expected);
  }

}
