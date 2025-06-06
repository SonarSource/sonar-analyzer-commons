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
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParserTestUtils;
import org.sonarsource.analyzer.commons.regex.helpers.RegexTreeHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.CHARACTER;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class PosixCharacterClassElementTreeTest {

  @Test
  void posixCharacterClassElements() {
    assertPosixClass("[[:alnum:]]", "alnum", false);
    assertPosixClass("[[:alpha:]]", "alpha", false);
    assertPosixClass("[[:ascii:]]", "ascii", false);
    assertPosixClass("[[:cntrl:]]", "cntrl", false);
    assertPosixClass("[[:digit:]]", "digit", false);
    assertPosixClass("[[:graph:]]", "graph", false);
    assertPosixClass("[[:lower:]]", "lower", false);
    assertPosixClass("[[:print:]]", "print", false);
    assertPosixClass("[[:punct:]]", "punct", false);
    assertPosixClass("[[:space:]]", "space", false);
    assertPosixClass("[[:upper:]]", "upper", false);
    assertPosixClass("[[:word:]]", "word", false);
    assertPosixClass("[[:xdigit:]]", "xdigit", false);
    assertPosixClass("[[:<:]]", "<", false);
    assertPosixClass("[[:>:]]", ">", false);

    assertPosixClass("[[:^alnum:]]", "alnum", true);
    assertPosixClass("[[:^alpha:]]", "alpha", true);
    assertPosixClass("[[:^ascii:]]", "ascii", true);
    assertPosixClass("[[:^cntrl:]]", "cntrl", true);
    assertPosixClass("[[:^digit:]]", "digit", true);
    assertPosixClass("[[:^graph:]]", "graph", true);
    assertPosixClass("[[:^lower:]]", "lower", true);
    assertPosixClass("[[:^print:]]", "print", true);
    assertPosixClass("[[:^punct:]]", "punct", true);
    assertPosixClass("[[:^space:]]", "space", true);
    assertPosixClass("[[:^upper:]]", "upper", true);
    assertPosixClass("[[:^word:]]", "word", true);
    assertPosixClass("[[:^xdigit:]]", "xdigit", true);
    assertPosixClass("[[:^<:]]", "<", true);
    assertPosixClass("[[:^>:]]", ">", true);
  }

  @Test
  void posixCharacterClassElements_within_union() {
    RegexTree tree = assertSuccessfulParse("[[:alnum:]0-9]", RegexFeature.POSIX_CHARACTER_CLASS);
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, tree);
    CharacterClassUnionTree characterClassUnion = assertType(CharacterClassUnionTree.class, characterClass.getContents());

    List<CharacterClassElementTree> classElementTrees = characterClassUnion.getCharacterClasses();
    assertThat(classElementTrees).hasSize(2);
    assertType(PosixCharacterClassElementTree.class, classElementTrees.get(0));
    assertType(CharacterRangeTree.class, classElementTrees.get(1));
  }

  @Test
  void nonPosixCharacterClassElements() {
    assertNonPosixClass("[[:alpha]]");
    assertNonPosixClass("[[alpha]]");
  }

  @Test
  void posixCharacterClassWithoutFeatureSupport() {
    // Java
    RegexTree tree = assertSuccessfulParse("[[:alpha:]]", RegexFeature.NESTED_CHARTER_CLASS);
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, tree);
    assertThat(characterClass.characterClassElementKind()).isEqualTo(CharacterClassElementTree.Kind.NESTED_CHARACTER_CLASS);

    // Python
    tree = assertSuccessfulParse("[[:alpha:]]");
    SequenceTree sequence = assertType(SequenceTree.class, tree);
    List<RegexTree> items = sequence.getItems();
    assertThat(items).hasSize(2);
    assertKind(RegexTree.Kind.CHARACTER_CLASS, items.get(0));
    assertKind(RegexTree.Kind.CHARACTER, items.get(1));
  }

  private void assertPosixClass(String regex, String expectedProperty, boolean isNegation) {
    RegexTree tree = assertSuccessfulParse(regex, RegexFeature.POSIX_CHARACTER_CLASS);
    assertPosixClass(tree, expectedProperty, isNegation);
  }

  private void assertPosixClass(RegexSyntaxElement tree, String expectedProperty, boolean isNegation) {
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, tree);
    PosixCharacterClassElementTree posixCharacterClassElement = assertType(PosixCharacterClassElementTree.class, characterClass.getContents());
    assertKind(CharacterClassElementTree.Kind.POSIX_CLASS, posixCharacterClassElement);
    assertThat(posixCharacterClassElement.property()).isNotNull().isEqualTo(expectedProperty);
    assertThat(posixCharacterClassElement.activeFlags().isEmpty()).isTrue();
    assertThat(posixCharacterClassElement.isNegation()).isEqualTo(isNegation);

    CharacterClassElementTree classElementTree = spy(posixCharacterClassElement);
    RegexBaseVisitor visitor = new RegexBaseVisitor();
    visitor.visitInCharClass(classElementTree);
    verify(classElementTree).accept(visitor);
  }

  private void assertNonPosixClass(String regex) {
    RegexTree tree = assertSuccessfulParse(regex, RegexFeature.POSIX_CHARACTER_CLASS);
    assertThat(tree).isNotInstanceOf(CharacterClassTree.class);
  }

}
