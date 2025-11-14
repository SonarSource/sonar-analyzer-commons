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

import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CharacterTree extends RegexTree implements CharacterClassElementTree {

  private final int codePoint;
  private final boolean isEscapeSequence;

  public CharacterTree(RegexSource source, IndexRange range, int codePoint, boolean isEscapeSequence, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.codePoint = codePoint;
    this.isEscapeSequence = isEscapeSequence;
  }

  public int codePointOrUnit() {
    return codePoint;
  }

  public boolean isEscapeSequence() {
    return isEscapeSequence;
  }

  public String characterAsString() {
    return String.valueOf(Character.toChars(codePoint));
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitCharacter(this);
  }

  @Override
  public RegexTree.Kind kind() {
    return RegexTree.Kind.CHARACTER;
  }

  @Nonnull
  @Override
  public CharacterClassElementTree.Kind characterClassElementKind() {
    return CharacterClassElementTree.Kind.PLAIN_CHARACTER;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.CHARACTER;
  }
}
