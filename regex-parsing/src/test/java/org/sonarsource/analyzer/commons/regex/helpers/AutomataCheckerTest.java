/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.helpers;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexSource;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AutomataCheckerTest {

  DotTree dot = new DotTree(mock(RegexSource.class), mock(IndexRange.class), new FlagSet(Pattern.DOTALL));
  SubAutomaton dotClassSubAutomaton = new SubAutomaton(dot, new FinalState(new FlagSet()), false);
  SubAutomaton nonDotClassSubAutomaton = new SubAutomaton(new FinalState(new FlagSet()), new FinalState(new FlagSet()), false);

  SupersetAutomataChecker supersetAutomataChecker = new SupersetAutomataChecker(true);
  IntersectAutomataChecker intersectAutomataChecker = new IntersectAutomataChecker(true);

  @Test
  void test_AutomataChecker_with_one_invalid_SubAutomaton() {
    assertThat(supersetAutomataChecker.checkAuto1AndAuto2Successors(dotClassSubAutomaton, nonDotClassSubAutomaton, true, false)).isTrue();
    assertThat(intersectAutomataChecker.checkAuto1AndAuto2Successors(dotClassSubAutomaton, nonDotClassSubAutomaton, true, false)).isTrue();
  }

  @Test
  void test_AutomataChecker_with_two_invalid_SubAutomaton() {
    assertThat(supersetAutomataChecker.checkAuto1AndAuto2Successors(nonDotClassSubAutomaton, nonDotClassSubAutomaton, true, false)).isTrue();
    assertThat(intersectAutomataChecker.checkAuto1AndAuto2Successors(nonDotClassSubAutomaton, nonDotClassSubAutomaton, true, false)).isTrue();
  }
}
