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

public interface CharacterClassElementTree extends RegexSyntaxElement {

  enum Kind {
    INTERSECTION,
    UNION,
    NEGATION,
    CHARACTER_RANGE,
    ESCAPED_CHARACTER_CLASS,
    PLAIN_CHARACTER,
    UNICODE_CODE_POINT,
    MISC_ESCAPE_SEQUENCE,
    NESTED_CHARACTER_CLASS,
    POSIX_CLASS
  }

  @Nonnull
  Kind characterClassElementKind();

  void accept(RegexVisitor visitor);

  default boolean is(Kind... kinds) {
    Kind thisKind = characterClassElementKind();
    for (Kind kind : kinds) {
      if (thisKind == kind) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  FlagSet activeFlags();

}
