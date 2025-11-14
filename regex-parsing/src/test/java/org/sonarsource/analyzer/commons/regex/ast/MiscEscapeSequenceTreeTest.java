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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertLocation;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class MiscEscapeSequenceTreeTest {

  @Test
  void testBackslashN() {
    assertMiscEscapeSequence("\\\\N{Slightly Smiling Face}");
    assertMiscEscapeSequence("\\\\N{invalid name}"); // This should actually produce an error, but is accepted for now
    assertFailParsing("\\\\N", "Expected '{', but found the end of the regex");
    assertFailParsing("\\\\N{", "Expected a Unicode character name, but found the end of the regex");
    assertFailParsing("\\\\N{}", "Expected a Unicode character name, but found '}'");
    assertFailParsing("\\\\N{x", "Expected '}', but found the end of the regex");
  }

  @Test
  void testBackslashR() {
    assertMiscEscapeSequence("\\\\R");
  }

  @Test
  void testBackslashX() {
    assertMiscEscapeSequence("\\\\X");
  }

  private void assertMiscEscapeSequence(String regex) {
    assertMiscEscapeSequence(regex, 0);
  }

  private void assertMiscEscapeSequence(String regex, int initialFlags) {
    MiscEscapeSequenceTree escapeSequence = assertType(MiscEscapeSequenceTree.class, assertSuccessfulParse(regex, initialFlags));
    assertKind(RegexTree.Kind.MISC_ESCAPE_SEQUENCE, escapeSequence);
    assertThat(escapeSequence.activeFlags().getMask()).isEqualTo(initialFlags);
    assertEquals(regex, escapeSequence.getText());
    assertLocation(0, regex.length(), escapeSequence);
    assertEquals(AutomatonState.TransitionType.CHARACTER, escapeSequence.incomingTransitionType());
    assertEquals(CharacterClassElementTree.Kind.MISC_ESCAPE_SEQUENCE, escapeSequence.characterClassElementKind());
  }

}
