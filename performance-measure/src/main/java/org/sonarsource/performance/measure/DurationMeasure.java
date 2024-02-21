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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class DurationMeasure {

  private String name;
  private long calls;
  private long durationNanos;
  @Nullable
  private Map<String, DurationMeasure> childrenMap;

  public DurationMeasure(String name) {
    this(name, 0, 0, null);
  }

  public DurationMeasure(String name, long calls, long durationNanos, @Nullable Map<String, DurationMeasure> childrenMap) {
    this.name = name;
    this.calls = calls;
    this.durationNanos = durationNanos;
    this.childrenMap = childrenMap;
  }

  public void addCalls(long callsToAdd, long durationNanosToAdd) {
    calls += callsToAdd;
    durationNanos += durationNanosToAdd;
  }

  public DurationMeasure getOrCreateChild(String name) {
    if (childrenMap == null) {
      childrenMap = new HashMap<>();
    }
    return childrenMap.computeIfAbsent(name, DurationMeasure::new);
  }

  public DurationMeasure addOrMerge(DurationMeasure child) {
    if (childrenMap == null) {
      childrenMap = new HashMap<>();
    }
    return childrenMap.merge(child.name(), child, DurationMeasure::merge);
  }

  public DurationMeasure merge(DurationMeasure measure) {
    if (!name.equals(measure.name)) {
      throw new IllegalStateException("Incompatible name '" + name + "' and '" + measure.name + "'");
    }
    durationNanos += measure.durationNanos;
    calls += measure.calls;
    for (DurationMeasure child : measure.children()) {
      addOrMerge(child);
    }
    return this;
  }

  public String name() {
    return name;
  }

  public long calls() {
    return calls;
  }

  public long durationNanos() {
    return durationNanos;
  }

  public void subtractDuration(long durationNanosToSubtract) {
    durationNanos -= durationNanosToSubtract;
  }

  public DurationMeasure remove(String childName) {
    if (childrenMap == null) {
      return null;
    }
    return childrenMap.remove(childName);
  }

  public DurationMeasure get(String childName) {
    if (childrenMap == null) {
      return null;
    }
    return childrenMap.get(childName);
  }

  public Collection<DurationMeasure> children() {
    return childrenMap != null ? childrenMap.values() : Collections.emptyList();
  }

  public Collection<DurationMeasure> sortedChildren() {
    List<DurationMeasure> sortedList = new ArrayList<>(children());
    sortedList.sort(Comparator.comparing(DurationMeasure::name));
    return sortedList;
  }

  public boolean hasChildren() {
    return childrenMap == null || childrenMap.isEmpty();
  }

  public DurationMeasure copy() {
    Map<String, DurationMeasure> childrenMapCopy = null;
    if (childrenMap != null) {
      childrenMapCopy = new HashMap<>();
      for (DurationMeasure child : childrenMap.values()) {
        childrenMapCopy.put(child.name, child.copy());
      }
    }
    return new DurationMeasure(name, calls, durationNanos, childrenMapCopy);
  }

  public void recursiveMergeOnUpperLevel(String targetChildName) {
    List<Runnable> delayedActions = new ArrayList<>();
    children().forEach(child -> child.recursiveMergeOnUpperLevel(this, targetChildName, delayedActions));
    delayedActions.forEach(Runnable::run);
  }

  private void recursiveMergeOnUpperLevel(DurationMeasure parent, String targetChildName, List<Runnable> delayedActions) {
    if (childrenMap != null) {
      DurationMeasure childToMove = childrenMap.get(targetChildName);
      if (childToMove != null) {
        delayedActions.add(() -> {
          remove(childToMove.name);
          durationNanos -= childToMove.durationNanos;
          parent.addOrMerge(childToMove);
        });
      }
      children().forEach(child -> child.recursiveMergeOnUpperLevel(this, targetChildName, delayedActions));
    }
  }

  public void rename(String name) {
    this.name = name;
  }
}
