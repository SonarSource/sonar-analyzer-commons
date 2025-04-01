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

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class SetUtilsTest {

  @Test
  public void test_returns_unmodifiable_set() {
    Set<String> set = SetUtils.immutableSetOf();
    assertThatThrownBy(() -> set.add("value")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void test_construct_strings_set() {
    Set<String> set = SetUtils.immutableSetOf("value1", "value2");

    assertThat(set).containsExactlyInAnyOrder("value1", "value2");
  }

  @Test
  public void test_construct_any_set() {
    Set<SomeType> set = SetUtils.immutableSetOf(new SomeType("value1"), new SomeType("value2"));

    assertThat(set).containsExactlyInAnyOrder(new SomeType("value1"), new SomeType("value2"));
  }

  @Test
  public void test_concat_any_set() {
    Set<SomeType> set1 = SetUtils.immutableSetOf(new SomeType("value1"), new SomeType("value2"));
    Set<SomeType> set2 = SetUtils.immutableSetOf(new SomeType("value3"), new SomeType("value4"));

    assertThat(SetUtils.concat(set1, set2))
      .containsExactlyInAnyOrder(new SomeType("value1"), new SomeType("value2"),
        new SomeType("value3"), new SomeType("value4"));
  }

  @Test
  public void test_concat_many_sets() {
    Set<SomeType> set1 = SetUtils.immutableSetOf(new SomeType("value1"), new SomeType("value2"));
    Set<SomeType> set2 = SetUtils.immutableSetOf(new SomeType("value3"), new SomeType("value4"));
    Set<SomeType> set3 = SetUtils.immutableSetOf(new SomeType("value5"), new SomeType("value6"));
    Set<SomeType> set4 = SetUtils.immutableSetOf(new SomeType("value7"), new SomeType("value8"));
    Set<SomeType> set5 = SetUtils.immutableSetOf(new SomeType("value9"), new SomeType("value10"));
    Set<SomeType> set6 = SetUtils.immutableSetOf(new SomeType("value11"), new SomeType("value12"));
    Set<SomeType> set7 = SetUtils.immutableSetOf(new SomeType("value13"), new SomeType("value14"));

    assertThat(SetUtils.concat(set1, set2, set3, set4, set5, set6, set7))
      .containsExactlyInAnyOrder(
        new SomeType("value1"), new SomeType("value2"),
        new SomeType("value3"), new SomeType("value4"),
        new SomeType("value5"), new SomeType("value6"),
        new SomeType("value7"), new SomeType("value8"),
        new SomeType("value9"), new SomeType("value10"),
        new SomeType("value11"), new SomeType("value12"),
        new SomeType("value13"), new SomeType("value14")
      );
  }

  @Test
  public void test_no_difference() {
    Set<String> set1 = SetUtils.immutableSetOf("A", "B", "C");
    Set<String> set2 = SetUtils.immutableSetOf("A", "B", "C");

    assertThat(SetUtils.difference(set1, set2))
      .isEqualTo(Collections.emptySet());
  }

  @Test
  public void test_no_difference_on_left() {
    Set<String> set1 = SetUtils.immutableSetOf("A", "B", "C");
    Set<String> set2 = SetUtils.immutableSetOf("A", "B", "C", "D", "E");

    assertThat(SetUtils.difference(set1, set2))
      .isEqualTo(Collections.emptySet());
  }

  @Test
  public void test_difference() {
    Set<String> set1 = SetUtils.immutableSetOf("A", "B", "C");
    Set<String> set2 = SetUtils.immutableSetOf("A", "B", "C", "D", "E");

    assertThat(SetUtils.difference(set2, set1))
      .containsExactlyInAnyOrder("D", "E");
  }

  @Test
  public void test_get_the_only_element() {
    Set<String> list = Collections.singleton("A");
    assertThat(SetUtils.getOnlyElement(list)).isEqualTo("A");
  }

  @Test
  public void test_get_the_only_element_with_empty_set() {
    Set<String> list = Collections.emptySet();
    assertThatThrownBy(() -> SetUtils.getOnlyElement(list))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Expected set of size 1, but was set of size 0.");
  }

  @Test
  public void test_get_the_only_element_with_too_big_set() {
    Set<String> list = SetUtils.immutableSetOf("A", "B");
    assertThatThrownBy(() -> SetUtils.getOnlyElement(list)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> SetUtils.getOnlyElement(list))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Expected set of size 1, but was set of size 2.");
  }


  private static class SomeType {
    final String value;

    private SomeType(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SomeType value1 = (SomeType) o;
      return Objects.equals(value, value1.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }
}
