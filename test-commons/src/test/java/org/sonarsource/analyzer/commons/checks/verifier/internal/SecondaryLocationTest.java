/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import javax.annotation.Nullable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecondaryLocationTest {

  @Test
  public void test() throws Exception {
    UnderlinedRange range = new UnderlinedRange(5, 3, 5, 8);

    assertThat(secondary("XX", range, false, 7, null))
      .isEqualTo("XX  ^^^^^^> 7");

    assertThat(secondary("", range, false, 1, "msg1"))
      .isEqualTo("  ^^^^^^> 1 {{msg1}}");

    assertThat(secondary("", range, true, null, null))
      .isEqualTo("  ^^^^^^<");
  }

  private static String secondary(String prefix, UnderlinedRange range, boolean primaryIsBefore, @Nullable Integer index, @Nullable String message) {
    PreciseLocation location = new SecondaryLocation(range, primaryIsBefore, index, message);
    StringBuilder out = new StringBuilder();
    out.append(prefix);
    location.write(prefix.length(), out, primaryIsBefore);
    return out.toString();
  }

}
