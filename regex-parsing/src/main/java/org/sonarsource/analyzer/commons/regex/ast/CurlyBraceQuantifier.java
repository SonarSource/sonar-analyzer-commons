/*
 * SonarSource Analyzers Regex Parsing Commons
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
