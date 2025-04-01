/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.ast;

import org.sonarsource.analyzer.commons.regex.RegexSource;

public class SourceCharacter extends AbstractRegexSyntaxElement {

  private final char character;
  private final boolean isEscapeSequence;

  public SourceCharacter(RegexSource source, IndexRange range, char character) {
    this(source, range, character, false);
  }

  public SourceCharacter(RegexSource source, IndexRange range, char character, boolean isEscapeSequence) {
    super(source, range);
    this.character = character;
    this.isEscapeSequence = isEscapeSequence;
  }

  public char getCharacter() {
    return character;
  }

  public boolean isEscapeSequence() {
    return isEscapeSequence;
  }

}
