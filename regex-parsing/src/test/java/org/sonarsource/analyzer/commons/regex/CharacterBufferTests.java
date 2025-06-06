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
package org.sonarsource.analyzer.commons.regex;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterBufferTests {

  @Test
  void testEmpty() {
    assertEmptyBuffer(new CharacterBuffer(42));
    assertEmptyBuffer(new CharacterBuffer(1));
  }

  @Test
  void testAddingGettingAndRemoving() {
    CharacterBuffer buffer = new CharacterBuffer(2);
    buffer.add(makeCharacter('a'));
    buffer.add(makeCharacter('b'));
    assertEquals('a', buffer.get(0).getCharacter());
    assertEquals('b', buffer.get(1).getCharacter());
    buffer.removeFirst();
    assertEquals('b', buffer.get(0).getCharacter());
    buffer.add(makeCharacter('c'));
    buffer.add(makeCharacter('d'));
    assertEquals('b', buffer.get(0).getCharacter());
    assertEquals('c', buffer.get(1).getCharacter());
    assertEquals('d', buffer.get(2).getCharacter());
    buffer.removeFirst();
    assertEquals('c', buffer.get(0).getCharacter());
    assertEquals('d', buffer.get(1).getCharacter());
  }

  @Test
  void indexOutOfBounds() {
    CharacterBuffer buffer = new CharacterBuffer(23);
    buffer.add(makeCharacter('a'));
    buffer.add(makeCharacter('b'));
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(2));
  }

  @Test
  void popEmptyBuffer() {
    CharacterBuffer buffer = new CharacterBuffer(7);
    assertThrows(NoSuchElementException.class, buffer::removeFirst);
  }

  private void assertEmptyBuffer(CharacterBuffer buffer) {
    assertTrue(buffer.isEmpty(), "Empty buffer should be empty.");
    assertEquals(0, buffer.size(), "Size of empty buffer should be 0.");
    buffer.add(makeCharacter('x'));
    assertFalse(buffer.isEmpty(), "Non-empty buffer should be non-empty.");
    assertNotEquals(0, buffer.size(), "Non-empty buffer should not have size 0");
  }

  private SourceCharacter makeCharacter(char c) {
    return new SourceCharacter(new JavaRegexSource(""), new IndexRange(0, 1), c);
  }
}
