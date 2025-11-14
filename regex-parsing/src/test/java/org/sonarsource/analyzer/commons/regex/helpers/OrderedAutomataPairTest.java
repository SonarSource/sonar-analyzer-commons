/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedAutomataPairTest {

  @Test
  void equals_and_hashCode() {
    AutomatonState state11 = new FinalState(new FlagSet());
    AutomatonState state12 = new FinalState(new FlagSet());
    AutomatonState state21 = new FinalState(new FlagSet());
    AutomatonState state22 = new FinalState(new FlagSet());

    SubAutomaton auto1 = new SubAutomaton(state11, state12, false);
    SubAutomaton auto2 = new SubAutomaton(state21, state22, false);

    AbstractAutomataChecker.OrderedAutomataPair pairS1S2A = new AbstractAutomataChecker.OrderedAutomataPair(auto1, auto2, false);
    assertThat(pairS1S2A)
      .isNotNull()
      .isNotEqualTo("")
      .isEqualTo(pairS1S2A)
      .hasSameHashCodeAs(pairS1S2A);

    // isEqualTo() in this case doesn't actually call .equals() method
    assertThat(pairS1S2A.equals(pairS1S2A)).isTrue();
    assertThat(pairS1S2A.equals("pairS1S2A")).isFalse();
    assertThat(pairS1S2A.equals(null)).isFalse();

    AbstractAutomataChecker.OrderedAutomataPair pairS1S2B = new AbstractAutomataChecker.OrderedAutomataPair(auto1, auto2, false);
    assertThat(pairS1S2B).isEqualTo(pairS1S2A).hasSameHashCodeAs(pairS1S2A);

    AbstractAutomataChecker.OrderedAutomataPair pairS1S2BWithConsumedInput = new AbstractAutomataChecker.OrderedAutomataPair(auto1, auto2, true);
    assertThat(pairS1S2BWithConsumedInput).isNotEqualTo(pairS1S2A);


    AbstractAutomataChecker.OrderedAutomataPair pairS2S1 = new AbstractAutomataChecker.OrderedAutomataPair(auto2, auto1, false);
    assertThat(pairS2S1).isNotEqualTo(pairS1S2A);

    AbstractAutomataChecker.OrderedAutomataPair pairS1S1 = new AbstractAutomataChecker.OrderedAutomataPair(auto1, auto1, false);
    assertThat(pairS1S1).isNotEqualTo(pairS1S2A);
  }

}

