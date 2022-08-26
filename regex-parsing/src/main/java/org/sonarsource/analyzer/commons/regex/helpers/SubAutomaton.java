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

import java.util.Objects;
import java.util.stream.Stream;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

public class SubAutomaton {
  public final AutomatonState start;
  public final AutomatonState end;
  public final boolean allowPrefix;
  public final int lowerBound;

  public SubAutomaton(AutomatonState start, AutomatonState end, boolean allowPrefix) {
    this(start, end, -1, allowPrefix);
  }

  public SubAutomaton(AutomatonState start, AutomatonState end, int lowerBound, boolean allowPrefix) {
    this.start = start;
    this.end = end;
    this.allowPrefix = allowPrefix;
    this.lowerBound = lowerBound;
  }

  public TransitionType incomingTransitionType() {
    return start.incomingTransitionType();
  }

  public boolean isAtEnd() {
    return start == end;
  }

  public Stream<SubAutomaton> successorsAutomata() {
    return start.successors().stream()
      .filter(successor ->
        successor.toRegexTree().filter(tree -> tree.is(RegexTree.Kind.REPETITION)).isPresent() ||
        successor.toRegexTree().map(tree -> tree.getRange().getBeginningOffset() >= lowerBound).orElse(true))
      .map(successor -> new SubAutomaton(successor, end, lowerBound, allowPrefix));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SubAutomaton automaton = (SubAutomaton) o;
    return allowPrefix == automaton.allowPrefix &&
      lowerBound == automaton.lowerBound &&
      Objects.equals(start, automaton.start) &&
      Objects.equals(end, automaton.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end, lowerBound, allowPrefix);
  }
}

