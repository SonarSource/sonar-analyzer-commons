/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons.collections;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class PStackTest {
  @Test
  public void of0() {
    assertThat(PStack.of()).isSameAs(PCollections.emptyStack());
  }

  @Test
  public void of1() {
    var stack = PStack.of(
      "partridge in a pear tree"
    );

    assertThat(stack.size()).isEqualTo(1);
    assertThat(stack.peek(0)).isEqualTo("partridge in a pear tree");
  }

  @Test
  public void of2() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove"
    );

    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.peek(1)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(0)).isEqualTo("turtle dove");
  }

  @Test
  public void of3() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen"
    );

    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.peek(2)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(1)).isEqualTo("turtle dove");
    assertThat(stack.peek(0)).isEqualTo("French hen");
  }

  @Test
  public void of4() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird"
    );

    assertThat(stack.size()).isEqualTo(4);
    assertThat(stack.peek(3)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(2)).isEqualTo("turtle dove");
    assertThat(stack.peek(1)).isEqualTo("French hen");
    assertThat(stack.peek(0)).isEqualTo("calling bird");
  }

  @Test
  public void of5() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring"
    );

    assertThat(stack.size()).isEqualTo(5);
    assertThat(stack.peek(4)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(3)).isEqualTo("turtle dove");
    assertThat(stack.peek(2)).isEqualTo("French hen");
    assertThat(stack.peek(1)).isEqualTo("calling bird");
    assertThat(stack.peek(0)).isEqualTo("gold ring");
  }

  @Test
  public void of6() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "geese a-laying"
    );

    assertThat(stack.size()).isEqualTo(6);
    assertThat(stack.peek(5)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(4)).isEqualTo("turtle dove");
    assertThat(stack.peek(3)).isEqualTo("French hen");
    assertThat(stack.peek(2)).isEqualTo("calling bird");
    assertThat(stack.peek(1)).isEqualTo("gold ring");
    assertThat(stack.peek(0)).isEqualTo("geese a-laying");
  }

  @Test
  public void of7() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming"
    );

    assertThat(stack.size()).isEqualTo(7);
    assertThat(stack.peek(6)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(5)).isEqualTo("turtle dove");
    assertThat(stack.peek(4)).isEqualTo("French hen");
    assertThat(stack.peek(3)).isEqualTo("calling bird");
    assertThat(stack.peek(2)).isEqualTo("gold ring");
    assertThat(stack.peek(1)).isEqualTo("goose a-laying");
    assertThat(stack.peek(0)).isEqualTo("swan a-swimming");
  }

  @Test
  public void of8() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming",
      "maid a-milking"
    );

    assertThat(stack.size()).isEqualTo(8);
    assertThat(stack.peek(7)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(6)).isEqualTo("turtle dove");
    assertThat(stack.peek(5)).isEqualTo("French hen");
    assertThat(stack.peek(4)).isEqualTo("calling bird");
    assertThat(stack.peek(3)).isEqualTo("gold ring");
    assertThat(stack.peek(2)).isEqualTo("goose a-laying");
    assertThat(stack.peek(1)).isEqualTo("swan a-swimming");
    assertThat(stack.peek(0)).isEqualTo("maid a-milking");
  }

  @Test
  public void of9() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming",
      "maid a-milking",
      "lady dancing"
    );

    assertThat(stack.size()).isEqualTo(9);
    assertThat(stack.peek(8)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(7)).isEqualTo("turtle dove");
    assertThat(stack.peek(6)).isEqualTo("French hen");
    assertThat(stack.peek(5)).isEqualTo("calling bird");
    assertThat(stack.peek(4)).isEqualTo("gold ring");
    assertThat(stack.peek(3)).isEqualTo("goose a-laying");
    assertThat(stack.peek(2)).isEqualTo("swan a-swimming");
    assertThat(stack.peek(1)).isEqualTo("maid a-milking");
    assertThat(stack.peek(0)).isEqualTo("lady dancing");
  }

  @Test
  public void of10() {
    var stack = PStack.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming",
      "maid a-milking",
      "lady dancing",
      "lord a-leaping"
    );

    assertThat(stack.size()).isEqualTo(10);
    assertThat(stack.peek(9)).isEqualTo("partridge in a pear tree");
    assertThat(stack.peek(8)).isEqualTo("turtle dove");
    assertThat(stack.peek(7)).isEqualTo("French hen");
    assertThat(stack.peek(6)).isEqualTo("calling bird");
    assertThat(stack.peek(5)).isEqualTo("gold ring");
    assertThat(stack.peek(4)).isEqualTo("goose a-laying");
    assertThat(stack.peek(3)).isEqualTo("swan a-swimming");
    assertThat(stack.peek(2)).isEqualTo("maid a-milking");
    assertThat(stack.peek(1)).isEqualTo("lady dancing");
    assertThat(stack.peek(0)).isEqualTo("lord a-leaping");
  }
}
