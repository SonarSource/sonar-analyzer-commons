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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import javax.annotation.Nullable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlowLocationTest {

  @Test
  public void test() throws Exception {
    UnderlinedRange range = new UnderlinedRange(5, 6, 5, 11);

    assertThat(flow("XX", range, false, 2, 3, null))
      .isEqualTo("XX     ^^^^^^> 2.3");

    assertThat(flow("", range, false, 1, 1, "msg1"))
      .isEqualTo("     ^^^^^^> 1.1 {{msg1}}");

    assertThat(flow("", range, true, 1, 1, null))
      .isEqualTo("     ^^^^^^< 1.1");
  }

  private static String flow(String prefix, UnderlinedRange range, boolean primaryIsBefore, int flowIndex, int indexInTheFlow, @Nullable String message) {
    PreciseLocation location = new FlowLocation(range, primaryIsBefore, flowIndex, indexInTheFlow, message);
    StringBuilder out = new StringBuilder();
    out.append(prefix);
    location.write(prefix.length(), out, primaryIsBefore);
    return out.toString();
  }

}
