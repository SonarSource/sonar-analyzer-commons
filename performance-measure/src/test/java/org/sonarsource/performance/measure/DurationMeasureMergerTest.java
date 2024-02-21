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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.performance.measure.log.StringLogger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class DurationMeasureMergerTest {

  private final StringLogger logger = StringLogger.initialize(StringLogger.Level.INFO);

  private final DurationMeasureMerger merger = new DurationMeasureMerger()
    .withMaxScoreThreshold(1.02)
    .forPerformanceFileName("sonar.java.performance.measure.json")
    .withCategoryNames(categoryNames())
    .groupedBy(name -> name.endsWith("Check"))
    .withRepositoryBaseUrl("https://github.com/SonarSource/peachee-languages-statistics/blob/sonar-java/");

  private static Map<String, String> categoryNames() {
    Map<String, String> map = new HashMap<>();
    map.put("Main", "1.main");
    map.put("Test", "1.test");
    map.put("Scanners", "2.scanners");
    map.put("IssuableSubscriptionVisitors", "2.subscription");
    map.put("SymbolicExecutionVisitor", "3.symbolic-execution");
    return map;
  }

  @Test
  void merge_project_performances(@TempDir Path destDirectory) throws IOException {
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25780");
    merger.mergeProjectPerformances(latestAnalysisFolder, destDirectory, Collections.singletonList("RegexParser"));

    Path expectedPerformanceFile = latestAnalysisFolder.resolve("sonar.java.performance.measure.json");
    Path actualPerformanceFile = destDirectory.resolve("sonar.java.performance.measure.json");
    assertSameTextContent(expectedPerformanceFile, actualPerformanceFile);

    Path expectedPerformanceStatisticsFile = latestAnalysisFolder.resolve("performance.statistics.txt");
    Path actualPerformanceStatisticsFile = destDirectory.resolve("performance.statistics.txt");
    assertSameTextContent(expectedPerformanceStatisticsFile, actualPerformanceStatisticsFile);

    assertThat(logger.logs().replaceAll("\\R", "\n")).isEqualTo("" +
      "[INFO] Merge Project Performances of 6.15.0.25780\n" +
      "[INFO] Merged Performance File: " + actualPerformanceFile + "\n" +
      "[INFO] Performance Statistics File: " + actualPerformanceStatisticsFile + "\n");
  }

  @Test
  void compare_with_previous_release(@TempDir Path tmpDirectory) throws IOException {
    Path tmpGlobalPerformanceScoreFile = tmpDirectory.resolve("performance.score.json");
    Path tmpLatestAnalysisFolder = tmpDirectory.resolve("events").resolve("6.15.0.25780");
    Files.createDirectories(tmpLatestAnalysisFolder);

    Path previousReleaseFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25600");
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25780");
    Path globalScoreFile = Paths.get("src", "test", "resources", "performance.score.json");
    merger.compareWithRelease(previousReleaseFolder, latestAnalysisFolder, tmpLatestAnalysisFolder, tmpGlobalPerformanceScoreFile);

    Path expectedScoreFile = latestAnalysisFolder.resolve("performance.score.json");
    Path actualScoreFile = tmpLatestAnalysisFolder.resolve("performance.score.json");
    assertSameTextContent(expectedScoreFile, actualScoreFile);

    assertSameTextContent(globalScoreFile, tmpGlobalPerformanceScoreFile);

    assertThat(logger.logs().replaceAll("\\R", "\n")).isEqualTo("" +
      "[INFO] Compare Performances between release 6.15.0.25600 and latest 6.15.0.25780\n" +
      "[INFO] Writing: " + actualScoreFile + "\n" +
      "[INFO] Writing: " + tmpGlobalPerformanceScoreFile + "\n");
  }

  @Test
  void compare_with_previous_release_with_bigger_threshold(@TempDir Path tmpDirectory) throws IOException {
    Path tmpGlobalPerformanceScoreFile = tmpDirectory.resolve("performance.score.json");
    Path tmpLatestAnalysisFolder = tmpDirectory.resolve("events").resolve("6.15.0.25780");
    Files.createDirectories(tmpLatestAnalysisFolder);

    Path previousReleaseFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25600");
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25780");
    merger.withMaxScoreThreshold(1.06)
      .compareWithRelease(previousReleaseFolder, latestAnalysisFolder, tmpLatestAnalysisFolder, tmpGlobalPerformanceScoreFile);

    assertThat(tmpGlobalPerformanceScoreFile).hasContent("" +
      "{\n" +
      "  \"scoreOverstepThreshold\": false,\n" +
      "  \"score\": \"105.1%\",\n" +
      "  \"link\": \"https://github.com/SonarSource/peachee-languages-statistics/blob/sonar-java/events/6.15.0.25780/performance.score.json\"\n" +
      "}");
  }

  @Test
  void compare_with_fake_previous_release(@TempDir Path tmpDirectory) throws IOException {
    Path tmpGlobalPerformanceScoreFile = tmpDirectory.resolve("performance.score.json");
    Path tmpLatestAnalysisFolder = tmpDirectory.resolve("events").resolve("6.15.0.25600");
    Files.createDirectories(tmpLatestAnalysisFolder);

    Path previousReleaseFolder = Paths.get("src", "test", "resources", "events", "6.14.0.25463");
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25600");
    merger.compareWithRelease(previousReleaseFolder, latestAnalysisFolder, tmpLatestAnalysisFolder, tmpGlobalPerformanceScoreFile);

    Path expectedScoreFile = latestAnalysisFolder.resolve("performance.score.json");
    Path actualScoreFile = tmpLatestAnalysisFolder.resolve("performance.score.json");
    assertSameTextContent(expectedScoreFile, actualScoreFile);

    assertThat(tmpGlobalPerformanceScoreFile).hasContent("" +
      "{\n" +
      "  \"scoreOverstepThreshold\": false,\n" +
      "  \"score\": \"93.5%\",\n" +
      "  \"link\": \"https://github.com/SonarSource/peachee-languages-statistics/blob/sonar-java/events/6.15.0.25600/performance.score.json\"\n" +
      "}");

    assertThat(logger.logs().replaceAll("\\R", "\n")).isEqualTo("" +
      "[INFO] Compare Performances between release 6.14.0.25463 and latest 6.15.0.25600\n" +
      "[INFO] Writing: " + actualScoreFile + "\n" +
      "[INFO] Writing: " + tmpGlobalPerformanceScoreFile + "\n");
  }

  @Test
  void can_not_find_any_performance_file(@TempDir Path destDirectory) throws IOException {
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25780");
    merger
      .forPerformanceFileName("invalid.performance.measure.json")
      .mergeProjectPerformances(latestAnalysisFolder, destDirectory, Collections.emptyList());
    assertThat(logger.logs().replaceAll("\\R", "\n")).isEqualTo("" +
      "[INFO] Merge Project Performances of 6.15.0.25780\n" +
      "[WARNING] Can't find any 'invalid.performance.measure.json' in " + latestAnalysisFolder + "\n");
  }

  @Test
  void can_not_find_any_common_project(@TempDir Path tmpDirectory) throws IOException {
    Path tmpGlobalPerformanceScoreFile = tmpDirectory.resolve("performance.score.json");
    Path tmpLatestAnalysisFolder = tmpDirectory.resolve("events").resolve("6.15.0.25600");
    Files.createDirectories(tmpLatestAnalysisFolder);

    Path previousReleaseFolder = Paths.get("src", "test", "resources", "events", "6.14.0.25463");
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25600");
    merger
      .forPerformanceFileName("invalid.performance.measure.json")
      .compareWithRelease(previousReleaseFolder, latestAnalysisFolder, tmpLatestAnalysisFolder, tmpGlobalPerformanceScoreFile);

    Path actualScoreFile = tmpLatestAnalysisFolder.resolve("performance.score.json");
    assertThat(actualScoreFile).hasContent("" +
      "{\n" +
      "  \"scoreOverstepThreshold\": true,\n" +
      "  \"score\": \"Zero Duration\",\n" +
      "  \"durationRatioCompareToRelease\": 0.0,\n" +
      "  \"comparedWithRelease\": \"6.14.0.25463\",\n" +
      "  \"releaseAnalysisDuration\": \"0h00m00s\",\n" +
      "  \"latestAnalysisDuration\": \"0h00m00s\",\n" +
      "  \"releaseAnalysisDurationNanos\": 0,\n" +
      "  \"latestAnalysisDurationNanos\": 0,\n" +
      "  \"projectsMissingInRelease\": [],\n" +
      "  \"projectsMissingInLatest\": [],\n" +
      "  \"comparedProjects\": []\n" +
      "}");

    assertThat(tmpGlobalPerformanceScoreFile).hasContent("" +
      "{\n" +
      "  \"scoreOverstepThreshold\": true,\n" +
      "  \"score\": \"Zero Duration\",\n" +
      "  \"link\": \"https://github.com/SonarSource/peachee-languages-statistics/blob/sonar-java/events/6.15.0.25600/performance.score.json\"\n" +
      "}");

    assertThat(logger.logs().replaceAll("\\R", "\n")).isEqualTo("" +
      "[INFO] Compare Performances between release 6.14.0.25463 and latest 6.15.0.25600\n" +
      "[INFO] Writing: " + actualScoreFile + "\n" +
      "[INFO] Writing: " + tmpGlobalPerformanceScoreFile + "\n");
  }

  @Test
  void release_with_zero_duration(@TempDir Path tmpDirectory) throws IOException {
    Path tmpGlobalPerformanceScoreFile = tmpDirectory.resolve("performance.score.json");
    Path tmpLatestAnalysisFolder = tmpDirectory.resolve("events").resolve("6.15.0.25600");
    Files.createDirectories(tmpLatestAnalysisFolder);

    Path previousReleaseFolder = Paths.get("src", "test", "resources", "events", "6.14.0.25321");
    Path latestAnalysisFolder = Paths.get("src", "test", "resources", "events", "6.15.0.25600");
    merger.compareWithRelease(previousReleaseFolder, latestAnalysisFolder, tmpLatestAnalysisFolder, tmpGlobalPerformanceScoreFile);

    assertThat(tmpGlobalPerformanceScoreFile).hasContent("" +
      "{\n" +
      "  \"scoreOverstepThreshold\": true,\n" +
      "  \"score\": \"Zero Duration\",\n" +
      "  \"link\": \"https://github.com/SonarSource/peachee-languages-statistics/blob/sonar-java/events/6.15.0.25600/performance.score.json\"\n" +
      "}");
  }

  static void assertSameTextContent(Path expected, Path actual) throws IOException {
    assertThat(Files.readAllLines(actual))
      .withFailMessage("" +
        "Expecting: " + actual + "\n" +
        "To match: " + expected + "\n" +
        "Actual content:\n" +
        "________________________________________________________________________\n" +
        new String(Files.readAllBytes(actual), UTF_8) +
        "________________________________________________________________________\n")
      .isEqualTo(Files.readAllLines(actual));
  }

}
