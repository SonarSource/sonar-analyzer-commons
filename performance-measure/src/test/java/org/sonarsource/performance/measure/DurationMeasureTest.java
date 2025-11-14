/*
 * SonarSource Performance Measure Library
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
package org.sonarsource.performance.measure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationMeasureTest {

  @Test
  void constructor() {
    DurationMeasure child = new DurationMeasure("foo", 2, 123, null);
    assertThat(child.name()).isEqualTo("foo");
    assertThat(child.calls()).isEqualTo(2L);
    assertThat(child.durationNanos()).isEqualTo(123L);
    assertThat(child.hasChildren()).isTrue();
    assertThat(child.get("a")).isNull();
    assertThat(child.children()).isEmpty();

    DurationMeasure parent = new DurationMeasure("bar", 5, 435, toMap(child));
    assertThat(parent.name()).isEqualTo("bar");
    assertThat(parent.calls()).isEqualTo(5L);
    assertThat(parent.durationNanos()).isEqualTo(435L);
    assertThat(parent.hasChildren()).isFalse();
    assertThat(parent.get("foo")).isSameAs(child);
  }

  @Test
  void copy() {
    DurationMeasure measure1Child = new DurationMeasure("foo", 1, 100, null);
    DurationMeasure measure1 = new DurationMeasure("bar", 1, 200, toMap(measure1Child));

    DurationMeasure measure1Copy = measure1.copy();
    assertThat(measure1).isNotSameAs(measure1Copy);
    assertThat(measure1.name()).isEqualTo(measure1Copy.name());
    assertThat(measure1.calls()).isEqualTo(measure1Copy.calls());
    assertThat(measure1.durationNanos()).isEqualTo(measure1Copy.durationNanos());

    DurationMeasure measure1ChildCopy = measure1Copy.get("foo");
    assertThat(measure1Child).isNotSameAs(measure1ChildCopy);
    assertThat(measure1Child.name()).isEqualTo(measure1ChildCopy.name());
    assertThat(measure1Child.calls()).isEqualTo(measure1ChildCopy.calls());
    assertThat(measure1Child.durationNanos()).isEqualTo(measure1ChildCopy.durationNanos());
    assertThat(measure1Child.hasChildren()).isTrue();
  }

  @Test
  void invalid_merge() {
    DurationMeasure measure1 = new DurationMeasure("foo", 1, 100, null);
    DurationMeasure measure2 = new DurationMeasure("bar", 1, 100, null);
    assertThatThrownBy(() -> measure1.merge(measure2))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Incompatible name 'foo' and 'bar'");
  }

  @Test
  void add_or_merge() {
    DurationMeasure root = new DurationMeasure("root", 1, 100, null);
    DurationMeasure child1 = new DurationMeasure("child", 1, 100, null);
    DurationMeasure merge1 = root.addOrMerge(child1);
    assertThat(merge1).isSameAs(child1);
    assertThat(root.get("child")).isSameAs(child1);
    assertThat(merge1.name()).isEqualTo("child");
    assertThat(merge1.calls()).isEqualTo(1);
    assertThat(merge1.durationNanos()).isEqualTo(100);

    DurationMeasure child2 = new DurationMeasure("child", 2, 200, null);
    DurationMeasure merge2 = root.addOrMerge(child2);
    assertThat(merge2).isSameAs(child1);
    assertThat(root.get("child")).isSameAs(child1);
    assertThat(merge2.name()).isEqualTo("child");
    assertThat(merge2.calls()).isEqualTo(3);
    assertThat(merge2.durationNanos()).isEqualTo(300);
  }

  @Test
  void sorted_children() {
    DurationMeasure root = new DurationMeasure("root", 1, 100, null);
    Supplier<List<String>> extractChildNames = () -> root.sortedChildren().stream()
      .map(DurationMeasure::name).collect(Collectors.toList());

    assertThat(extractChildNames.get()).isEmpty();

    root.addOrMerge(new DurationMeasure("b"));
    assertThat(extractChildNames.get()).contains("b");

    root.addOrMerge(new DurationMeasure("a"));
    assertThat(extractChildNames.get()).contains("a", "b");

    root.addOrMerge(new DurationMeasure("c"));
    assertThat(extractChildNames.get()).contains("a", "b", "c");
  }

  @Test
  void recursive_merge_on_upper_level() {
    String json = "" +
      "{ \"name\": \"Root\", \"calls\": 1, \"durationNanos\": 1234, \"children\": [\n" +
      "    { \"name\": \"A\", \"calls\": 5, \"durationNanos\": 622, \"children\": [\n" +
      "        { \"name\": \"Cache\", \"calls\": 3, \"durationNanos\": 300 }\n" +
      "      ]\n" +
      "    },\n" +
      "    { \"name\": \"B\", \"calls\": 3, \"durationNanos\": 321, \"children\": [\n" +
      "        { \"name\": \"Cache\", \"calls\": 2, \"durationNanos\": 100 }\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}";

    DurationMeasure measure = DurationMeasureFiles.fromJson(json);
    measure.recursiveMergeOnUpperLevel("Cache");
    assertThat(DurationMeasureFiles.toJson(measure)).isEqualTo("" +
      "{ \"name\": \"Root\", \"calls\": 1, \"durationNanos\": 1234, \"children\": [\n" +
      "    { \"name\": \"A\", \"calls\": 5, \"durationNanos\": 322 },\n" +
      "    { \"name\": \"B\", \"calls\": 3, \"durationNanos\": 221 },\n" +
      "    { \"name\": \"Cache\", \"calls\": 5, \"durationNanos\": 400 }\n" +
      "  ]\n" +
      "}");
  }

  private static Map<String, DurationMeasure> toMap(DurationMeasure... measures) {
    return Arrays.stream(measures).collect(Collectors.toMap(DurationMeasure::name, Function.identity()));
  }

}
