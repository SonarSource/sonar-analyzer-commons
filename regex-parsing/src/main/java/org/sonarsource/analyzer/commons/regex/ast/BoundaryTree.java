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
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class BoundaryTree extends RegexTree {

  public enum Type {
    LINE_START('^'),
    LINE_END('$'),
    WORD('b'),
    // requires brackets as well
    UNICODE_EXTENDED_GRAPHEME_CLUSTER('b'),
    NON_WORD('B'),
    INPUT_START('A'),
    PREVIOUS_MATCH_END('G'),
    INPUT_END_FINAL_TERMINATOR('Z'),
    INPUT_END('z');

    private final char key;

    Type(char key) {
      this.key = key;
    }

    @Nullable
    public static Type forKey(char k) {
      for (Type type : Type.values()) {
        if (type.key == k) {
          return type;
        }
      }
      return null;
    }
  }

  private final Type type;

  public BoundaryTree(RegexSource source, Type type, IndexRange range, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.type = type;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitBoundary(this);
  }

  @Override
  public RegexTree.Kind kind() {
    return RegexTree.Kind.BOUNDARY;
  }

  public Type type() {
    return type;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

}
