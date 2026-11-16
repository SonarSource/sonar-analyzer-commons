/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegexTreeHelperPosixTest {

  @Test
  void checks_unicode_case_flags_across_the_whole_sequence() {
    String superset = "\\p{ASCII}(?i:k)";
    String subset = "a(?iu:k)";
    assertThat(Pattern.compile(superset).matcher("a\u212A").matches()).isFalse();
    assertThat(Pattern.compile(subset).matcher("a\u212A").matches()).isTrue();
    SubAutomaton first = automaton(parse(superset));
    SubAutomaton second = automaton(parse(subset));
    assertThat(RegexTreeHelper.supersetOf(first, second, false)).isFalse();
    assertThat(RegexTreeHelper.supersetOf(first, second, true)).isTrue();
    assertThat(new SupersetAutomataChecker(false).check(first, second)).isFalse();
  }

  @Test
  void checks_unicode_case_flags_before_proving_intersections() {
    String left = "\\p{ASCII}(?iu:[^k])";
    String right = "a(?iu:\u212A)";
    assertThat(Pattern.compile(left).matcher("ak").matches()).isFalse();
    assertThat(Pattern.compile(right).matcher("ak").matches()).isTrue();
    SubAutomaton first = automaton(parse(left));
    SubAutomaton second = automaton(parse(right));
    assertThat(RegexTreeHelper.intersects(first, second, false)).isFalse();
    assertThat(RegexTreeHelper.intersects(first, second, true)).isTrue();
    assertThat(new IntersectAutomataChecker(false).check(first, second)).isFalse();
  }

  @Test
  void ignores_flags_outside_the_subautomaton() {
    SequenceTree sequence = (SequenceTree) parse("\\p{ASCII}(?iu:k)");
    SubAutomaton prefix = new SubAutomaton(sequence.getItems().get(0), sequence.getItems().get(1), false);
    assertThat(RegexTreeHelper.supersetOf(prefix, automaton(parse("a")), false)).isTrue();
  }

  private static SubAutomaton automaton(RegexTree tree) {
    return new SubAutomaton(tree, tree.continuation(), false);
  }

  private static RegexTree parse(String regex) {
    RegexParseResult result = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return result.getResult();
  }
}
