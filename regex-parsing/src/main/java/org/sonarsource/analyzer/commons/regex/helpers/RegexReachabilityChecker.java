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


import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.EndOfLookaroundState;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;

import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.CHARACTER;
import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.EPSILON;
import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.NEGATION;

public class RegexReachabilityChecker {
  private static final int MAX_CACHE_SIZE = 5_000;

  private final boolean defaultAnswer;
  private final Map<OrderedStatePair, Boolean> cache = new HashMap<>();
  private static final List<BoundaryTree.Type> TYPE_ENDINGS = Arrays.asList(BoundaryTree.Type.INPUT_END_FINAL_TERMINATOR, BoundaryTree.Type.LINE_END);

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
      if (((transition == CHARACTER) && !isLineBreakOrPeriodAfterEndBoundaries(visited, successor) && canReach(successor, goal))
        || ((transition != CHARACTER) && canReachWithConsumingInput(successor, goal, visited))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the automaton can reach the goal state from start state, ignoring boundaries (see {@link BoundaryTree})
   *
   * @param start The start state
   * @param goal the targeted state
   * @return true if the goal state can be reached from start, ignoring boundaries
   */
  public static boolean canReachWithoutConsumingInput(AutomatonState start, AutomatonState goal) {
    return canReachWithoutConsumingInput(start, goal, false, new HashSet<>());
  }

  /**
   * Check if the automaton can reach the goal state from start state, taking into consideration boundaries (see {@link BoundaryTree})
   *
   * @param start The start state
   * @param goal the targeted state
   * @return true if the goal state can be reached from start, stopping if encountering boundaries
   */
  public static boolean canReachWithoutConsumingInputNorCrossingBoundaries(AutomatonState start, AutomatonState goal) {
    return canReachWithoutConsumingInput(start, goal, true, new HashSet<>());
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
        || ((transition == EPSILON || transition == NEGATION || isLineBreakOrPeriodAfterEndBoundaries(visited, successor))
            && canReachWithoutConsumingInput(successor, goal, stopAtBoundaries, visited))) {
        return true;
      }
    }
    return false;
  }

  // Checks whether some end boundaries ($ and \Z) have been previously visited and are followed by newline characters.
  // Those boundaries do not consume new line characters, so the newline characters can be matched.
  // Moreover, if the flag DOTALL is set, periods after those boundaries can be matched as well.
  private static boolean isLineBreakOrPeriodAfterEndBoundaries(Set<AutomatonState> visited, AutomatonState currentState) {
    if (isEscapeSequence(currentState) || isDotall(currentState)) {
      return visited.stream()
        .filter(BoundaryTree.class::isInstance)
        .map(state -> ((BoundaryTree) state).type())
        .anyMatch(TYPE_ENDINGS::contains);
    }
    return false;
  }

  private static boolean isEscapeSequence(AutomatonState state) {
    return state instanceof CharacterTree && ((CharacterTree) state).isEscapeSequence();
  }

  private static boolean isDotall(AutomatonState currentState) {
    return currentState instanceof DotTree && currentState.activeFlags().contains(Pattern.DOTALL);
  }
}
