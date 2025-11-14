/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TextSpanTest {

  @Test
  public void emptyTextSpan() {
    // same line, same offset
    assertThat(new TextSpan(42, 2, 42, 2).isEmpty()).isTrue();
    // different offset
    assertThat(new TextSpan(42, 2, 42, 5).isEmpty()).isFalse();
    // different lines, different offset
    assertThat(new TextSpan(42, 2, 43, 5).isEmpty()).isFalse();
    // different lines, same offset
    assertThat(new TextSpan(42, 2, 43, 2).isEmpty()).isFalse();
  }

  @Test
  public void equals_hash_code() {
    assertThat(new TextSpan(42, 2, 42, 2)).isEqualTo(new TextSpan(42, 2, 42, 2));
    assertThat(new TextSpan(42, 2, 42, 2)).isNotEqualTo(new TextSpan(42, 1, 42, 2));
    assertThat(new TextSpan(42, 2, 42, 2)).hasSameHashCodeAs(new TextSpan(42, 2, 42, 2));
    assertThat(new TextSpan(42, 2, 42, 2).hashCode()).isNotEqualTo(new TextSpan(42, 1, 42, 2).hashCode());
  }

  @Test
  public void textSpanOnLine() {
    assertThat(new TextSpan(42).onLine()).isTrue();
    assertThat(new TextSpan(0, -1, 0, 5).onLine()).isTrue();
    assertThat(new TextSpan(0, 2, 0, 2).onLine()).isFalse();
  }

  @Test
  public void test_text_span_equals_hashcode_tostring() {
    TextSpan textSpan1 = new TextSpan(1, 2, 3, 4);
    assertTextSpan(textSpan1, 1, 2, 3, 4);

    TextSpan textSpanSameAs1 = new TextSpan(1, 2, 3, 4);
    TextSpan textSpan2 = new TextSpan(2, 3, 4, 5);
    assertTextSpan(textSpan2, 2, 3, 4, 5);

    assertThat(textSpan1.equals(null)).isFalse();
    assertThat(textSpan1.equals(new Object())).isFalse();
    assertThat(textSpan1.equals(textSpan2)).isFalse();

    assertThat(textSpan1.equals(textSpan1)).isTrue();
    assertThat(textSpan1.equals(textSpanSameAs1)).isTrue();

    assertThat(textSpan1.hashCode()).isNotEqualTo(textSpan2.hashCode());
    assertThat(textSpan1.hashCode()).hasSameHashCodeAs(textSpan1.hashCode());
    assertThat(textSpan1.hashCode()).hasSameHashCodeAs(textSpanSameAs1.hashCode());

    assertThat(new TextSpan(1, 2, 3, 4).equals(new TextSpan(1, 99, 3, 4))).isFalse();
    assertThat(new TextSpan(1, 2, 3, 4).equals(new TextSpan(1, 2, 99, 4))).isFalse();
    assertThat(new TextSpan(1, 2, 3, 4).equals(new TextSpan(1, 2, 3, 99))).isFalse();

    assertThat(textSpan1).hasToString("(1:2)-(3:4)");
  }

  public static void assertTextSpan(TextSpan textSpan, int startLine, int startColumn, int endLine, int endColumn) {
    assertThat(textSpan.startLine).as("Start line").isEqualTo(startLine);
    assertThat(textSpan.startCharacter).as("Start character").isEqualTo(startColumn);
    assertThat(textSpan.endLine).as("End line").isEqualTo(endLine);
    assertThat(textSpan.endCharacter).as("End character").isEqualTo(endColumn);
  }

}
