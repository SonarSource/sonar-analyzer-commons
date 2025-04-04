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
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;

public class CharacterBuffer {

  private static final int RESIZE_FACTOR = 2;

  private SourceCharacter[] contents;

  private int startIndex = 0;

  private int size = 0;

  public CharacterBuffer(int initialCapacity) {
    contents = new SourceCharacter[initialCapacity];
  }

  public SourceCharacter get(int index) {
    if (index >= size) {
      throw new IndexOutOfBoundsException("Invalid index " + index + " for buffer of size " + size + ".");
    }
    return contents[(startIndex + index) % contents.length];
  }

  public void add(SourceCharacter character) {
    if (size + 1 == contents.length) {
      resize(contents.length * RESIZE_FACTOR);
    }
    contents[(startIndex + size) % contents.length] = character;
    size++;
  }

  public void removeFirst() {
    if (size == 0) {
      throw new NoSuchElementException("Trying to delete from empty buffer.");
    }
    startIndex++;
    if (startIndex == contents.length) {
      startIndex = 0;
    }
    size--;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int size() {
    return size;
  }

  private void resize(int newCapacity) {
    SourceCharacter[] newContents = new SourceCharacter[newCapacity];
    System.arraycopy(contents, startIndex, newContents, 0, contents.length - startIndex);
    System.arraycopy(contents, 0, newContents, contents.length - startIndex, startIndex);
    contents = newContents;
    startIndex = 0;
  }

}
