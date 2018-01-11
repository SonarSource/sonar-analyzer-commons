/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrimaryLocationTest {

  @Test
  public void test() throws Exception {
    UnderlinedRange range = new UnderlinedRange(5, 3, 5, 8);
    SecondaryLocation secondary = new SecondaryLocation(new UnderlinedRange(6, 21, 6, 25), true, null, null);

    assertThat(primary("XX", range, null, Collections.emptyList()))
      .isEqualTo("XX  ^^^^^^");

    assertThat(primary("", range, 0, Collections.emptyList()))
      .isEqualTo("  ^^^^^^ 0");

    assertThat(primary("", range, null, Collections.singletonList(secondary)))
      .isEqualTo("  ^^^^^^");

    assertThat(primary("", range, 1, Collections.singletonList(secondary)))
      .isEqualTo("  ^^^^^^ 1");

    assertThat(primary("", range, 2, Collections.singletonList(secondary)))
      .isEqualTo("  ^^^^^^ 2[ERROR expect 1]");
  }

  private static String primary(String prefix, UnderlinedRange range, @Nullable Integer expectedAdditionalCount, List<SecondaryLocation> secondaryLocations) {
    PrimaryLocation location = new PrimaryLocation(range, expectedAdditionalCount);
    location.secondaryLocations.addAll(secondaryLocations);
    StringBuilder out = new StringBuilder();
    out.append(prefix);
    location.write(prefix.length(), out, false);
    return out.toString();
  }

}
