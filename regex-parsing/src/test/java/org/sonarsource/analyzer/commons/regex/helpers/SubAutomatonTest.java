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


import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SubAutomatonTest {

  @Test
  void testEqualsAndHashcode() {
    SubAutomaton subAutomaton1 = new SubAutomaton(new FinalState(new FlagSet()), new FinalState(new FlagSet()), false);
    SubAutomaton subAutomaton2 = new SubAutomaton(new FinalState(new FlagSet()), new FinalState(new FlagSet()), false);
    SubAutomaton subAutomaton3 = new SubAutomaton(subAutomaton1.start, subAutomaton1.end, true);
    SubAutomaton subAutomaton4 = new SubAutomaton(subAutomaton1.start, subAutomaton1.end, false);
    SubAutomaton subAutomaton5 = new SubAutomaton(subAutomaton1.start, subAutomaton2.end, false);
    SubAutomaton subAutomaton6 = new SubAutomaton(subAutomaton2.start, subAutomaton1.end, false);
    SubAutomaton subAutomaton7 = new SubAutomaton(subAutomaton1.start, subAutomaton2.end, new IndexRange(0, 3), false);

    assertThat(subAutomaton1)
      .isNotEqualTo(null)
      .isNotEqualTo("null")
      .isNotEqualTo(subAutomaton2)
      .isNotEqualTo(subAutomaton3)
      .isNotEqualTo(subAutomaton5)
      .isNotEqualTo(subAutomaton6)
      .isNotEqualTo(subAutomaton7)
      .isEqualTo(subAutomaton4)
      .isEqualTo(subAutomaton1)
      .hasSameHashCodeAs(subAutomaton4);

    // isEqualTo() in this case doesn't actually call .equals() method
    assertThat(subAutomaton1.equals(subAutomaton1)).isTrue();
    assertThat(subAutomaton1.equals("subAutomaton1")).isFalse();
    assertThat(subAutomaton1.equals(null)).isFalse();
  }
}

