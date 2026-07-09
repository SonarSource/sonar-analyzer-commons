/*
 * SonarSource Performance Measure Library
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationMeasureFilesTest {

  @Test
  void no_child() {
    DurationMeasure measure = new DurationMeasure("root", 1, 42, null);
    assertThat(DurationMeasureFiles.toJson(measure)).isEqualTo("" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 42 }");
  }

  @Test
  void with_children() {
    Map<String, DurationMeasure> childrenMap = new HashMap<>();
    childrenMap.put("child2", new DurationMeasure("child2", 3, 15, null));
    childrenMap.put("child1", new DurationMeasure("child1", 2, 7, null));
    DurationMeasure measure = new DurationMeasure("root", 1, 42, childrenMap);
    assertThat(DurationMeasureFiles.toJson(measure)).isEqualTo("""
      { "name": "root", "calls": 1, "durationNanos": 42, "children": [
          { "name": "child1", "calls": 2, "durationNanos": 7 },
          { "name": "child2", "calls": 3, "durationNanos": 15 }
        ]
      }""");
  }

  @Test
  void from_json() {
    String json = """
      { "name": "Root", "calls": 13, "durationNanos": 205655500131, "children": [
          { "name": "Child1", "calls": 13, "durationNanos": 168349001508 },
          { "name": "Child2", "calls": 7, "durationNanos": 36058273738 }
        ]
      }""";

    DurationMeasure measure = DurationMeasureFiles.fromJson(json);
    assertThat(DurationMeasureFiles.toJson(measure)).isEqualTo(json);
  }

  @Test
  void from_json_ignore_empty_children_array() {
    String json = "{ \"name\": \"Foo\", \"calls\": 1, \"durationNanos\": 20, \"children\": [] }";
    DurationMeasure measure = DurationMeasureFiles.fromJson(json);
    assertThat(DurationMeasureFiles.toJson(measure))
      .isEqualTo("{ \"name\": \"Foo\", \"calls\": 1, \"durationNanos\": 20 }");
  }

  @Test
  void to_statistics_without_group() {
    String json = """
      { "name": "Root", "calls": 13, "durationNanos": 205655500131, "children": [
          { "name": "Child1", "calls": 13, "durationNanos": 168349001508 },
          { "name": "Child2", "calls": 7, "durationNanos": 36058273738 }
        ]
      }""";
    DurationMeasure measure = DurationMeasureFiles.fromJson(json);

    String out = DurationMeasureFiles.toStatistics(measure, Collections.emptyMap(), name -> false);
    assertThat(out).isEqualTo("""
      Performance (in seconds without observation cost)
      • 205.655500131 Root
          • 168.349001508 Child1
          •  36.058273738 Child2

      """);
  }

  @Test
  void to_statistics_rank_with_categories() {
    String json = """
      { "name": "Root", "calls": 13, "durationNanos": 205655500131, "children": [
          { "name": "Child1", "calls": 13, "durationNanos": 168349001508 },
          { "name": "Child2", "calls": 7, "durationNanos": 36058273738 },
          { "name": "Child3", "calls": 2, "durationNanos": 543531314 }
        ]
      }""";
    DurationMeasure measure = DurationMeasureFiles.fromJson(json);
    Map<String, String> categories = new HashMap<>();
    categories.put("Child1", "main-cat");
    categories.put("Child3", "other-cat");

    String out = DurationMeasureFiles.toStatistics(measure, categories, name -> name.startsWith("Child"));
    assertThat(out).isEqualTo("""
      Performance (in seconds without observation cost)
      • 205.655500131 Root
          • 204.950806560 [ 3 grouped measure(s) ]

      Grouped Entries (in seconds without observation cost)
      Total   204.950806560
      001/003 168.349001508 Child1                                             (main-cat)
      002/003  36.058273738 Child2                                           \s
      003/003   0.543531314 Child3                                             (other-cat)
      """);
  }
}
