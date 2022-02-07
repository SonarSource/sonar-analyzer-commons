/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.helpers;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.EndOfLookaroundState;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;

import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.CHARACTER;
import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.EPSILON;
import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.NEGATION;

public class RegexReachabilityChecker {
  private static final int MAX_CACHE_SIZE = 5_000;

  private final boolean defaultAnswer;
  private final Map<OrderedStatePair, Boolean> cache = new HashMap<>();

  public RegexReachabilityChecker(boolean defaultAnswer) {
    this.defaultAnswer = defaultAnswer;
  }

  public void clearCache() {
    cache.clear();
  }

  public boolean canReach(AutomatonState start, AutomatonState goal) {
    if (start == goal) {
      return true;
    }
    OrderedStatePair pair = new OrderedStatePair(start, goal);
    if (cache.containsKey(pair)) {
      return cache.get(pair);
    }
    if (cache.size() >= MAX_CACHE_SIZE) {
      return defaultAnswer;
    }
    cache.put(pair, false);
    boolean result = false;
    for (AutomatonState successor : start.successors()) {
      if (canReach(successor, goal)) {
        result = true;
        break;
      }
    }
    cache.put(pair, result);
    return result;
  }

  private static class OrderedStatePair {
    private final AutomatonState source;
    private final AutomatonState target;

    OrderedStatePair(AutomatonState source, AutomatonState target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof OrderedStatePair)) return false;
      OrderedStatePair that = (OrderedStatePair) o;
      return source == that.source && target == that.target;
    }

    @Override
    public int hashCode() {
      return Objects.hash(source, target);
    }
  }

  public boolean canReachWithConsumingInput(AutomatonState start, AutomatonState goal, Set<AutomatonState> visited) {
    if (start == goal || visited.contains(start)) {
      return false;
    }
    visited.add(start);

    if (start instanceof LookAroundTree) {
      return canReachWithConsumingInput(start.continuation(), goal, visited);
    }

    for (AutomatonState successor : start.successors()) {
      AutomatonState.TransitionType transition = successor.incomingTransitionType();
      if (((transition == CHARACTER) && canReach(successor, goal))
        || ((transition != CHARACTER) && canReachWithConsumingInput(successor, goal, visited))) {
        return true;
      }
    }
    return false;
  }

  public static boolean canReachWithoutConsumingInput(AutomatonState start, AutomatonState goal) {
    return canReachWithoutConsumingInput(start, goal, false, new HashSet<>());
  }

  private static boolean canReachWithoutConsumingInput(AutomatonState start, AutomatonState goal, boolean stopAtBoundaries, Set<AutomatonState> visited) {
    if (start == goal) {
      return true;
    }
    if (visited.contains(start) || (stopAtBoundaries && start instanceof BoundaryTree)) {
      return false;
    }
    visited.add(start);
    for (AutomatonState successor : start.successors()) {
      AutomatonState.TransitionType transition = successor.incomingTransitionType();
      // We don't generally consider elements behind backtracking edges to be 0-input reachable because what comes
      // after the edge won't directly follow what's before the edge. However, we do consider the end-of-lookahead
      // state itself reachable (but not any state behind it), so that we can check whether the end of the lookahead
      // can be reached without input from a given place within the lookahead.
      if ((successor instanceof EndOfLookaroundState && successor == goal)
        || ((transition == EPSILON || transition == NEGATION) && canReachWithoutConsumingInput(successor, goal, stopAtBoundaries, visited))) {
        return true;
      }
    }
    return false;
  }
}
