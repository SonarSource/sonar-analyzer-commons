/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2021 SonarSource SA
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
    assertThat(DurationMeasureFiles.toJson(measure)).isEqualTo("" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 42, \"children\": [\n" +
      "    { \"name\": \"child1\", \"calls\": 2, \"durationNanos\": 7 },\n" +
      "    { \"name\": \"child2\", \"calls\": 3, \"durationNanos\": 15 }\n" +
      "  ]\n" +
      "}");
  }

  @Test
  void from_json() {
    String json = "" +
      "{ \"name\": \"Root\", \"calls\": 13, \"durationNanos\": 205655500131, \"children\": [\n" +
      "    { \"name\": \"Child1\", \"calls\": 13, \"durationNanos\": 168349001508 },\n" +
      "    { \"name\": \"Child2\", \"calls\": 7, \"durationNanos\": 36058273738 }\n" +
      "  ]\n" +
      "}";

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
    String json = "" +
      "{ \"name\": \"Root\", \"calls\": 13, \"durationNanos\": 205655500131, \"children\": [\n" +
      "    { \"name\": \"Child1\", \"calls\": 13, \"durationNanos\": 168349001508 },\n" +
      "    { \"name\": \"Child2\", \"calls\": 7, \"durationNanos\": 36058273738 }\n" +
      "  ]\n" +
      "}";
    DurationMeasure measure = DurationMeasureFiles.fromJson(json);

    String out = DurationMeasureFiles.toStatistics(measure, Collections.emptyMap(), name -> false);
    assertThat(out).isEqualTo("" +
      "Performance (in seconds without observation cost)\n" +
      "• 205.655500131 Root\n" +
      "    • 168.349001508 Child1\n" +
      "    •  36.058273738 Child2\n" +
      "\n");
  }

  @Test
  void to_statistics_rank_with_categories() {
    String json = "" +
      "{ \"name\": \"Root\", \"calls\": 13, \"durationNanos\": 205655500131, \"children\": [\n" +
      "    { \"name\": \"Child1\", \"calls\": 13, \"durationNanos\": 168349001508 },\n" +
      "    { \"name\": \"Child2\", \"calls\": 7, \"durationNanos\": 36058273738 },\n" +
      "    { \"name\": \"Child3\", \"calls\": 2, \"durationNanos\": 543531314 }\n" +
      "  ]\n" +
      "}";
    DurationMeasure measure = DurationMeasureFiles.fromJson(json);
    Map<String, String> categories = new HashMap<>();
    categories.put("Child1", "main-cat");
    categories.put("Child3", "other-cat");

    String out = DurationMeasureFiles.toStatistics(measure, categories, name -> name.startsWith("Child"));
    assertThat(out).isEqualTo("" +
      "Performance (in seconds without observation cost)\n" +
      "• 205.655500131 Root\n" +
      "    • 204.950806560 [ 3 grouped measure(s) ]\n" +
      "\n" +
      "Grouped Entries (in seconds without observation cost)\n" +
      "Total   204.950806560\n" +
      "001/003 168.349001508 Child1                                             (main-cat)\n" +
      "002/003  36.058273738 Child2                                            \n" +
      "003/003   0.543531314 Child3                                             (other-cat)\n");
  }
}
