/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


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


}
