/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2021 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class PrimaryLocation extends PreciseLocation {

  @Nullable
  public Integer expectedAdditionalCount;

  public final List<SecondaryLocation> secondaryLocations = new ArrayList<>();

  public final List<List<FlowLocation>> flowLocations = new ArrayList<>();

  public PrimaryLocation(UnderlinedRange range, @Nullable Integer expectedAdditionalCount) {
    super(range);
    this.expectedAdditionalCount = expectedAdditionalCount;
  }

  public int secondaryAndFlowLocationCount() {
    return secondaryLocations.size() + flowLocations.stream().mapToInt(List::size).sum();
  }

  public SecondaryLocation addSecondary(UnderlinedRange range, @Nullable String message) {
    boolean primaryIsBefore = this.range.compareTo(range) <= 0;
    SecondaryLocation location = new SecondaryLocation(range, primaryIsBefore, secondaryLocations.size() + 1, message);
    secondaryLocations.add(location);
    return location;
  }

  @Override
  public void write(int indent, StringBuilder out, boolean primaryIsWritten) {
    range.underline(indent, out);
    if (expectedAdditionalCount != null) {
      out.append(' ').append(expectedAdditionalCount);
      int additionalCount = secondaryAndFlowLocationCount();
      if (expectedAdditionalCount != additionalCount) {
        out.append("[ERROR expect ").append(additionalCount).append("]");
      }
    }
  }

}
