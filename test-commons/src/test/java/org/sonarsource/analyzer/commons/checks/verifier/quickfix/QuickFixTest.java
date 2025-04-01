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

import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QuickFixTest {

  @Test
  public void test_build_quick_fix_without_edits() {
    QuickFix quickFix = QuickFix.newQuickFix("description").build();
    assertThat(quickFix.getDescription()).isEqualTo("description");
    assertThat(quickFix.getTextEdits()).isEmpty();
  }

  @Test
  public void test_build_quick_fix_with_formatted_message() {
    QuickFix quickFix = QuickFix.newQuickFix("description %s %d", "yolo", 42).build();
    assertThat(quickFix.getDescription()).isEqualTo("description yolo 42");
    assertThat(quickFix.getTextEdits()).isEmpty();
  }

  @Test
  public void test_build_quick_fix_with_edits() {
    TextEdit edit = TextEdit.removeTextSpan(TextEdit.textSpan(1, 2, 3, 4));
    QuickFix quickFix = QuickFix.newQuickFix("description")
      .addTextEdits(Collections.singletonList(edit))
      .build();
    assertThat(quickFix.getDescription()).isEqualTo("description");
    assertThat(quickFix.getTextEdits()).hasSize(1).containsExactly(edit);
  }

  @Test
  public void test_builder_with_edits() {
    QuickFix quickFix = QuickFix.newQuickFix("description")
      .addTextEdit(1, 2, 3, 4, "")
      .build();
    assertThat(quickFix.getDescription()).isEqualTo("description");
    assertThat(quickFix.getTextEdits()).hasSize(1);
    assertThat(quickFix.getTextEdits().get(0).getTextSpan()).isEqualTo(
      new TextSpan(1, 2, 3, 4)
    );
  }

  @Test
  public void test_can_set_edits_multiples_times() {
    TextEdit edit1 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 2, 3, 4));
    TextEdit edit2 = TextEdit.removeTextSpan(TextEdit.textSpan(2, 3, 4, 5));
    QuickFix quickFix = QuickFix.newQuickFix("description")
      .addTextEdits(Collections.singletonList(edit1))
      .addTextEdit(edit2)
      .build();
    assertThat(quickFix.getDescription()).isEqualTo("description");
    assertThat(quickFix.getTextEdits()).hasSize(2).containsExactly(edit1, edit2);
    assertThat(quickFix).hasToString("[[QuickFix: description]]\n" +
      " Edits: \n" +
      "(1:2)-(3:4) -> {{}}\n" +
      "(2:3)-(4:5) -> {{}}");
  }

  @Test
  public void reverseSortEdits_sorts_as_expected() {
    TextEdit edit1 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 2, 3, 4));
    TextEdit edit2 = TextEdit.removeTextSpan(TextEdit.textSpan(2, 3, 4, 5));

    assert_text_edits_are_ordered_as_expected(
      Collections.emptyList(),
      Collections.emptyList()
    );

    assert_text_edits_are_ordered_as_expected(
      Collections.singletonList(edit1),
      Collections.singletonList(edit1)
    );

    assert_text_edits_are_ordered_as_expected(
      List.of(edit1, edit1),
      List.of(edit1, edit1)
    );

    assert_text_edits_are_ordered_as_expected(
      List.of(edit1, edit2),
      List.of(edit2, edit1)
    );

    TextEdit edit3 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 1, 1, 2));
    TextEdit edit4 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 1, 2, 1));
    assert_text_edits_are_ordered_as_expected(
      List.of(edit3, edit4),
      List.of(edit4, edit3)
    );

    TextEdit edit5 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 1, 1, 1));
    TextEdit edit6 = TextEdit.removeTextSpan(TextEdit.textSpan(1, 2, 1, 3));
    assert_text_edits_are_ordered_as_expected(
      List.of(edit5, edit6),
      List.of(edit6, edit5)
    );
  }

  void assert_text_edits_are_ordered_as_expected(List<TextEdit> editsToAdd, List<TextEdit> expectedOrder) {
    QuickFix quickFix = QuickFix.newQuickFix("Text edits should be ordered as expected")
      .addTextEdits(editsToAdd)
      .reverseSortEdits()
      .build();
    assertThat(quickFix.getTextEdits()).isEqualTo(expectedOrder);
  }

}
