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

import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CharacterRangeTree extends AbstractRegexSyntaxElement implements CharacterClassElementTree {

  private final CharacterTree lowerBound;

  private final CharacterTree upperBound;

  private final FlagSet activeFlags;

  public CharacterRangeTree(RegexSource source, IndexRange range, CharacterTree lowerBound, CharacterTree upperBound, FlagSet activeFlags) {
    super(source, range);
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.activeFlags = activeFlags;
  }

  public CharacterTree getLowerBound() {
    return lowerBound;
  }

  public CharacterTree getUpperBound() {
    return upperBound;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitCharacterRange(this);
  }

  @Nonnull
  @Override
  public Kind characterClassElementKind() {
    return Kind.CHARACTER_RANGE;
  }

  @Nonnull
  @Override
  public FlagSet activeFlags() {
    return activeFlags;
  }

}
