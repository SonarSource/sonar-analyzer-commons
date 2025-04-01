/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


public class TextEditTest {

  @Test
  public void testGetTextSpan() {
    TextSpan textSpan = new TextSpan(1, 1, 1, 1);
    TextEdit textEdit = TextEdit.replaceTextSpan(textSpan, "replacement");
    assertEquals(textSpan, textEdit.getTextSpan());
  }

  @Test
  public void testGetReplacement() {
    String replacement = "replacement";
    TextEdit textEdit = TextEdit.insertAtPosition(1, 1, replacement);
    assertEquals(replacement, textEdit.getReplacement());
  }

  @Test
  public void testRemoveTextSpan() {
    TextSpan textSpan = new TextSpan(1, 1, 1, 1);
    TextEdit textEdit = TextEdit.removeTextSpan(textSpan);
    assertEquals(textSpan, textEdit.getTextSpan());
    assertEquals("", textEdit.getReplacement());
  }

  @Test
  public void testReplaceTextSpan() {
    TextSpan textSpan = new TextSpan(1, 1, 1, 1);
    String replacement = "replacement";
    TextEdit textEdit = TextEdit.replaceTextSpan(textSpan, replacement);
    assertEquals(textSpan, textEdit.getTextSpan());
    assertEquals(replacement, textEdit.getReplacement());
  }

  @Test
  public void testInsertAtPosition() {
    int line = 1;
    int column = 1;
    String addition = "addition";
    TextEdit textEdit = TextEdit.insertAtPosition(line, column, addition);
    assertEquals(new TextSpan(line, column, line, column), textEdit.getTextSpan());
    assertEquals(addition, textEdit.getReplacement());
  }

  @Test
  public void testPosition() {
    int line = 1;
    int column = 1;
    TextSpan textSpan = TextEdit.position(line, column);
    assertEquals(new TextSpan(line, column, line, column), textSpan);
  }

  @Test
  public void testEqualsAndHashCode(){
    TextSpan textSpan = new TextSpan(1, 1, 1, 1);
    TextEdit textEdit1 = TextEdit.replaceTextSpan(textSpan, "replacement");
    TextEdit textEdit2 = TextEdit.replaceTextSpan(textSpan, "replacement");
    TextEdit textEdit3 = TextEdit.replaceTextSpan(textSpan, "replacement2");
    TextEdit textEdit4 = TextEdit.replaceTextSpan(new TextSpan(1, 2, 1, 2), "replacement");
    assertEquals(textEdit1, textEdit2);
    assertEquals(textEdit1.hashCode(), textEdit2.hashCode());
    assertNotEquals(textEdit1, textEdit3);
    assertNotEquals(textEdit1.hashCode(), textEdit3.hashCode());
    assertNotEquals(textEdit2, new Object());
    assertNotEquals(textEdit4, textEdit1);
  }

}
