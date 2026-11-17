/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class QuotedPosixCharacterClassTest {

  @Test
  void quotedPosixSyntaxIsLiteral() {
    for (String property : List.of("alpha", "^digit", "<", ">")) {
      String literal = "[:" + property + ":]";
      RegexTree tree = assertSuccessfulParse("[\\\\Q" + literal + "\\\\E]", RegexFeature.POSIX_CHARACTER_CLASS);
      CharacterClassTree characterClass = assertType(CharacterClassTree.class, tree);
      CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, characterClass.getContents());
      assertThat(union.getCharacterClasses()).hasSize(literal.length());
      for (int i = 0; i < literal.length(); i++) {
        CharacterTree character = assertType(CharacterTree.class, union.getCharacterClasses().get(i));
        assertThat(character.codePointOrUnit()).isEqualTo(literal.charAt(i));
      }
    }
    assertFailParsing("[\\\\Q[:alpha:]]", "Expected '\\E', but found the end of the regex", RegexFeature.POSIX_CHARACTER_CLASS);
  }

  @Test
  void posixNameCannotSpanQuotingDelimiters() {
    RegexParseResult result = new RegexParser(new PhpRegexSource("[[\\Q:\\Ealpha:]]", '\''), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    SequenceTree sequence = assertType(SequenceTree.class, result.getResult());
    assertThat(sequence.getItems()).hasSize(2);
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, sequence.getItems().get(0));
    CharacterClassUnionTree union = assertType(CharacterClassUnionTree.class, characterClass.getContents());
    assertThat(union.getCharacterClasses())
      .extracting(element -> assertType(CharacterTree.class, element).characterAsString())
      .containsExactly("[", ":", "a", "l", "p", "h", "a", ":");
    assertThat(assertType(CharacterTree.class, sequence.getItems().get(1)).characterAsString()).isEqualTo("]");
  }

}
