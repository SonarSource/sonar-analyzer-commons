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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasurementCostTest {

  private static final String JSON_SAMPLE1 = """
    { "name": "root", "calls": 1, "durationNanos": 1383187, "children": [
        { "name": "#MeasurementCost_v1", "calls": 17, "durationNanos": 24649788, "children": [
            { "name": "createChild", "calls": 17, "durationNanos": 3604 },
            { "name": "incrementChild", "calls": 17, "durationNanos": 2753 },
            { "name": "nanoTime", "calls": 17, "durationNanos": 779 },
            { "name": "observationCost", "calls": 17, "durationNanos": 814 }
          ]
        },
        { "name": "child1", "calls": 2, "durationNanos": 398244 },
        { "name": "child2", "calls": 1, "durationNanos": 154332 },
        { "name": "child3", "calls": 0, "durationNanos": 0 }
      ]
    }""";

  private static final String JSON_SAMPLE1_ADJUSTED = """
    { "name": "root", "calls": 1, "durationNanos": 1382555, "children": [
        { "name": "#MeasurementCost_subtracted_v1", "calls": 17, "durationNanos": 24649788, "children": [
            { "name": "createChild", "calls": 17, "durationNanos": 3604 },
            { "name": "incrementChild", "calls": 17, "durationNanos": 2753 },
            { "name": "nanoTime", "calls": 17, "durationNanos": 779 },
            { "name": "observationCost", "calls": 17, "durationNanos": 814 }
          ]
        },
        { "name": "child1", "calls": 2, "durationNanos": 398150 },
        { "name": "child2", "calls": 1, "durationNanos": 154285 },
        { "name": "child3", "calls": 0, "durationNanos": 0 }
      ]
    }""";

  private static final String JSON_SAMPLE2 = """
    { "name": "root", "calls": 1, "durationNanos": 1383187, "children": [
        { "name": "#MeasurementCost_subtracted_v1", "calls": 17, "durationNanos": 14649788, "children": [
            { "name": "createChild", "calls": 17, "durationNanos": 2604 },
            { "name": "incrementChild", "calls": 17, "durationNanos": 1753 },
            { "name": "nanoTime", "calls": 17, "durationNanos": 679 },
            { "name": "observationCost", "calls": 17, "durationNanos": 714 }
          ]
        },
        { "name": "child1", "calls": 2, "durationNanos": 398244 },
        { "name": "child2", "calls": 1, "durationNanos": 154332 }
      ]
    }""";

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
    DurationMeasure measure = DurationMeasureFiles.fromJson("""
      { "name": "root", "calls": 1, "durationNanos": 1383187, "children": [
          { "name": "child1", "calls": 1, "durationNanos": 154332 }
        ]
      }""");
    MeasurementCost observationCost = MeasurementCost.observationCostOf(measure);
    assertThat(observationCost).isNull();
  }

  @Test
  void no_observation_content_error() {
    DurationMeasure measure = DurationMeasureFiles.fromJson("""
      { "name": "root", "calls": 1, "durationNanos": 1383187, "children": [
          { "name": "#MeasurementCost_v1", "calls": 17, "durationNanos": 24649788, "children": [
              { "name": "createChild", "calls": 0, "durationNanos": 0 }
            ]
          }
        ]
      }""");
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
