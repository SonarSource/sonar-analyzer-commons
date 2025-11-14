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

/**
 * This class represents escape sequences inside regular expression that we don't particularly care about.
 * Therefore the tree provides no information about the escape sequence other than its text.
 */
public class MiscEscapeSequenceTree extends RegexTree implements CharacterClassElementTree {

  public MiscEscapeSequenceTree(RegexSource source, IndexRange range, FlagSet activeFlags) {
    super(source, range, activeFlags);
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitMiscEscapeSequence(this);
  }

  @Override
  public RegexTree.Kind kind() {
    return RegexTree.Kind.MISC_ESCAPE_SEQUENCE;
  }

  @Nonnull
  @Override
  public CharacterClassElementTree.Kind characterClassElementKind() {
    return CharacterClassElementTree.Kind.MISC_ESCAPE_SEQUENCE;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.CHARACTER;
  }

}
