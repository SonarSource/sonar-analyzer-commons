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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class ListUtilsTest {

  @Test
  public void test_get_last_String() {
    List<String> list = Arrays.asList("A", "B", "Z");
    assertThat(ListUtils.getLast(list)).isEqualTo("Z");
  }

  @Test
  public void test_construct_any_map() {

    List<SomeType> list = Arrays.asList(new SomeType("value1"), new SomeType("value2"));

    assertThat(ListUtils.getLast(list)).isEqualTo(new SomeType("value2"));
  }

  @Test
  public void test_get_the_only_element() {
    List<String> list = Collections.singletonList("A");
    assertThat(ListUtils.getOnlyElement(list)).isEqualTo("A");
  }

  @Test
  public void test_get_the_only_element_with_empty_list() {
    List<String> list = Collections.emptyList();
    assertThatThrownBy(() -> ListUtils.getOnlyElement(list))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Expected list of size 1, but was list of size 0.");
  }

  @Test
  public void test_get_the_only_element_with_too_big_list() {
    List<String> list = Arrays.asList("A", "B");
    assertThatThrownBy(() -> ListUtils.getOnlyElement(list))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Expected list of size 1, but was list of size 2.");
  }

  @Test
  public void test_reverse() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));

    assertThat(ListUtils.reverse(list1))
      .containsExactly(new SomeType("value2"), new SomeType("value1"));
  }


  @Test
  public void test_concat_any_list() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));
    List<SomeType> list2 = Arrays.asList(new SomeType("value3"), new SomeType("value4"));

    assertThat(ListUtils.concat(list1, list2))
      .containsExactly(new SomeType("value1"), new SomeType("value2"), new SomeType("value3"), new SomeType("value4"));
  }

  @Test
  public void test_concat_many_lists() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));
    List<SomeType> list2 = Arrays.asList(new SomeType("value3"), new SomeType("value4"));
    List<SomeType> list3 = Arrays.asList(new SomeType("value5"), new SomeType("value6"));
    List<SomeType> list4 = Arrays.asList(new SomeType("value7"), new SomeType("value8"));
    List<SomeType> list5 = Arrays.asList(new SomeType("value9"), new SomeType("value10"));
    List<SomeType> list6 = Arrays.asList(new SomeType("value11"), new SomeType("value12"));
    List<SomeType> list7 = Arrays.asList(new SomeType("value13"), new SomeType("value14"));

    assertThat(ListUtils.concat(list1, list2, list3, list4, list5, list6, list7))
      .containsExactly(
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
  public void test_concat_many_iterables() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));
    Set<SomeType> set2 = SetUtils.immutableSetOf(new SomeType("value3"), new SomeType("value4"));
    List<SomeType> list3 = Arrays.asList(new SomeType("value5"), new SomeType("value6"));
    Set<SomeType> set4 = SetUtils.immutableSetOf(new SomeType("value7"), new SomeType("value8"));
    List<SomeType> list5 = Arrays.asList(new SomeType("value9"), new SomeType("value10"));
    Set<SomeType> set6 = SetUtils.immutableSetOf(new SomeType("value11"), new SomeType("value12"));
    List<SomeType> list7 = Arrays.asList(new SomeType("value13"), new SomeType("value14"));

    assertThat(ListUtils.concat(list1, set2, list3, set4, list5, set6, list7))
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
  public void test_alternate() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));
    List<SomeType> list2 = Arrays.asList(new SomeType("value3"), new SomeType("value4"));

    assertThat(ListUtils.alternate(list1, list2))
      .containsExactly(new SomeType("value1"), new SomeType("value3"), new SomeType("value2"), new SomeType("value4"));
  }

  @Test
  public void test_alternate_left_bigger() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"), new SomeType("value3"));
    List<SomeType> list2 = Arrays.asList(new SomeType("value4"), new SomeType("value5"));

    assertThat(ListUtils.alternate(list1, list2))
      .containsExactly(new SomeType("value1"), new SomeType("value4"), new SomeType("value2"), new SomeType("value5"), new SomeType("value3"));
  }

  @Test
  public void test_alternate_right_bigger() {
    List<SomeType> list1 = Arrays.asList(new SomeType("value1"), new SomeType("value2"));
    List<SomeType> list2 = Arrays.asList(new SomeType("value3"), new SomeType("value4"), new SomeType("value5"));

    assertThat(ListUtils.alternate(list1, list2))
      .containsExactly(new SomeType("value1"), new SomeType("value3"), new SomeType("value2"), new SomeType("value4"), new SomeType("value5"));
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
