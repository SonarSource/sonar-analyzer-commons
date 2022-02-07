/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListElements;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertLocation;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertPlainString;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSingleEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class SequenceTreeTest {

  @Test
  void emptyString() {
    RegexTree regex = assertSuccessfulParse("");
    assertLocation(0, 0, regex);
    assertKind(RegexTree.Kind.SEQUENCE, regex);
    assertEquals(AutomatonState.TransitionType.EPSILON, regex.incomingTransitionType());
    assertSingleEdge(regex, regex.continuation(), AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void multipleEscapes() {
    SequenceTree sequence = assertType(SequenceTree.class, assertSuccessfulParse("\\123\\124"));
    assertListElements(sequence.getItems(),
      first -> {
        assertCharacter('S', first);
        assertLocation(0, 4, first);
      },
      second -> {
        assertCharacter('T', second);
        assertLocation(4, 8, second);
      }
    );
    assertEquals(AutomatonState.TransitionType.EPSILON, sequence.incomingTransitionType());
    assertSingleEdge(sequence, sequence.getItems().get(0), AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(sequence.getItems().get(0), sequence.getItems().get(1), AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(sequence.getItems().get(1), sequence.continuation(), AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void octalEscapeLimit() {
    SequenceTree sequence = assertType(SequenceTree.class, assertSuccessfulParse("\\456"));
    assertListElements(sequence.getItems(),
      first -> {
        assertCharacter('%', first);
        assertLocation(0, 3, first);
      },
      second -> {
        assertCharacter('6', second);
        assertLocation(3, 4, second);
      }
    );
    assertEquals(AutomatonState.TransitionType.EPSILON, sequence.incomingTransitionType());
    assertSingleEdge(sequence, sequence.getItems().get(0), AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(sequence.getItems().get(0), sequence.getItems().get(1), AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(sequence.getItems().get(1), sequence.continuation(), AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void quotedString() {
    assertPlainString("a(b)\\w|cd*[]", "\\\\Qa(b)\\\\w|cd*[]\\\\E");
    assertPlainString("a(b)\\w|cd*[]", "a\\\\Q(b)\\\\w|\\\\Ecd\\\\Q*[]\\\\E");
    assertPlainString("a(b)\\w|cd*[]\\", "\\\\Qa(b)\\\\w|cd*[]\\\\\\\\E");
  }

  @Test
  void quotedStringWithComments() {
    assertPlainString("a#b", "\\\\Qa#b\\\\E", Pattern.COMMENTS);
    assertPlainString("ab", "ab#\\\\Qc\\\\E", Pattern.COMMENTS);
    assertPlainString("ab", "\\\\Qab\\\\E#\\\\Qb\\\\E", Pattern.COMMENTS);
    assertPlainString("", "\\\\Q\\\\E#lala", Pattern.COMMENTS);
    assertPlainString("a b", "\\\\Qa b\\\\E", Pattern.COMMENTS);
    assertPlainString("ab", "\\\\Qa\\\\E \\\\Qb\\\\E", Pattern.COMMENTS);
  }

  @Test
  void illegalQuotedString() {
    assertFailParsing("abc\\\\E", "\\E used without \\Q");
    assertFailParsing("\\\\Qabc", "Expected '\\E', but found the end of the regex");
  }

  @Test
  void unescapedCurlyBrace() {
    assertSequenceWithUnescapedCurlyBrace("x{");
    assertSequenceWithUnescapedCurlyBrace("x{}");
    assertSequenceWithUnescapedCurlyBrace("x{1");
    assertSequenceWithUnescapedCurlyBrace("x{a");
    assertSequenceWithUnescapedCurlyBrace("x{a}");
    assertSequenceWithUnescapedCurlyBrace("x{a}");
    assertSequenceWithUnescapedCurlyBrace("x{1a}");
    assertSequenceWithUnescapedCurlyBrace("x{1,a}");
    assertSequenceWithUnescapedCurlyBrace("{1}");
    assertSequenceWithUnescapedCurlyBrace("{1");

    assertFailParsing("x{", "Expected integer, but found the end of the regex");
    assertFailParsing("x{1", "Expected ',' or '}', but found the end of the regex");
    assertFailParsing("x{}", "Expected integer, but found '}'");
    assertFailParsing("x{a", "Expected integer, but found 'a'");
    assertFailParsing("x{1,a", "Expected integer or '}', but found 'a'");
    assertFailParsing("x{1a", "Expected ',' or '}', but found 'a'");
    assertFailParsing("{1}", "Unexpected quantifier '{1}'");
    assertFailParsing("{1", "Expected ',' or '}', but found the end of the regex");
  }

  private void assertSequenceWithUnescapedCurlyBrace(String regex) {
    assertType(SequenceTree.class, assertSuccessfulParse(regex, RegexFeature.UNESCAPED_CURLY_BRACKET));
  }

}
