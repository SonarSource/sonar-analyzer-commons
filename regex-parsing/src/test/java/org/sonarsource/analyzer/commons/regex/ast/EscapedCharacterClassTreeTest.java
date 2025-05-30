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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexLexer;
import org.sonarsource.analyzer.commons.regex.RegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacterClass;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.makeSource;

class EscapedCharacterClassTreeTest {

  @Nested
  class Properties {

    @Test
    void posixCharacterClasses() {
      assertEscapedProperty("\\\\p{Lower}", "Lower", false);
      assertEscapedProperty("\\\\p{Upper}", "Upper", false);
      assertEscapedProperty("\\\\p{ASCII}", "ASCII", false);
      assertEscapedProperty("\\\\p{Alpha}", "Alpha", false);
      assertEscapedProperty("\\\\p{Digit}", "Digit", false);
      assertEscapedProperty("\\\\p{Alnum}", "Alnum", false);
      assertEscapedProperty("\\\\p{Punct}", "Punct", false);
      assertEscapedProperty("\\\\p{Graph}", "Graph", false);
      assertEscapedProperty("\\\\p{Print}", "Print", false);
      assertEscapedProperty("\\\\p{Blank}", "Blank", false);
      assertEscapedProperty("\\\\p{Cntrl}", "Cntrl", false);
      assertEscapedProperty("\\\\p{XDigit}", "XDigit", false);
      assertEscapedProperty("\\\\p{Space}", "Space", false);
    }

    @Test
    void javalangCharacterClasses() {
      assertEscapedProperty("\\\\p{javaLowerCase}", "javaLowerCase", false);
      assertEscapedProperty("\\\\p{javaUpperCase}", "javaUpperCase", false);
      assertEscapedProperty("\\\\p{javaWhitespace}", "javaWhitespace", false);
      assertEscapedProperty("\\\\p{javaMirrored}", "javaMirrored", false);
    }

    @Test
    void unicodeScriptsBLocksCategoriesAndBinaryProperties() {
      // Classes for Unicode scripts, blocks, categories and binary properties
      assertEscapedProperty("\\\\p{IsLatin}", "IsLatin", false);
      assertEscapedProperty("\\\\p{InGreek}", "InGreek", false);
      assertEscapedProperty("\\\\p{Lu}", "Lu", false);
      assertEscapedProperty("\\\\p{IsAlphabetic}", "IsAlphabetic", false);
      assertEscapedProperty("\\\\p{Sc}", "Sc", false);
      assertEscapedProperty(assertCharacterClass(false, assertSuccessfulParse("[\\\\p{IsLatin}]", RegexFeature.ESCAPED_CHARACTER_CLASS)),
        "IsLatin", false);
    }

    @Test
    void negation() {
      assertEscapedProperty("\\\\P{InGreek}", "InGreek", true); // Any character except one in the Greek block
    }

    /**
     * Accept any property, even if it does not exists in hardcoded properties
     */
    @Test
    void anyNameProperty() {
      assertEscapedProperty("\\\\p{Cowabunga}", "Cowabunga", false);
      assertEscapedProperty("\\\\P{Cowabunga}", "Cowabunga", true);
    }

    @Test
    void failingInvalidEscapedProperties() {
      assertFailParsing("\\\\p", "Expected '{', but found the end of the regex", RegexFeature.ESCAPED_CHARACTER_CLASS);
      assertFailParsing("\\\\p{", "Expected a property name, but found the end of the regex", RegexFeature.ESCAPED_CHARACTER_CLASS);
      assertFailParsing("\\\\p{foo", "Expected '}', but found the end of the regex", RegexFeature.ESCAPED_CHARACTER_CLASS);
      assertFailParsing("\\\\p{}", "Expected a property name, but found '}'", RegexFeature.ESCAPED_CHARACTER_CLASS);
      assertFailParsing("\\\\p{Lower}", "Expected integer, but found 'L'");
    }

    private void assertEscapedProperty(String regex, String expectedProperty, boolean isNegation) {
      RegexTree tree = assertSuccessfulParse(regex, RegexFeature.ESCAPED_CHARACTER_CLASS);
      assertEscapedProperty(tree, expectedProperty, isNegation);
    }

    private void assertEscapedProperty(RegexSyntaxElement tree, String expectedProperty, boolean isNegation) {
      assertThat(tree).isInstanceOf(EscapedCharacterClassTree.class);
      EscapedCharacterClassTree escapedProperty = (EscapedCharacterClassTree) tree;
      assertKind(RegexTree.Kind.ESCAPED_CHARACTER_CLASS, escapedProperty);
      assertKind(CharacterClassElementTree.Kind.ESCAPED_CHARACTER_CLASS, escapedProperty);

      assertThat(escapedProperty.isProperty()).isTrue();
      assertThat(escapedProperty.getType()).isEqualTo(isNegation ? 'P' : 'p');
      assertThat(escapedProperty.property()).isNotNull().isEqualTo(expectedProperty);
      assertThat(escapedProperty.isNegation()).isEqualTo(isNegation);
      assertThat(escapedProperty.incomingTransitionType()).isEqualTo(AutomatonState.TransitionType.CHARACTER);
    }

  }

  @Nested
  class OtherClasses {

    @Test
    void positiveClasses() {
      assertCharacterClass("\\\\w", 'w', false);
      assertCharacterClass("\\\\d", 'd', false);
      assertCharacterClass("\\\\s", 's', false);
      assertCharacterClass("\\\\h", 'h', false);
      assertCharacterClass("\\\\v", 'v', false);
    }

    @Test
    void negatedClasses() {
      assertCharacterClass("\\\\W", 'W', true);
      assertCharacterClass("\\\\D", 'D', true);
      assertCharacterClass("\\\\S", 'S', true);
      assertCharacterClass("\\\\H", 'H', true);
      assertCharacterClass("\\\\V", 'V', true);
    }

    @Test
    void notCharacterClasses() {
      assertCharacter('i', "\\\\i");
    }

    @Test
    void illegalConstructorArguments() {
      assertIllegalArgument("\\\\p", false, "\\p needs a property string");
      assertIllegalArgument("\\\\P", false, "\\p needs a property string");
      assertIllegalArgument("\\\\w{x}", true, "Only \\p can have a property string");
      assertIllegalArgument("\\\\W{x}", true, "Only \\p can have a property string");
    }

    private void assertIllegalArgument(String regex, boolean includesProperty, String message) {
      RegexSource source = makeSource(regex);
      RegexLexer lexer = source.createLexer();
      SourceCharacter backslash = lexer.getCurrent();
      lexer.moveNext();
      SourceCharacter type = lexer.getCurrent();
      FlagSet activeFlags = new FlagSet();
      Executable createTree;
      if (includesProperty) {
        lexer.moveNext();
        SourceCharacter openingBrace = lexer.getCurrent();
        lexer.moveNext(2);
        SourceCharacter closingBrace = lexer.getCurrent();
        createTree = () -> new EscapedCharacterClassTree(source, backslash, type, openingBrace, closingBrace, activeFlags);
      } else {
        createTree = () -> new EscapedCharacterClassTree(source, backslash, type, activeFlags);
      }
      assertThrows(IllegalArgumentException.class, createTree, message);
    }

    private void assertCharacterClass(String regex, char expectedType, boolean isNegation) {
      EscapedCharacterClassTree escapedClass = assertType(EscapedCharacterClassTree.class, assertSuccessfulParse(regex));
      assertThat(escapedClass.isProperty()).isFalse();
      assertThat(escapedClass.property()).isNull();
      assertThat(escapedClass.getType()).isEqualTo(expectedType);
      assertThat(escapedClass.isNegation()).isEqualTo(isNegation);
      assertThat(escapedClass.characterClassElementKind()).isEqualTo(CharacterClassElementTree.Kind.ESCAPED_CHARACTER_CLASS);
      assertThat(escapedClass.incomingTransitionType()).isEqualTo(AutomatonState.TransitionType.CHARACTER);
    }

  }

}
