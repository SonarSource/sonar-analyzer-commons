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
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedAutomataPairCacheTest {

  AbstractAutomataChecker.OrderedAutomataPairCache<String> cache = new AbstractAutomataChecker.OrderedAutomataPairCache<>();

  @Test
  void test() {
    for (int i = 0; i < AbstractAutomataChecker.OrderedAutomataPairCache.MAX_CACHE_SIZE; i++) {
      AbstractAutomataChecker.OrderedAutomataPair pair = createPair();
      assertThat(cache.startCalculation(pair, "default")).isNull();
      assertThat(cache.startCalculation(pair, "default")).isEqualTo("default");
      assertThat(cache.save(pair, "foo")).isEqualTo("foo");
      assertThat(cache.startCalculation(pair, "default")).isEqualTo("foo");
    }
    assertThat(cache.startCalculation(createPair(), "default")).isEqualTo("default");
    assertThat(cache.startCalculation(createPair(), "default")).isEqualTo("default");
  }

  private static AbstractAutomataChecker.OrderedAutomataPair createPair() {
    SubAutomaton automaton1 = new SubAutomaton(new FinalState(new FlagSet()), new FinalState(new FlagSet()), false);
    SubAutomaton automaton2 = new SubAutomaton(new FinalState(new FlagSet()), new FinalState(new FlagSet()), false);
    return new AbstractAutomataChecker.OrderedAutomataPair(automaton1, automaton2, false);
  }

}
