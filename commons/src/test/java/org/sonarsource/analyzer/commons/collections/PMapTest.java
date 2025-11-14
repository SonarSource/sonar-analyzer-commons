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

public final class PMapTest {
  @Test
  public void lastDupeWins() {
    var map = PMap.of(
      "banana", 1,
      "apple", 2,
      "banana", 42
    );

    assertThat(getSize(map.entries())).isEqualTo(2);
    assertThat(map.get("banana")).isEqualTo(42);
  }

  @Test
  public void of0() {
    assertThat(PMap.of()).isSameAs(PCollections.emptyMap());
  }

  @Test
  public void of1() {
    var map = PMap.of(
      "partridge in a pear tree", 1
    );

    assertThat(getSize(map.entries())).isEqualTo(1);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
  }

  @Test
  public void of2() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2
    );

    assertThat(getSize(map.entries())).isEqualTo(2);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
  }

  @Test
  public void of3() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3
    );

    assertThat(getSize(map.entries())).isEqualTo(3);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
  }

  @Test
  public void of4() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4
    );

    assertThat(getSize(map.entries())).isEqualTo(4);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
  }

  @Test
  public void of5() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5
    );

    assertThat(getSize(map.entries())).isEqualTo(5);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
  }

  @Test
  public void of6() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5,
      "geese a-laying", 6
    );

    assertThat(getSize(map.entries())).isEqualTo(6);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
    assertThat(map.get("geese a-laying")).isEqualTo(6);
  }

  @Test
  public void of7() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5,
      "goose a-laying", 6,
      "swan a-swimming", 7
    );

    assertThat(getSize(map.entries())).isEqualTo(7);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
    assertThat(map.get("goose a-laying")).isEqualTo(6);
    assertThat(map.get("swan a-swimming")).isEqualTo(7);
  }

  @Test
  public void of8() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5,
      "goose a-laying", 6,
      "swan a-swimming", 7,
      "maid a-milking", 8
    );

    assertThat(getSize(map.entries())).isEqualTo(8);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
    assertThat(map.get("goose a-laying")).isEqualTo(6);
    assertThat(map.get("swan a-swimming")).isEqualTo(7);
    assertThat(map.get("maid a-milking")).isEqualTo(8);
  }

  @Test
  public void of9() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5,
      "goose a-laying", 6,
      "swan a-swimming", 7,
      "maid a-milking", 8,
      "lady dancing", 9
    );

    assertThat(getSize(map.entries())).isEqualTo(9);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
    assertThat(map.get("goose a-laying")).isEqualTo(6);
    assertThat(map.get("swan a-swimming")).isEqualTo(7);
    assertThat(map.get("maid a-milking")).isEqualTo(8);
    assertThat(map.get("lady dancing")).isEqualTo(9);
  }

  @Test
  public void of10() {
    var map = PMap.of(
      "partridge in a pear tree", 1,
      "turtle dove", 2,
      "French hen", 3,
      "calling bird", 4,
      "gold ring", 5,
      "goose a-laying", 6,
      "swan a-swimming", 7,
      "maid a-milking", 8,
      "lady dancing", 9,
      "lord a-leaping", 10
    );

    assertThat(getSize(map.entries())).isEqualTo(10);
    assertThat(map.get("partridge in a pear tree")).isEqualTo(1);
    assertThat(map.get("turtle dove")).isEqualTo(2);
    assertThat(map.get("French hen")).isEqualTo(3);
    assertThat(map.get("calling bird")).isEqualTo(4);
    assertThat(map.get("gold ring")).isEqualTo(5);
    assertThat(map.get("goose a-laying")).isEqualTo(6);
    assertThat(map.get("swan a-swimming")).isEqualTo(7);
    assertThat(map.get("maid a-milking")).isEqualTo(8);
    assertThat(map.get("lady dancing")).isEqualTo(9);
    assertThat(map.get("lord a-leaping")).isEqualTo(10);
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
