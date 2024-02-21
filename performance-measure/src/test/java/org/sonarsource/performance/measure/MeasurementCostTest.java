/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2024 SonarSource SA
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasurementCostTest {

  private static final String JSON_SAMPLE1 = "" +
    "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1383187, \"children\": [\n" +
    "    { \"name\": \"#MeasurementCost_v1\", \"calls\": 17, \"durationNanos\": 24649788, \"children\": [\n" +
    "        { \"name\": \"createChild\", \"calls\": 17, \"durationNanos\": 3604 },\n" +
    "        { \"name\": \"incrementChild\", \"calls\": 17, \"durationNanos\": 2753 },\n" +
    "        { \"name\": \"nanoTime\", \"calls\": 17, \"durationNanos\": 779 },\n" +
    "        { \"name\": \"observationCost\", \"calls\": 17, \"durationNanos\": 814 }\n" +
    "      ]\n" +
    "    },\n" +
    "    { \"name\": \"child1\", \"calls\": 2, \"durationNanos\": 398244 },\n" +
    "    { \"name\": \"child2\", \"calls\": 1, \"durationNanos\": 154332 },\n" +
    "    { \"name\": \"child3\", \"calls\": 0, \"durationNanos\": 0 }\n" +
    "  ]\n" +
    "}";

  private static final String JSON_SAMPLE1_ADJUSTED = "" +
    "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1382555, \"children\": [\n" +
    "    { \"name\": \"#MeasurementCost_subtracted_v1\", \"calls\": 17, \"durationNanos\": 24649788, \"children\": [\n" +
    "        { \"name\": \"createChild\", \"calls\": 17, \"durationNanos\": 3604 },\n" +
    "        { \"name\": \"incrementChild\", \"calls\": 17, \"durationNanos\": 2753 },\n" +
    "        { \"name\": \"nanoTime\", \"calls\": 17, \"durationNanos\": 779 },\n" +
    "        { \"name\": \"observationCost\", \"calls\": 17, \"durationNanos\": 814 }\n" +
    "      ]\n" +
    "    },\n" +
    "    { \"name\": \"child1\", \"calls\": 2, \"durationNanos\": 398150 },\n" +
    "    { \"name\": \"child2\", \"calls\": 1, \"durationNanos\": 154285 },\n" +
    "    { \"name\": \"child3\", \"calls\": 0, \"durationNanos\": 0 }\n" +
    "  ]\n" +
    "}";

  private static final String JSON_SAMPLE2 = "" +
    "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1383187, \"children\": [\n" +
    "    { \"name\": \"#MeasurementCost_subtracted_v1\", \"calls\": 17, \"durationNanos\": 14649788, \"children\": [\n" +
    "        { \"name\": \"createChild\", \"calls\": 17, \"durationNanos\": 2604 },\n" +
    "        { \"name\": \"incrementChild\", \"calls\": 17, \"durationNanos\": 1753 },\n" +
    "        { \"name\": \"nanoTime\", \"calls\": 17, \"durationNanos\": 679 },\n" +
    "        { \"name\": \"observationCost\", \"calls\": 17, \"durationNanos\": 714 }\n" +
    "      ]\n" +
    "    },\n" +
    "    { \"name\": \"child1\", \"calls\": 2, \"durationNanos\": 398244 },\n" +
    "    { \"name\": \"child2\", \"calls\": 1, \"durationNanos\": 154332 }\n" +
    "  ]\n" +
    "}";

  @Test
  void observation_cost_of_v1() {
    DurationMeasure measure = DurationMeasureFiles.fromJson(JSON_SAMPLE1);
    MeasurementCost observationCost = MeasurementCost.observationCostOf(measure);
    assertThat(observationCost).isNotNull();
    assertThat(observationCost.createChild).isEqualTo(3604 / 17);
    assertThat(observationCost.incrementChild).isEqualTo(2753 / 17);
    assertThat(observationCost.nanoTime).isEqualTo(779 / 17);
    assertThat(observationCost.observationCost).isEqualTo(814 / 17);
  }

  @Test
  void observation_cost_of_subtracted_v1() {
    DurationMeasure measure = DurationMeasureFiles.fromJson(JSON_SAMPLE2);
    MeasurementCost observationCost = MeasurementCost.observationCostOf(measure);
    assertThat(observationCost).isNotNull();
    assertThat(observationCost.createChild).isEqualTo(2604 / 17);
    assertThat(observationCost.incrementChild).isEqualTo(1753 / 17);
    assertThat(observationCost.nanoTime).isEqualTo(679 / 17);
    assertThat(observationCost.observationCost).isEqualTo(714 / 17);
  }

  @Test
  void no_observation_cost() {
    DurationMeasure measure = DurationMeasureFiles.fromJson("" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1383187, \"children\": [\n" +
      "    { \"name\": \"child1\", \"calls\": 1, \"durationNanos\": 154332 }\n" +
      "  ]\n" +
      "}");
    MeasurementCost observationCost = MeasurementCost.observationCostOf(measure);
    assertThat(observationCost).isNull();
  }

  @Test
  void no_observation_content_error() {
    DurationMeasure measure = DurationMeasureFiles.fromJson("" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1383187, \"children\": [\n" +
      "    { \"name\": \"#MeasurementCost_v1\", \"calls\": 17, \"durationNanos\": 24649788, \"children\": [\n" +
      "        { \"name\": \"createChild\", \"calls\": 0, \"durationNanos\": 0 }\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}");
    assertThatThrownBy(() -> MeasurementCost.observationCostOf(measure))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Missing incrementChild");
  }

  @Test
  void subtract_observation_cost_v1() {
    DurationMeasure initialMeasure = DurationMeasureFiles.fromJson(JSON_SAMPLE1);
    DurationMeasure measure = MeasurementCost.subtractObservationCost(initialMeasure);
    assertThat(measure)
      .isNotNull()
      .isNotSameAs(initialMeasure);
    assertThat(DurationMeasureFiles.toJson(measure))
      .isEqualTo(JSON_SAMPLE1_ADJUSTED);
  }

  @Test
  void subtract_observation_cost_subtracted_v1() {
    DurationMeasure initialMeasure = DurationMeasureFiles.fromJson(JSON_SAMPLE2);
    DurationMeasure measure = MeasurementCost.subtractObservationCost(initialMeasure);
    assertThat(measure)
      .isNotNull()
      .isNotSameAs(initialMeasure);
    assertThat(DurationMeasureFiles.toJson(measure))
      .isEqualTo(JSON_SAMPLE2);
  }

}
