/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2022 SonarSource SA
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

import javax.annotation.Nullable;

public class SecondaryLocation extends PreciseLocation {

  public final boolean primaryIsBefore;

  @Nullable
  public Integer index;

  @Nullable
  public String message;

  public SecondaryLocation(UnderlinedRange range, boolean primaryIsBefore, @Nullable Integer index, @Nullable String message) {
    super(range);
    this.primaryIsBefore = primaryIsBefore;
    this.index = index;
    this.message = message;
  }

  @Override
  public void write(int indent, StringBuilder out, boolean primaryIsWritten) {
    range.underline(indent, out);
    out.append(primaryIsWritten ? '<' : '>');
    if (index != null) {
      out.append(" ").append(index);
    }
    if (message != null) {
      out.append(" {{").append(message).append("}}");
    }
  }

}
