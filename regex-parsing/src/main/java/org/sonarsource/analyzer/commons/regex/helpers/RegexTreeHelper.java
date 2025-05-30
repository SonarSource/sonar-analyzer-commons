/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.helpers;


import java.util.HashSet;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;

import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.EPSILON;

public class RegexTreeHelper {

  private RegexTreeHelper() {
    // Utils class
  }

  /**
   * If both sub-automata have allowPrefix set to true, this method will check whether auto1 intersects
   * the prefix of auto2 or auto2 intersects the prefix of auto1. This is different than checking whether
   * the prefix of auto1 intersects the prefix of auto2 (which would always be true because both prefix
   * always contain the empty string).
   * defaultAnswer will be returned in case of unsupported features or the state limit is exceeded.
   * It should be whichever answer does not lead to an issue being reported to avoid false positives.
   */
  public static boolean intersects(SubAutomaton auto1, SubAutomaton auto2, boolean defaultAnswer) {
    return new IntersectAutomataChecker(defaultAnswer).check(auto1, auto2);
  }

  /**
   * Here auto2.allowPrefix means that if supersetOf(auto1, auto2), then for every string matched by auto2, auto1 can match a prefix of it
   * auto1.allowPrefix means that if supersetOf(auto1, auto2), then for every string matched by auto2, auto1 can match a continuation of it
   * If both are set, it means either one can be the case.
   */
  public static boolean supersetOf(SubAutomaton auto1, SubAutomaton auto2, boolean defaultAnswer) {
    return new SupersetAutomataChecker(defaultAnswer).check(auto1, auto2);
  }

  public static boolean isAnchoredAtEnd(AutomatonState start) {
    return isAnchoredAtEnd(start, new HashSet<>());
  }

  private static boolean isAnchoredAtEnd(AutomatonState start, Set<AutomatonState> visited) {
    if (isEndBoundary(start)) {
      return true;
    }
    if (start instanceof FinalState) {
      return false;
    }
    visited.add(start);
    for (AutomatonState successor : start.successors()) {
      if (!visited.contains(successor) && !isAnchoredAtEnd(successor, visited)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isEndBoundary(AutomatonState state) {
    if (!(state instanceof BoundaryTree)) {
      return false;
    }
    switch (((BoundaryTree) state).type()) {
      case LINE_END:
      case INPUT_END:
      case INPUT_END_FINAL_TERMINATOR:
        return true;
      default:
        return false;
    }
  }

  public static boolean onlyMatchesEmptySuffix(AutomatonState start) {
    return onlyMatchesEmptySuffix(start, new HashSet<>());
  }

  private static boolean onlyMatchesEmptySuffix(AutomatonState start, Set<AutomatonState> visited) {
    if (start instanceof FinalState || visited.contains(start)) {
      return true;
    }
    visited.add(start);
    if (start instanceof LookAroundTree) {
      return onlyMatchesEmptySuffix(start.continuation(), visited);
    }
    if (start.incomingTransitionType() != EPSILON) {
      return false;
    }

    for (AutomatonState successor : start.successors()) {
      if (!onlyMatchesEmptySuffix(successor, visited)) {
        return false;
      }
    }
    return true;
  }
}
