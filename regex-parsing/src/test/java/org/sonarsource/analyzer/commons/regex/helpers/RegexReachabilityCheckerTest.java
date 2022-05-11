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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexSource;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.sonarsource.analyzer.commons.regex.ast.AutomatonState.TransitionType.EPSILON;
import static org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker.canReachWithoutConsumingInput;
import static org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker.canReachWithoutConsumingInputOrGoingThroughBoundaries;

class RegexReachabilityCheckerTest {

  @Test
  void can_reach_equals() {
    RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(true);
    AutomatonState start = mock(AutomatonState.class);
    assertThat(regexReachabilityChecker.canReach(start, start)).isTrue();
  }

  @Test
  void can_reach_successor() {
    RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(true);
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);
    // Unrelated states: can reach is false, populates cache
    assertThat(regexReachabilityChecker.canReach(start, end)).isFalse();

    doReturn(Collections.singletonList(end)).when(start).successors();
    // Returns value from the cache (false) despite successor now set
    assertThat(regexReachabilityChecker.canReach(start, end)).isFalse();

    regexReachabilityChecker.clearCache();
    assertThat(regexReachabilityChecker.canReach(start, end)).isTrue();
  }

  @Test
  void can_reach_multiple_successors() {
    RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(true);
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState intermediate = mock(AutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);

    doReturn(Collections.singletonList(intermediate)).when(start).successors();
    doReturn(Collections.singletonList(end)).when(intermediate).successors();
    assertThat(regexReachabilityChecker.canReach(start, end)).isTrue();
  }

  @Test
  void can_reach_cyclic_successors() {
    RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(true);
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState intermediate = mock(AutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);

    doReturn(Collections.singletonList(intermediate)).when(start).successors();
    doReturn(Collections.singletonList(start)).when(intermediate).successors();

    assertThat(regexReachabilityChecker.canReach(start, end)).isFalse();
  }

  @Test
  void can_reach_full_cache() {
    RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(true);
    AutomatonState end = mock(AutomatonState.class);
    for (int i = 0; i<5001;i++) {
      AutomatonState start = end;
      end = mock(AutomatonState.class);
      if (i < 5000) {
        assertThat(regexReachabilityChecker.canReach(start, end)).isFalse();
      } else {
        // Cache is full: return defaultAnswer
        assertThat(regexReachabilityChecker.canReach(start, end)).isTrue();
      }
    }
  }

  @Test
  void can_reach_from_static_method() {
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);
    assertThat(canReachWithoutConsumingInput(start, end)).isFalse();

    doReturn(Collections.singletonList(end)).when(start).successors();
    doReturn(EPSILON).when(end).incomingTransitionType();
    // Result is not cached
    assertThat(canReachWithoutConsumingInput(start, end)).isTrue();
  }


  @Test
  void can_reach_without_consuming_or_going_through_boundary() {
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState intermediate = mock(AutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);

    doReturn(Collections.singletonList(intermediate)).when(start).successors();
    doReturn(EPSILON).when(intermediate).incomingTransitionType();
    doReturn(Collections.singletonList(end)).when(intermediate).successors();
    doReturn(EPSILON).when(end).incomingTransitionType();

    assertThat(canReachWithoutConsumingInputOrGoingThroughBoundaries(start, end)).isTrue();
  }

  @Test
  void can_reach_without_consuming_or_going_through_boundary_should_stop_at_boundary() {
    AutomatonState start = mock(AutomatonState.class);
    AutomatonState intermediate = mock(BoundaryAutomatonState.class);
    AutomatonState end = mock(AutomatonState.class);

    doReturn(Collections.singletonList(intermediate)).when(start).successors();
    doReturn(EPSILON).when(intermediate).incomingTransitionType();
    doReturn(Collections.singletonList(end)).when(intermediate).successors();
    doReturn(EPSILON).when(end).incomingTransitionType();

    assertThat(canReachWithoutConsumingInputOrGoingThroughBoundaries(start, end)).isFalse();
  }

  private static class BoundaryAutomatonState extends BoundaryTree implements AutomatonState {
    public BoundaryAutomatonState(RegexSource source, Type type, IndexRange range, FlagSet activeFlags) {
      super(source, type, range, activeFlags);
    }
  }
}
