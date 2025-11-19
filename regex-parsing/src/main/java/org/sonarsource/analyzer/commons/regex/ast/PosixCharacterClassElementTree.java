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

public class PosixCharacterClassElementTree extends AbstractRegexSyntaxElement implements CharacterClassElementTree {

  private final String property;
  private final FlagSet activeFlags;
  private final boolean isNegation;

  public PosixCharacterClassElementTree(RegexSource source, IndexRange range, boolean isNegation, String property, FlagSet activeFlags) {
    super(source, range);
    this.isNegation = isNegation;
    this.property = property;
    this.activeFlags = activeFlags;
  }

  public PosixCharacterClassElementTree(RegexSource source, SourceCharacter openBracket, SourceCharacter closingBracket, boolean isNegation, String property, FlagSet activeFlags) {
    this(source, openBracket.getRange().merge(closingBracket.getRange()), isNegation, property, activeFlags);
  }

  @Nonnull
  @Override
  public Kind characterClassElementKind() {
    return Kind.POSIX_CLASS;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    // do nothing
  }

  public boolean isNegation() {
    return isNegation;
  }

  @Nonnull
  public String property() {
    return property;
  }

  @Nonnull
  @Override
  public FlagSet activeFlags() {
    return activeFlags;
  }
}
