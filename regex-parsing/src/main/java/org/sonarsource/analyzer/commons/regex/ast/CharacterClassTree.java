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

import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CharacterClassTree extends RegexTree implements CharacterClassElementTree {

  private final SourceCharacter openingBracket;

  private final CharacterClassElementTree contents;

  private final boolean negated;

  public CharacterClassTree(RegexSource source, IndexRange range, SourceCharacter openingBracket, boolean negated,
    CharacterClassElementTree contents, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.negated = negated;
    this.contents = contents;
    this.openingBracket = openingBracket;
  }

  public CharacterClassElementTree getContents() {
    return contents;
  }

  public boolean isNegated() {
    return negated;
  }

  public SourceCharacter getOpeningBracket() {
    return openingBracket;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitCharacterClass(this);
  }

  @Override
  public RegexTree.Kind kind() {
    return RegexTree.Kind.CHARACTER_CLASS;
  }

  @Nonnull
  @Override
  public CharacterClassElementTree.Kind characterClassElementKind() {
    return CharacterClassElementTree.Kind.NESTED_CHARACTER_CLASS;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.CHARACTER;
  }

}
