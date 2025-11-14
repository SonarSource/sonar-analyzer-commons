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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacterClass;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class DotTreeTest {

  @Test
  void onlyDot() {
    RegexTree regex = assertSuccessfulParse(".");
    assertType(DotTree.class, regex);
    assertKind(RegexTree.Kind.DOT, regex);
    assertEquals(AutomatonState.TransitionType.CHARACTER, regex.incomingTransitionType());
  }

  @Test
  void escapedDot() {
    assertCharacter('.', "\\\\.");
  }

  @Test
  void dotInCharacterClass() {
    RegexTree regex = assertSuccessfulParse("[.]");
    assertCharacter('.', assertCharacterClass(false, regex));
  }

  @Test
  void quantifiedDot() {
    RegexTree regex = assertSuccessfulParse(".*");
    assertType(DotTree.class, assertType(RepetitionTree.class, regex).getElement());
  }
}
