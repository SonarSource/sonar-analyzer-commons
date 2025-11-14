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
package org.sonarsource.analyzer.commons.regex;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;
import org.sonarsource.analyzer.commons.regex.java.JavaCharacterParser;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;
import org.sonarsource.analyzer.commons.regex.java.JavaUnicodeEscapeParser;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterParsingTest {

  @Test
  void parsingUnicode() {
    String regex = "\\t\\u1234";
    RegexSource regexSource = new JavaRegexSource(regex);

    List<SourceCharacter> unicodeCharacters = parseUnicode(regexSource);
    assertThat(unicodeCharacters.stream().map(SourceCharacter::getCharacter))
      .hasSize(3)
      .containsExactly('\\', 't', '\u1234');

    List<SourceCharacter> sourceCharacters = parseJavaCharacters(regexSource);
    assertThat(sourceCharacters.stream().map(SourceCharacter::getCharacter))
      .hasSize(2)
      .containsExactly('\t', '\u1234');
  }

  @Test
  void escapedBackslashes() {
    String regex = "\\\\\\\\u+[a-fA-F0-9]{4}";
    RegexSource regexSource = new JavaRegexSource(regex);

    List<SourceCharacter> unicodeCharacters = parseUnicode(regexSource);
    assertThat(unicodeCharacters.stream().map(SourceCharacter::getCharacter))
      .hasSize(20)
      .startsWith('\\', '\\', '\\', '\\', 'u', '+', '[');

    List<SourceCharacter> sourceCharacters = parseJavaCharacters(regexSource);
    assertThat(sourceCharacters.stream().map(SourceCharacter::getCharacter))
      .hasSize(18)
      .startsWith('\\', '\\', 'u', '+', '[');
  }

  private static List<SourceCharacter> parseJavaCharacters(RegexSource regexSource) {
    JavaCharacterParser characterParser = new JavaCharacterParser(regexSource);
    List<SourceCharacter> sourceCharacters = new ArrayList<>();
    while (!characterParser.isAtEnd()) {
      sourceCharacters.add(characterParser.getCurrent());
      characterParser.moveNext();
    }
    return sourceCharacters;
  }

  private static List<SourceCharacter> parseUnicode(RegexSource regexSource) {
    JavaUnicodeEscapeParser unicodeParser = new JavaUnicodeEscapeParser(regexSource);
    List<SourceCharacter> unicodeCharacters = new ArrayList<>();
    while (unicodeParser.getCurrent() != null) {
      unicodeCharacters.add(unicodeParser.getCurrent());
      unicodeParser.moveNext();
    }
    return unicodeCharacters;
  }

}
