/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons.collections;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class PSetTest {
  @Test
  public void dupesAreIgnored() {
    var set = PSet.of(
      "banana",
      "apple",
      "banana"
    );

    assertThat(getSize(set)).isEqualTo(2);
  }

  @Test
  public void of0() {
    assertThat(PSet.of()).isSameAs(PCollections.emptySet());
  }

  @Test
  public void of1() {
    var set = PSet.of(
      "partridge in a pear tree"
    );

    assertThat(getSize(set)).isEqualTo(1);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
  }

  @Test
  public void of2() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove"
    );

    assertThat(getSize(set)).isEqualTo(2);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
  }

  @Test
  public void of3() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen"
    );

    assertThat(getSize(set)).isEqualTo(3);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
  }

  @Test
  public void of4() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird"
    );

    assertThat(getSize(set)).isEqualTo(4);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
  }

  @Test
  public void of5() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring"
    );

    assertThat(getSize(set)).isEqualTo(5);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
  }

  @Test
  public void of6() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "geese a-laying"
    );

    assertThat(getSize(set)).isEqualTo(6);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
    assertThat(set.contains("geese a-laying")).isTrue();
  }

  @Test
  public void of7() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming"
    );

    assertThat(getSize(set)).isEqualTo(7);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
    assertThat(set.contains("goose a-laying")).isTrue();
    assertThat(set.contains("swan a-swimming")).isTrue();
  }

  @Test
  public void of8() {
    var set = PSet.of(
      "partridge in a pear tree",
      "turtle dove",
      "French hen",
      "calling bird",
      "gold ring",
      "goose a-laying",
      "swan a-swimming",
      "maid a-milking"
    );

    assertThat(getSize(set)).isEqualTo(8);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
    assertThat(set.contains("goose a-laying")).isTrue();
    assertThat(set.contains("swan a-swimming")).isTrue();
    assertThat(set.contains("maid a-milking")).isTrue();
  }

  @Test
  public void of9() {
    var set = PSet.of(
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

    assertThat(getSize(set)).isEqualTo(9);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
    assertThat(set.contains("goose a-laying")).isTrue();
    assertThat(set.contains("swan a-swimming")).isTrue();
    assertThat(set.contains("maid a-milking")).isTrue();
    assertThat(set.contains("lady dancing")).isTrue();
  }

  @Test
  public void of10() {
    var set = PSet.of(
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

    assertThat(getSize(set)).isEqualTo(10);
    assertThat(set.contains("partridge in a pear tree")).isTrue();
    assertThat(set.contains("turtle dove")).isTrue();
    assertThat(set.contains("French hen")).isTrue();
    assertThat(set.contains("calling bird")).isTrue();
    assertThat(set.contains("gold ring")).isTrue();
    assertThat(set.contains("goose a-laying")).isTrue();
    assertThat(set.contains("swan a-swimming")).isTrue();
    assertThat(set.contains("maid a-milking")).isTrue();
    assertThat(set.contains("lady dancing")).isTrue();
    assertThat(set.contains("lord a-leaping")).isTrue();
  }

  private static <T> int getSize(Iterable<T> iterable) {
    int size = 0;
    var iterator = iterable.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      size++;
    }
    return size;
  }
}
