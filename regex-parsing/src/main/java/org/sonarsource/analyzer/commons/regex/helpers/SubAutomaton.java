/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.helpers;

import java.util.Objects;
import java.util.function.Predicate;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;

public class SubAutomaton {
  public final AutomatonState start;
  public final AutomatonState end;
  public final IndexRange excludedRange;
  public final boolean allowPrefix;

  public SubAutomaton(AutomatonState start, AutomatonState end, boolean allowPrefix) {
    this(start, end, new IndexRange(-1, -1), allowPrefix);
  }

  public SubAutomaton(AutomatonState start, AutomatonState end, IndexRange excludedRange, boolean allowPrefix) {
    this.start = start;
    this.end = end;
    this.allowPrefix = allowPrefix;
    this.excludedRange = excludedRange;
  }

  public TransitionType incomingTransitionType() {
    return start.incomingTransitionType();
  }

  public boolean isAtEnd() {
    return start == end;
  }

  public boolean anySuccessorMatch(Predicate<SubAutomaton> predicate) {
    for (AutomatonState successor : start.successors()) {
      if (successor.toRegexTree().map(tree -> !excludedRange.contains(tree.getRange())).orElse(true) &&
          predicate.test(new SubAutomaton(successor, end, excludedRange, allowPrefix))) {
        return true;
      }
    }
    return false;
  }

  public boolean allSuccessorMatch(Predicate<SubAutomaton> predicate) {
    for (AutomatonState successor : start.successors()) {
      if (successor.toRegexTree().map(tree -> !excludedRange.contains(tree.getRange())).orElse(true)
          && !predicate.test(new SubAutomaton(successor, end, excludedRange, allowPrefix))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SubAutomaton automaton = (SubAutomaton) o;
    return allowPrefix == automaton.allowPrefix &&
      Objects.equals(excludedRange, automaton.excludedRange) &&
      Objects.equals(start, automaton.start) &&
      Objects.equals(end, automaton.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end, excludedRange, allowPrefix);
  }
}

