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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import org.sonarsource.performance.measure.log.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class PerformanceMeasure {

  private static final Logger LOG = Logger.get(PerformanceMeasure.class);

  private static final ThreadLocal<DurationMeasure> THREAD_LOCAL_CURRENT_MEASURE = new ThreadLocal<>();

  private static boolean globalDeactivation = true;

  private static Supplier<Long> nanoTimeSupplier = System::nanoTime;

  private PerformanceMeasure() {
    // utility class
  }

  private static void setGlobalDeactivation(boolean value) {
    globalDeactivation = value;
  }

  public static Builder reportBuilder() {
    return new Builder();
  }

  public static class Builder {

    private boolean active = false;

    @Nullable
    private String performanceFile = null;

    boolean appendMeasurementCost = false;

    public Builder activate(boolean active) {
      this.active = active;
      return this;
    }

    public Builder appendMeasurementCost() {
      this.appendMeasurementCost = true;
      return this;
    }

    public Builder toFile(@Nullable String performanceFile) {
      this.performanceFile = performanceFile;
      return this;
    }

    public Duration start(String name) {
      if (!active) {
        return IgnoredDuration.INSTANCE;
      }
      setGlobalDeactivation(false);
      Path performanceMeasureFile = null;
      if (performanceFile != null && !performanceFile.isEmpty()) {
        performanceMeasureFile = Paths.get(performanceFile.replace('\\', File.separatorChar).replace('/', File.separatorChar));
      }
      DurationMeasure parentMeasure = THREAD_LOCAL_CURRENT_MEASURE.get();
      DurationMeasure currentMeasure = new DurationMeasure(name);
      THREAD_LOCAL_CURRENT_MEASURE.set(currentMeasure);
      return new RecordedDurationReport(parentMeasure, currentMeasure, performanceMeasureFile, appendMeasurementCost);
    }

  }

  public static Duration start(Object object) {
    if (globalDeactivation) {
      return IgnoredDuration.INSTANCE;
    }
    return start(object.getClass().getSimpleName());
  }

  public static Duration start(String name) {
    if (globalDeactivation) {
      return IgnoredDuration.INSTANCE;
    }
    DurationMeasure parentMeasure = THREAD_LOCAL_CURRENT_MEASURE.get();
    if (parentMeasure == null) {
      return IgnoredDuration.INSTANCE;
    }
    DurationMeasure currentMeasure = parentMeasure.getOrCreateChild(name);
    THREAD_LOCAL_CURRENT_MEASURE.set(currentMeasure);
    return new RecordedDuration(parentMeasure, currentMeasure);
  }

  private static class RecordedDuration implements Duration {

    @Nullable
    protected final DurationMeasure parentMeasure;
    protected final DurationMeasure measure;
    private long startNanos;

    public RecordedDuration(@Nullable DurationMeasure parentMeasure, DurationMeasure measure) {
      this.parentMeasure = parentMeasure;
      this.measure = measure;
      this.startNanos = nanoTimeSupplier.get();
    }

    @Override
    public void stop() {
      if (startNanos != -1) {
        measure.addCalls(1, nanoTimeSupplier.get() - startNanos);
        startNanos = -1;
        if (parentMeasure == null) {
          THREAD_LOCAL_CURRENT_MEASURE.remove();
        } else {
          THREAD_LOCAL_CURRENT_MEASURE.set(parentMeasure);
        }
      }
    }

  }

  private static class RecordedDurationReport implements Duration {

    private static final String PARENT_OF_THROWAWAY_MEASURES_TO_COMPUTE_OBSERVATION_COST = "#measures to compute observation cost";
    private static final int SAMPLING_COUNT_TO_EVALUATE_OBSERVATION_COST = 99;
    private static final Supplier<IntStream> SAMPLES = () -> IntStream.range(0, SAMPLING_COUNT_TO_EVALUATE_OBSERVATION_COST);

    private final RecordedDuration duration;
    @Nullable
    private final Path performanceMeasureFile;
    private final boolean appendMeasurementCost;

    public RecordedDurationReport(@Nullable DurationMeasure parentMeasure, DurationMeasure measure,
      @Nullable Path performanceMeasureFile, boolean appendMeasurementCost) {
      duration = new RecordedDuration(parentMeasure, measure);
      this.performanceMeasureFile = performanceMeasureFile;
      this.appendMeasurementCost = appendMeasurementCost;
    }

    @Override
    public void stop() {
      if (appendMeasurementCost) {
        THREAD_LOCAL_CURRENT_MEASURE.set(duration.measure);
        appendMeasurementCost();
      }
      duration.stop();
      LOG.debug(() -> "Performance Measures:\n" + DurationMeasureFiles.toJson(duration.measure));
      if (performanceMeasureFile != null) {
        saveToFile(duration.measure, performanceMeasureFile);
      }
    }

    private static void appendMeasurementCost() {
      String[] sampleNames = SAMPLES.get().mapToObj(i -> "m" + i).toArray(String[]::new);
      RecordedDuration totalDuration = (RecordedDuration) start("#MeasurementCost_v1");
      DurationMeasure measurementCost = totalDuration.measure;
      RecordedDuration temporaryDuration = (RecordedDuration) start(PARENT_OF_THROWAWAY_MEASURES_TO_COMPUTE_OBSERVATION_COST);
      measurementCost.getOrCreateChild("nanoTime").addCalls(1, median(SAMPLES.get().mapToLong(i -> {
        long start = nanoTimeSupplier.get();
        return nanoTimeSupplier.get() - start;
      })));
      measurementCost.getOrCreateChild("createChild").addCalls(1, median(SAMPLES.get().mapToLong(i -> {
        long start = nanoTimeSupplier.get();
        start(sampleNames[i]).stop();
        return nanoTimeSupplier.get() - start;
      })));
      measurementCost.getOrCreateChild("observationCost").addCalls(1, median(Arrays.stream(sampleNames)
        .map(temporaryDuration.measure::get).mapToLong(DurationMeasure::durationNanos)));
      start("measure").stop();
      measurementCost.getOrCreateChild("incrementChild").addCalls(1, median(SAMPLES.get().mapToLong(i -> {
        long start = nanoTimeSupplier.get();
        start("measure").stop();
        return nanoTimeSupplier.get() - start;
      })));
      temporaryDuration.stop();
      measurementCost.remove(PARENT_OF_THROWAWAY_MEASURES_TO_COMPUTE_OBSERVATION_COST);
      totalDuration.stop();
    }

    private static long median(LongStream measures) {
      long[] sortedMeasures = measures.sorted().toArray();
      return sortedMeasures[(sortedMeasures.length - 1) / 2];
    }

    private static void saveToFile(DurationMeasure measure, Path performanceFile) {
      try {
        DurationMeasure allMeasures;
        if (Files.exists(performanceFile)) {
          LOG.info(() -> "Adding performance measures into: " + performanceFile);
          DurationMeasure existingMeasure = DurationMeasureFiles.fromJson(performanceFile);
          allMeasures = existingMeasure.merge(measure);
        } else {
          LOG.info(() -> "Saving performance measures into: " + performanceFile);
          allMeasures = measure;
          ensureParentDirectoryExists(performanceFile);
        }
        Files.write(performanceFile, DurationMeasureFiles.toJson(allMeasures).getBytes(UTF_8));
      } catch (IllegalStateException | IOException e) {
        LOG.error(() -> "Can't save performance measure: " + e.getMessage());
      }
    }

  }

  @FunctionalInterface
  public interface Duration {
    void stop();
  }

  public static final class IgnoredDuration implements Duration {

    public static final IgnoredDuration INSTANCE = new IgnoredDuration();

    @Override
    public void stop() {
      // no op
    }

  }

  // Visible for testing
  static void ensureParentDirectoryExists(Path path) throws IOException {
    Path parentDirectory = path.getParent();
    if (parentDirectory != null && !Files.isDirectory(parentDirectory)) {
      Files.createDirectory(parentDirectory);
    }
  }

  // Visible for testing
  static void overrideTimeSupplierForTest(Supplier<Long> nanoTimeSupplier) {
    PerformanceMeasure.nanoTimeSupplier = nanoTimeSupplier;
  }

  // Visible for testing
  static void deactivateAndClearCurrentMeasureForTest() {
    setGlobalDeactivation(true);
    THREAD_LOCAL_CURRENT_MEASURE.remove();
  }

}
