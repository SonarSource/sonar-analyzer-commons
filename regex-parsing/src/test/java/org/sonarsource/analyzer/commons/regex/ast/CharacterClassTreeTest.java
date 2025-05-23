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

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacterClass;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacterRange;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertJavaCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListElements;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListSize;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertToken;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class CharacterClassTreeTest {

  @Test
  void simpleCharacterClass() {
    RegexTree regex = assertSuccessfulParse("[a-z]", Pattern.UNIX_LINES);
    CharacterClassElementTree characterClass = assertCharacterClass(false, regex);
    assertThat(characterClass.activeFlags().getMask()).isEqualTo(Pattern.UNIX_LINES);
    assertCharacterRange('a', 'z', characterClass);
    assertJavaCharacter(0, '[', ((CharacterClassTree)regex).getOpeningBracket());
  }

  @Test
  void closingBracket() {
    RegexTree regex = assertSuccessfulParse("[]]");
    CharacterTree character = assertType(CharacterTree.class, assertCharacterClass(false, regex));
    assertEquals("]", character.characterAsString(), "Matched character should be ']'.");
  }

  @Test
  void dashRange() {
    RegexTree regex = assertSuccessfulParse("[---]");
    assertKind(RegexTree.Kind.CHARACTER_CLASS, regex);
    assertCharacterRange('-', '-', assertCharacterClass(false, regex));
  }

  @Test
  void leadingDash() {
    RegexTree regex = assertSuccessfulParse("[-a]", Pattern.CASE_INSENSITIVE);
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, assertCharacterClass(false, regex));
    assertKind(CharacterClassElementTree.Kind.UNION, union);
    assertThat(union.activeFlags().getMask()).isEqualTo(Pattern.CASE_INSENSITIVE);
    assertListElements(union.getCharacterClasses(),
      firstChar -> assertCharacter('-', firstChar),
      secondChar -> assertCharacter('a', secondChar)
    );
  }

  @Test
  void trailingDash() {
    RegexTree regex = assertSuccessfulParse("[a-]");
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, assertCharacterClass(false, regex));
    assertListElements(union.getCharacterClasses(),
      firstChar -> assertCharacter('a', firstChar),
      secondChar -> assertCharacter('-', secondChar)
    );
  }

  @Test
  void unionOfRangesAndSingleCharacters() {
    RegexTree regex = assertSuccessfulParse("[a-z0-9_.^]");
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, assertCharacterClass(false, regex));
    assertListElements(union.getCharacterClasses(),
      first -> assertCharacterRange('a', 'z', first),
      second -> assertCharacterRange('0', '9', second),
      third -> assertCharacter('_', third),
      fourth -> assertCharacter('.', fourth),
      fifth -> assertCharacter('^', fifth)
    );
  }

  @Test
  void negatedCharacterClass() {
    RegexTree regex = assertSuccessfulParse("[^a-z]");
    assertCharacterRange('a', 'z', assertCharacterClass(true, regex));
  }

  @Test
  void intersection() {
    RegexTree regex = assertSuccessfulParse("[a-z&&[^g-i]&]", Pattern.MULTILINE, RegexFeature.NESTED_CHARTER_CLASS);
    CharacterClassIntersectionTree intersection = assertType(CharacterClassIntersectionTree.class, assertCharacterClass(false, regex));
    assertKind(CharacterClassElementTree.Kind.INTERSECTION, intersection);
    assertThat(intersection.is(CharacterClassElementTree.Kind.UNION)).isFalse();
    assertThat(intersection.activeFlags().getMask()).isEqualTo(Pattern.MULTILINE);
    assertListElements(intersection.getAndOperators(),
      first -> assertToken(4, "&&", first)
    );
    assertListElements(intersection.getCharacterClasses(),
      first -> assertCharacterRange('a', 'z', first),
      second -> {
        CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, second);
        assertListElements(union.getCharacterClasses(),
          first -> assertCharacterRange('g', 'i', assertCharacterClass(true, first)),
          last -> assertCharacter('&', last)
        );
      }
    );
  }

  @Test
  void rangeWithEscapes() {
    RegexTree regex = assertSuccessfulParse("[\\\\[-\\\\]]");
    assertCharacterRange('[', ']', assertCharacterClass(false, regex));
  }

  @Test
  void classWithEscapes() {
    RegexTree regex = assertSuccessfulParse("[\\\\[\\\\-\\\\]]");
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, assertCharacterClass(false, regex));
    assertListElements(union.getCharacterClasses(),
      first -> assertCharacter('[', first),
      second -> assertCharacter('-', second),
      third -> assertCharacter(']', third)
    );
  }

  @Test
  void classWithCharacterClassEscapes() {
    RegexTree regex = assertSuccessfulParse("[\\\\w\\\\s]");
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, assertCharacterClass(false, regex));
    assertListElements(union.getCharacterClasses(),
      first -> assertEquals('w', assertType(EscapedCharacterClassTree.class, first).getType()),
      second -> assertEquals('s', assertType(EscapedCharacterClassTree.class, second).getType())
    );
  }

  @Test
  void emptyIntersectionOperands() {
    RegexTree regex = assertSuccessfulParse("[&&x]");
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, regex);
    CharacterClassIntersectionTree intersection = assertType(CharacterClassIntersectionTree.class, characterClass.getContents());
    assertListElements(intersection.getCharacterClasses(),
      first -> assertListSize(0, assertType(CharacterClassUnionTree.class, first).getCharacterClasses()),
      second -> assertCharacter('x', second)
    );
  }

  @Test
  void quotedStringInCharacterClass() {
    assertCharacterUnionCharacterClass("\\a-z]\\w", "[\\\\Q\\\\a-z]\\\\w\\\\E]");
    assertCharacterUnionCharacterClass("a-z", "[a\\\\Q-z\\\\E]");
  }

  @Test
  void quotedStringInCharacterRange() {
    CharacterClassElementTree contents = assertCharacterClass(false, assertSuccessfulParse("[a-\\\\QzA-Z\\\\E#]"));
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, contents);
    assertListElements(union.getCharacterClasses(),
      first -> assertCharacterRange('a', 'z', first),
      second -> assertCharacter('A', second),
      third -> assertCharacter('-', third),
      fourth -> assertCharacter('Z', fourth),
      fifth -> assertCharacter('#', fifth)
    );
  }

  @Test
  void quotedStringInCharacterIntersection() {
    CharacterClassElementTree contents = assertCharacterClass(false, assertSuccessfulParse("[\\\\QA-Z\\\\E&&]"));
    CharacterClassIntersectionTree union = assertType(CharacterClassIntersectionTree.class, contents);
    assertListElements(union.getCharacterClasses(),
      first -> assertCharacterUnion("A-Z", first),
      second -> assertCharacterUnion("", second)
    );
  }

  @Test
  void nestedCharacterClass() {
    RegexTree regex = assertSuccessfulParse("[a[bc]]", RegexFeature.NESTED_CHARTER_CLASS);
    CharacterClassElementTree characterClass = assertCharacterClass(false, regex);
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, characterClass);
    List<CharacterClassElementTree> elements = union.getCharacterClasses();
    assertListSize(2, elements);
    assertCharacter('a', elements.get(0));
    CharacterClassTree nestedUnion = assertType(CharacterClassTree.class, elements.get(1));
    assertCharacterUnion("bc", nestedUnion.getContents());

    assertFailParsing("[a[bc]", "Expected ']', but found the end of the regex", RegexFeature.NESTED_CHARTER_CLASS);

    CharacterClassElementTree contents = assertCharacterClass(false, assertSuccessfulParse("[a[bc]"));
    assertCharacterUnion("a[bc", contents);
  }

  @Test
  void unclosedCharacterClass() {
    assertFailParsing("[abc", "Expected ']', but found the end of the regex");
  }

  @Test
  void unclosedQuote() {
    assertFailParsing("[\\\\Q.-_]", "Expected '\\E', but found the end of the regex");
  }

  @Test
  void unclosedCharacterClassRange() {
    assertFailParsing("[abc-", "Expected ']', but found the end of the regex");
  }

  @Test
  void illegalRange() {
    assertFailParsing("[z-a]", "Illegal character range");
  }

  @Test
  void illegalRangeWithEscape() {
    assertFailParsing("[a-\\\\w]", "Expected simple character, but found '\\\\w'");
  }

  @Test
  void unsupportedEscapeInCharacterClass() {
    assertFailParsing("[\\\\b]", "Invalid escape sequence inside character class");
  }

  private void assertCharacterUnion(String expectedCharacters, CharacterClassElementTree characterClassElement) {
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, characterClassElement);
    List<CharacterClassElementTree> elements = union.getCharacterClasses();
    assertListSize(expectedCharacters.length(), elements);
    for (int i = 0; i < expectedCharacters.length(); i++) {
      assertCharacter(expectedCharacters.charAt(i), elements.get(i));
    }
  }

  private void assertCharacterUnionCharacterClass(String expectedCharacters, String regex) {
    CharacterClassElementTree contents = assertCharacterClass(false, assertSuccessfulParse(regex));
    assertCharacterUnion(expectedCharacters, contents);
  }

}
