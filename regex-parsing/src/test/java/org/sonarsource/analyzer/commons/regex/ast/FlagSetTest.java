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

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagSetTest {

  @Test
  void emptySet() {
    FlagSet empty = new FlagSet();
    assertTrue(empty.isEmpty());
    assertFalse(empty.contains(Pattern.CASE_INSENSITIVE));
    assertFalse(empty.contains(Pattern.COMMENTS));
    assertFalse(empty.contains(Pattern.MULTILINE));
    assertEquals(0, empty.getMask());
  }

  @Test
  void nonEmptySet() {
    FlagSet nonEmpty = new FlagSet(Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);
    assertFalse(nonEmpty.isEmpty());
    assertTrue(nonEmpty.contains(Pattern.CASE_INSENSITIVE));
    assertTrue(nonEmpty.contains(Pattern.COMMENTS));
    assertFalse(nonEmpty.contains(Pattern.MULTILINE));
    assertEquals(Pattern.CASE_INSENSITIVE | Pattern.COMMENTS, nonEmpty.getMask());
  }

}
