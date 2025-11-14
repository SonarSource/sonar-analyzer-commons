/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class SimpleQuantifier extends Quantifier {

  public enum Kind {
    STAR("*"), PLUS("+"), QUESTION_MARK("?");

    private final String str;

    Kind(String str) {
      this.str = str;
    }

    @Override
    public String toString() {
      return str;
    }
  }

  private final Kind kind;

  public SimpleQuantifier(RegexSource source, IndexRange range, Modifier modifier, Kind kind) {
    super(source, range, modifier);
    this.kind = kind;
  }

  @Override
  public int getMinimumRepetitions() {
    if (kind == Kind.PLUS) {
      return 1;
    } else {
      return 0;
    }
  }

  @CheckForNull
  @Override
  public Integer getMaximumRepetitions() {
    if (kind == Kind.QUESTION_MARK) {
      return 1;
    } else {
      return null;
    }
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  public Kind getKind() {
    return kind;
  }

}
