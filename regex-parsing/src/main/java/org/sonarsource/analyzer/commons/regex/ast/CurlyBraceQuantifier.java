/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.ast;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CurlyBraceQuantifier extends Quantifier {

  private final RegexToken minimumRepetitionsToken;

  private final int minimumRepetitions;

  private final RegexToken commaToken;

  private final RegexToken maximumRepetitionsToken;

  private final Integer maximumRepetitions;

  public CurlyBraceQuantifier(
    RegexSource source,
    IndexRange range,
    Modifier modifier,
    @Nullable RegexToken minimumRepetitionsToken,
    @Nullable RegexToken commaToken,
    @Nullable RegexToken maximumRepetitionsToken
  ) {
    super(source, range, modifier);
    this.minimumRepetitionsToken = minimumRepetitionsToken;
    this.minimumRepetitions = minimumRepetitionsToken == null ? 0 : Integer.parseInt(minimumRepetitionsToken.getText());
    this.commaToken = commaToken;
    this.maximumRepetitionsToken = maximumRepetitionsToken;
    if (maximumRepetitionsToken == null) {
      this.maximumRepetitions = null;
    } else {
      this.maximumRepetitions = Integer.parseInt(maximumRepetitionsToken.getText());
    }
  }

  @Override
  public int getMinimumRepetitions() {
    return minimumRepetitions;
  }

  @CheckForNull
  @Override
  public Integer getMaximumRepetitions() {
    if (commaToken == null) {
      return minimumRepetitions;
    } else {
      return maximumRepetitions;
    }
  }

  @CheckForNull
  public RegexToken getMinimumRepetitionsToken() {
    return minimumRepetitionsToken;
  }

  @CheckForNull
  public RegexToken getCommaToken() {
    return commaToken;
  }

  @CheckForNull
  public RegexToken getMaximumRepetitionsToken() {
    return maximumRepetitionsToken;
  }

  @Override
  public boolean isFixed() {
    return commaToken == null;
  }

}
