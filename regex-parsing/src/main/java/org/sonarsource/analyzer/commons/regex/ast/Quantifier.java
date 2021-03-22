/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.ast;

import javax.annotation.CheckForNull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public abstract class Quantifier extends AbstractRegexSyntaxElement {

  public enum Modifier {
    GREEDY, RELUCTANT, POSSESSIVE
  }

  private final Modifier modifier;

  protected Quantifier(RegexSource source, IndexRange range, Modifier modifier) {
    super(source, range);
    this.modifier = modifier;
  }

  public abstract int getMinimumRepetitions();

  @CheckForNull
  public abstract Integer getMaximumRepetitions();

  public Modifier getModifier() {
    return modifier;
  }

  public boolean isOpenEnded() {
    return getMaximumRepetitions() == null;
  }

  public abstract boolean isFixed();

}
