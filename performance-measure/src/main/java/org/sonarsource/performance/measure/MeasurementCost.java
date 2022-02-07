/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2022 SonarSource SA
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

import javax.annotation.CheckForNull;

public class MeasurementCost {

  public static final String MEASUREMENT_COST_NAME = "#MeasurementCost_v1";
  public static final String SUBTRACTED_MEASUREMENT_COST_NAME = "#MeasurementCost_subtracted_v1";

  public final long createChild;
  public final long incrementChild;
  public final long nanoTime;
  public final long observationCost;

  public MeasurementCost(DurationMeasure measurementCost) {
    createChild = averageDuration(measurementCost, "createChild");
    incrementChild = averageDuration(measurementCost, "incrementChild");
    nanoTime = averageDuration(measurementCost, "nanoTime");
    observationCost = averageDuration(measurementCost, "observationCost");
  }

  @CheckForNull
  public static MeasurementCost observationCostOf(DurationMeasure measure) {
    DurationMeasure costMeasure = measure.get(MEASUREMENT_COST_NAME);
    if (costMeasure == null) {
      costMeasure = measure.get(SUBTRACTED_MEASUREMENT_COST_NAME);
    }
    return costMeasure != null ? new MeasurementCost(costMeasure) : null;
  }

  public static DurationMeasure subtractObservationCost(DurationMeasure measure) {
    DurationMeasure measureCopy = measure.copy();
    DurationMeasure measurementCostNode = measureCopy.remove(MEASUREMENT_COST_NAME);
    if (measurementCostNode != null) {
      MeasurementCost measurementCost = new MeasurementCost(measurementCostNode);
      measurementCost.recursiveSubtractObservationCost(measureCopy);
      measurementCostNode.rename(SUBTRACTED_MEASUREMENT_COST_NAME);
      measureCopy.addOrMerge(measurementCostNode);
    }
    return measureCopy;
  }

  static long averageDuration(DurationMeasure measurementCost, String name) {
    DurationMeasure measure = measurementCost.get(name);
    if (measure == null) {
      throw new IllegalStateException("Missing " + name);
    }
    return measure.calls() == 0 ? 0 : (measure.durationNanos() / measure.calls());
  }

  ChildCounter recursiveSubtractObservationCost(DurationMeasure measure) {
    ChildCounter childCounter = ChildCounter.ZERO;
    for (DurationMeasure child : measure.children()) {
      childCounter = childCounter.add(recursiveSubtractObservationCost(child));
    }
    long cost = observationCost * measure.calls() + childCounter.cost(this);
    measure.subtractDuration(Math.min(cost, measure.durationNanos()));
    return childCounter.add(ChildCounter.of(measure));
  }

  static class ChildCounter {

    static final ChildCounter ZERO = new ChildCounter(0, 0);

    long createChildCount;
    long incrementChildCount;

    public ChildCounter(long createChildCount, long incrementChildCount) {
      this.createChildCount = createChildCount;
      this.incrementChildCount = incrementChildCount;
    }

    static ChildCounter of(DurationMeasure measure) {
      long count = measure.calls();
      if (count == 0) {
        return ZERO;
      }
      return new ChildCounter(1, count - 1);
    }

    ChildCounter add(ChildCounter other) {
      return new ChildCounter(createChildCount + other.createChildCount, incrementChildCount + other.incrementChildCount);
    }

    long cost(MeasurementCost measurementCost) {
      return createChildCount * measurementCost.createChild + incrementChildCount * measurementCost.incrementChild;
    }

  }

}
