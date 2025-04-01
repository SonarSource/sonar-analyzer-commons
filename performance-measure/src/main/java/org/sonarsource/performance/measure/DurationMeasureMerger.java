/*
 * SonarSource Performance Measure Library
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
package org.sonarsource.performance.measure;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonarsource.performance.measure.log.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DurationMeasureMerger {

  private static final Logger LOG = Logger.get(DurationMeasureMerger.class);

  private static final Pattern DATE_TIME_REGEX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-\\d{2}h\\d{2}m\\d{2}\\.\\d{3}$");
  private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);
  private static final NumberFormat SCORE_FORMAT = new DecimalFormat("0.0'%'", SYMBOLS);
  private static final int MINUTES_PER_HOUR = 60;
  private static final int SECONDS_PER_MINUTE = 60;

  public static final String PERFORMANCE_STATISTICS_FILE_NAME = "performance.statistics.txt";
  public static final String PERFORMANCE_SCORE_FILE_NAME = "performance.score.json";

  public static final String SCORE_OVERSTEP_THRESHOLD = "scoreOverstepThreshold";
  public static final String SCORE = "score";
  public static final String LINK = "link";

  private Map<String, String> categoryNames = new HashMap<>();
  private Predicate<String> groupedMeasurePredicate = name -> false;
  private String performanceFileName = "performance.measure.json";
  private String repositoryBaseUrl = "";
  private double scoreMaxThreshold = 1.02;

  public DurationMeasureMerger withMaxScoreThreshold(double scoreMaxThreshold) {
    this.scoreMaxThreshold = scoreMaxThreshold;
    return this;
  }

  public DurationMeasureMerger forPerformanceFileName(String performanceFileName) {
    this.performanceFileName = performanceFileName;
    return this;
  }

  public DurationMeasureMerger withCategoryNames(Map<String, String> categoryNames) {
    this.categoryNames = categoryNames;
    return this;
  }

  public DurationMeasureMerger groupedBy(Predicate<String> groupedMeasurePredicate) {
    this.groupedMeasurePredicate = groupedMeasurePredicate;
    return this;
  }

  public DurationMeasureMerger withRepositoryBaseUrl(String repositoryBaseUrl) {
    this.repositoryBaseUrl = repositoryBaseUrl;
    return this;
  }

  public void mergeProjectPerformances(Path latestAnalysisFolder, Path destDirectory, List<String> namesToMergeOnUpperLevel) throws IOException {
    LOG.info(() -> "Merge Project Performances of " + latestAnalysisFolder.getFileName());
    DurationMeasure mergedMeasure = null;
    for (Path performanceFile : findPerformanceFiles(latestAnalysisFolder)) {
      DurationMeasure measure = DurationMeasureFiles.fromJsonWithoutObservationCost(performanceFile);
      namesToMergeOnUpperLevel.forEach(measure::recursiveMergeOnUpperLevel);
      mergedMeasure = mergedMeasure == null ? measure : mergedMeasure.merge(measure);
    }
    if (mergedMeasure == null) {
      LOG.warning(() -> "Can't find any '" + performanceFileName + "' in " + latestAnalysisFolder);
      return;
    }
    Path mergedPerformanceFile = destDirectory.resolve(performanceFileName);
    LOG.info(() -> "Merged Performance File: " + mergedPerformanceFile);
    DurationMeasureFiles.writeJson(mergedPerformanceFile, mergedMeasure);

    Path performanceStatisticsFile = destDirectory.resolve(PERFORMANCE_STATISTICS_FILE_NAME);
    LOG.info(() -> "Performance Statistics File: " + performanceStatisticsFile);
    DurationMeasureFiles.writeStatistics(performanceStatisticsFile, mergedMeasure, categoryNames, groupedMeasurePredicate);
  }

  public void compareWithRelease(Path releaseFolder, Path latestAnalysisFolder, Path destDirectory, Path globalPerformanceScoreFile) throws IOException {
    LOG.info(() -> "Compare Performances between release " + releaseFolder.getFileName() + " and latest " + latestAnalysisFolder.getFileName());
    Set<Path> releasePerformanceFiles = findPerformanceFiles(releaseFolder);
    Set<Path> latestPerformanceFiles = findPerformanceFiles(latestAnalysisFolder);

    Set<String> releaseAnalysisProjects = releasePerformanceFiles.stream()
      .map(DurationMeasureMerger::projectName).collect(Collectors.toCollection(TreeSet::new));
    Set<String> latestAnalysisProjects = latestPerformanceFiles.stream()
      .map(DurationMeasureMerger::projectName).collect(Collectors.toCollection(TreeSet::new));

    Set<String> allProjects = new TreeSet<>(releaseAnalysisProjects);
    allProjects.addAll(latestAnalysisProjects);

    JsonArray projectsMissingInRelease = new JsonArray();
    JsonArray projectsMissingInLatest = new JsonArray();
    JsonArray commonProjectsCompared = new JsonArray();

    long releaseAnalysisDuration = 0;
    long latestAnalysisDuration = 0;
    for (String projectName : allProjects) {
      Optional<Path> releasePerformanceFile = releasePerformanceFiles.stream()
        .filter(path -> projectName(path).equals(projectName)).findFirst();

      Optional<Path> latestAnalysisPerformanceFile = latestPerformanceFiles.stream()
        .filter(path -> projectName(path).equals(projectName)).findFirst();

      if (!releasePerformanceFile.isPresent()) {
        projectsMissingInRelease.add(projectName);
      } else if (!latestAnalysisPerformanceFile.isPresent()) {
        projectsMissingInLatest.add(projectName);
      } else {
        commonProjectsCompared.add(projectName);
        releaseAnalysisDuration += analysisDuration(releasePerformanceFile.get());
        latestAnalysisDuration += analysisDuration(latestAnalysisPerformanceFile.get());
      }
    }
    JsonObject score = new JsonObject();
    boolean scoreOverstepThreshold;
    String scoreValue;
    double durationRatio;
    if (latestAnalysisDuration == 0 || releaseAnalysisDuration == 0) {
      durationRatio = 0f;
      scoreOverstepThreshold = true;
      scoreValue = "Zero Duration";
    } else {
      durationRatio = Math.round(latestAnalysisDuration * 10_000.0d / releaseAnalysisDuration) / 10_000.0d;
      scoreOverstepThreshold = durationRatio > scoreMaxThreshold;
      scoreValue = SCORE_FORMAT.format(durationRatio * 100);
    }
    score.addProperty(SCORE_OVERSTEP_THRESHOLD, scoreOverstepThreshold);
    score.addProperty(SCORE, scoreValue);
    score.addProperty("durationRatioCompareToRelease", durationRatio);
    score.addProperty("comparedWithRelease", releaseFolder.getFileName().toString());
    score.addProperty("releaseAnalysisDuration", durationNanosToString(releaseAnalysisDuration));
    score.addProperty("latestAnalysisDuration", durationNanosToString(latestAnalysisDuration));
    score.addProperty("releaseAnalysisDurationNanos", releaseAnalysisDuration);
    score.addProperty("latestAnalysisDurationNanos", latestAnalysisDuration);
    score.add("projectsMissingInRelease", projectsMissingInRelease);
    score.add("projectsMissingInLatest", projectsMissingInLatest);
    score.add("comparedProjects", commonProjectsCompared);

    Path performanceScoreFile = destDirectory.resolve(PERFORMANCE_SCORE_FILE_NAME);
    writeJson(performanceScoreFile, score);

    JsonObject globalScore = new JsonObject();
    globalScore.add(SCORE_OVERSTEP_THRESHOLD, score.get(SCORE_OVERSTEP_THRESHOLD));
    globalScore.add(SCORE, score.get(SCORE));
    String linkRelativePart = globalPerformanceScoreFile.getParent()
      .relativize(performanceScoreFile).toString().replace(File.separatorChar, '/');
    globalScore.addProperty(LINK, repositoryBaseUrl + linkRelativePart);

    writeJson(globalPerformanceScoreFile, globalScore);
  }

  private static void writeJson(Path path, JsonObject jsonObject) throws IOException {
    String json = new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
    LOG.info(() -> "Writing: " + path);
    Files.write(path, json.getBytes(UTF_8));
  }

  private static String durationNanosToString(long durationNanos) {
    Duration duration = Duration.ofNanos(durationNanos);
    return String.format("%dh%02dm%02ds",
      duration.toHours(),
      duration.toMinutes() % MINUTES_PER_HOUR,
      duration.getSeconds() % SECONDS_PER_MINUTE);
  }

  private static String projectName(Path performanceFilePath) {
    return performanceFilePath.getParent().getParent().getFileName().toString();
  }

  private static long analysisDuration(Path performanceFile) throws IOException {
    DurationMeasure measure = DurationMeasureFiles.fromJsonWithoutObservationCost(performanceFile);
    return measure.durationNanos();
  }

  private Set<Path> findPerformanceFiles(Path analysisPath) throws IOException {
    Set<Path> performanceFiles = new TreeSet<>();
    for(Path projectDirectory : getSubDirectories(analysisPath)) {
      getSubDirectories(projectDirectory).stream()
        .filter(path -> DATE_TIME_REGEX.matcher(path.getFileName().toString()).find())
        .sorted(Comparator.comparing(Object::toString).reversed())
        .map(path -> path.resolve(performanceFileName))
        .filter(Files::exists)
        .findFirst()
        .ifPresent(performanceFiles::add);
    }
    return performanceFiles;
  }

  private static List<Path> getSubDirectories(Path parentDirectory) throws IOException {
    try (Stream<Path> stream = Files.list(parentDirectory)) {
      return stream.filter(Files::isDirectory).collect(Collectors.toList());
    }
  }

}
