/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2020 SonarSource SA
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

import javax.annotation.Nullable;

public class FlowLocation extends SecondaryLocation {

  public final int flowIndex;

  public final int indexInTheFlow;

  public FlowLocation(UnderlinedRange range, boolean primaryIsBefore, int flowIndex, int indexInTheFlow, @Nullable String message) {
    super(range, primaryIsBefore, flowIndex, message);
    this.flowIndex = flowIndex;
    this.indexInTheFlow = indexInTheFlow;
  }

  @Override
  public void write(int indent, StringBuilder out, boolean primaryIsWritten) {
    range.underline(indent, out);
    out.append(primaryIsWritten ? '<' : '>');
    out.append(' ').append(flowIndex).append('.').append(indexInTheFlow);
    if (message != null) {
      out.append(" {{").append(message).append("}}");
    }
  }

}
