/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.performance.measure;

/*
 * SonarQube Java
 * Copyright (C) 2012-2021 SonarSource SA
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.performance.measure.log.StringLogger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.sonarsource.performance.measure.PerformanceMeasure.ensureParentDirectoryExists;

class PerformanceMeasureTest {

  public List<String> arrayList = new ArrayList<>();

  private final StringLogger logger = StringLogger.initialize(StringLogger.Level.INFO);

  public long testTimeNanos = System.nanoTime();

  @BeforeEach
  void beforeEach() {
    PerformanceMeasure.overrideTimeSupplierForTest(() -> ++testTimeNanos);
    PerformanceMeasure.deactivateAndClearCurrentMeasureForTest();
  }

  @Test
  void not_active_system_property() {
    logger.setLevel(StringLogger.Level.DEBUG);
    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.reportBuilder()
      .activate(false)
      .toFile(null)
      .start("root");
    PerformanceMeasure.Duration duration_1_1 = PerformanceMeasure.start("cat-1");
    duration_1_1.stop();
    PerformanceMeasure.Duration duration_1_2 = PerformanceMeasure.start(arrayList);
    duration_1_2.stop();
    duration_1.stop();
    assertThat(logger.logs()).isEmpty();
  }

  @Test
  void active_system_property_without_performance_file() {
    logger.setLevel(StringLogger.Level.DEBUG);
    PerformanceMeasure.Duration duration = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile("")
      .start("root");

    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.start("cat-1");
    testTimeNanos += 47_442_121L;
    PerformanceMeasure.Duration duration_1_1 = PerformanceMeasure.start("sub-cat-1");
    testTimeNanos += 234_453_958L;
    duration_1_1.stop();
    testTimeNanos += 123_183_297L;
    PerformanceMeasure.Duration duration_1_2 = PerformanceMeasure.start("sub-cat-2");
    testTimeNanos += 700_123_345L;
    duration_1_2.stop();
    testTimeNanos += 90_392_411L;
    duration_1.stop();

    PerformanceMeasure.Duration duration_2 = PerformanceMeasure.start("cat-2");
    PerformanceMeasure.Duration duration_2_1 = PerformanceMeasure.start(arrayList);
    testTimeNanos += 32_553_812L;
    duration_2_1.stop();
    testTimeNanos += 36_432_090L;
    // intentionally call "start()" without "stop()" to simulate a badly handled exception
    PerformanceMeasure.start("sub-cat-2");
    testTimeNanos += 1_900_000L;
    duration_2.stop();
    // stop can be called twice by mistake without consequences
    duration_2.stop();

    duration.stop();
    assertThat(logger.logs()).isEqualTo("" +
      "[DEBUG] Performance Measures:\n" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1266481046, \"children\": [\n" +
      "    { \"name\": \"cat-1\", \"calls\": 1, \"durationNanos\": 1195595137, \"children\": [\n" +
      "        { \"name\": \"sub-cat-1\", \"calls\": 1, \"durationNanos\": 234453959 },\n" +
      "        { \"name\": \"sub-cat-2\", \"calls\": 1, \"durationNanos\": 700123346 }\n" +
      "      ]\n" +
      "    },\n" +
      "    { \"name\": \"cat-2\", \"calls\": 1, \"durationNanos\": 70885906, \"children\": [\n" +
      "        { \"name\": \"ArrayList\", \"calls\": 1, \"durationNanos\": 32553813 },\n" +
      "        { \"name\": \"sub-cat-2\", \"calls\": 0, \"durationNanos\": 0 }\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}\n");
  }

  @Test
  void start_and_stop_after_a_report_should_be_ignored() {
    logger.setLevel(StringLogger.Level.DEBUG);

    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .start("root");
    duration_1.stop();

    PerformanceMeasure.Duration duration_1_1 = PerformanceMeasure.start("cat-1");
    duration_1_1.stop();

    assertThat(logger.logs()).isEqualTo("" +
      "[DEBUG] Performance Measures:\n" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1 }\n");
  }

  @Test
  void append_measurement_cost(@TempDir File workDir) throws IOException {
    Path performanceFile = workDir.toPath().resolve("performance.measure.json");
    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile(performanceFile.toString())
      .appendMeasurementCost()
      .start("root");
    testTimeNanos += 1_382_190L;
    duration_1.stop();

    String jsonContent = new String(Files.readAllBytes(performanceFile), UTF_8);
    assertThat(jsonContent).isEqualTo("" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 1383187, \"children\": [\n" +
      "    { \"name\": \"#MeasurementCost_v1\", \"calls\": 1, \"durationNanos\": 995, \"children\": [\n" +
      "        { \"name\": \"createChild\", \"calls\": 1, \"durationNanos\": 3 },\n" +
      "        { \"name\": \"incrementChild\", \"calls\": 1, \"durationNanos\": 3 },\n" +
      "        { \"name\": \"nanoTime\", \"calls\": 1, \"durationNanos\": 1 },\n" +
      "        { \"name\": \"observationCost\", \"calls\": 1, \"durationNanos\": 1 }\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}");

    assertThat(logger.logs()).isEqualTo("" +
      "[INFO] Saving performance measures into: " + performanceFile + "\n");
  }

  @Test
  void merge_performance_measures(@TempDir File workDir) throws IOException {
    logger.setLevel(StringLogger.Level.DEBUG);
    Path performanceFile = workDir.toPath().resolve("performance.measure.json");
    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile(performanceFile.toString())
      .start("root");
    testTimeNanos += 1_382_190L;

    PerformanceMeasure.Duration duration_1_1 = PerformanceMeasure.start("child-1");
    testTimeNanos += 47_442_121L;
    duration_1_1.stop();

    testTimeNanos += 2_456_121L;
    duration_1.stop();

    assertThat(logger.logs()).isEqualTo("" +
      "[DEBUG] Performance Measures:\n" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 51280435, \"children\": [\n" +
      "    { \"name\": \"child-1\", \"calls\": 1, \"durationNanos\": 47442122 }\n" +
      "  ]\n" +
      "}\n" +
      "[INFO] Saving performance measures into: " + performanceFile + "\n");

    String jsonContent = new String(Files.readAllBytes(performanceFile), UTF_8);
    assertThat(jsonContent).isEqualTo("{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 51280435, \"children\": [\n" +
      "    { \"name\": \"child-1\", \"calls\": 1, \"durationNanos\": 47442122 }\n" +
      "  ]\n" +
      "}");

    logger.clear();

    PerformanceMeasure.Duration duration_2 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile(performanceFile.toString())
      .start("root");
    testTimeNanos += 2_176_361L;

    PerformanceMeasure.Duration duration_2_1 = PerformanceMeasure.start("child-1");
    testTimeNanos += 32_478_123L;
    duration_2_1.stop();

    PerformanceMeasure.Duration duration_2_2 = PerformanceMeasure.start("child-2");
    testTimeNanos += 13_237_123L;
    duration_2_2.stop();

    testTimeNanos += 12_567_151L;
    duration_2.stop();
    assertThat(logger.logs()).isEqualTo("" +
      "[DEBUG] Performance Measures:\n" +
      "{ \"name\": \"root\", \"calls\": 1, \"durationNanos\": 60458763, \"children\": [\n" +
      "    { \"name\": \"child-1\", \"calls\": 1, \"durationNanos\": 32478124 },\n" +
      "    { \"name\": \"child-2\", \"calls\": 1, \"durationNanos\": 13237124 }\n" +
      "  ]\n" +
      "}\n" +
      "[INFO] Adding performance measures into: " + performanceFile + "\n");

    jsonContent = new String(Files.readAllBytes(performanceFile), UTF_8);
    assertThat(jsonContent).isEqualTo("{ \"name\": \"root\", \"calls\": 2, \"durationNanos\": 111739198, \"children\": [\n" +
      "    { \"name\": \"child-1\", \"calls\": 2, \"durationNanos\": 79920246 },\n" +
      "    { \"name\": \"child-2\", \"calls\": 1, \"durationNanos\": 13237124 }\n" +
      "  ]\n" +
      "}");
  }

  @Test
  void can_not_merge_incompatible_json(@TempDir File workDir) {
    logger.setLevel(StringLogger.Level.ERROR);
    Path performanceFile = workDir.toPath().resolve("performance.measure.json");
    PerformanceMeasure.Duration duration_1 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile(performanceFile.toString())
      .start("root1");
    testTimeNanos += 1_382_190L;
    duration_1.stop();

    assertThat(logger.logs()).isEmpty();

    logger.clear();

    PerformanceMeasure.Duration duration_2 = PerformanceMeasure.reportBuilder()
      .activate(true)
      .toFile(performanceFile.toString())
      .start("root2");
    testTimeNanos += 2_176_361L;
    duration_2.stop();

    assertThat(logger.logs())
      .isEqualTo("[ERROR] Can't save performance measure: Incompatible name 'root1' and 'root2'\n");
  }

  @Test
  void log_info_and_not_performance_file() {
    PerformanceMeasure.Duration duration = PerformanceMeasure.reportBuilder()
      .activate(true)
      .appendMeasurementCost()
      .start("root");
    testTimeNanos += 1_382_190L;
    duration.stop();

    assertThat(logger.logs()).isEmpty();
  }

  @Test
  void ensure_parent_directory_exists(@TempDir Path workDir) throws IOException {
    assertThatNoException().isThrownBy(() -> ensureParentDirectoryExists(Paths.get("file-without-parent.json")));

    ensureParentDirectoryExists(workDir.resolve("parent-dir/file.json"));
    assertThat(workDir.resolve("parent-dir")).isDirectory();
  }

}
