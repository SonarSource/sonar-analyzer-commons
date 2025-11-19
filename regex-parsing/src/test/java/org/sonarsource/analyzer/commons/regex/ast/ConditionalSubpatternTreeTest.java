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
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.SyntaxError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.parseRegex;

class ConditionalSubpatternTreeTest {

  @Test
  void conditionalSubpatternTree_with_reference() {
    assertConditionalSubpattern("(?(1)a)", ReferenceConditionTree.class, false);
    assertConditionalSubpattern("(?(2)a|b)", "2", true);
    assertConditionalSubpattern("(?(+1)a)", "+1", false);
    assertConditionalSubpattern("(?(+12)a)", "+12", false);
    assertConditionalSubpattern("(?(-1)a)", "-1", false);
    assertConditionalSubpattern("(?(<name>)a)", "<name>", false);
    assertConditionalSubpattern("(?('name')a)", "'name'", false);
    assertConditionalSubpattern("(?(R)a)", "R", false);
    assertConditionalSubpattern("(?(R2)a)", "R2", false);
    assertConditionalSubpattern("(?(R&name)a)", "R&name", false);
  }

  @Test
  void conditionalSubpatternTree_with_look_around() {
    assertConditionalSubpattern("(?(?=[^a-z])2)", LookAroundTree.class, false);
    assertConditionalSubpattern("(?(?=[^a-z])2|3)", LookAroundTree.class, true);
    assertConditionalSubpattern("(?(?<=[^a-z])2)", LookAroundTree.class, false);
    assertConditionalSubpattern("(?(?![^a-z])2)", LookAroundTree.class, false);
    assertConditionalSubpattern("(?(?<![^a-z])2)", LookAroundTree.class, false);
    assertConditionalSubpattern("(?(?=[^a-z]*[a-z])\\d{2}-[a-z]{3}-\\d{2}|\\d{2}-\\d{2}-\\d{2})", LookAroundTree.class, true);
  }

  @Test
  void elements_continuation() {
    ConditionalSubpatternTree tree = assertConditionalSubpattern("(?(1)()|())");

    BranchState conditionContinuation = (BranchState) tree.getCondition().continuation();
    assertThat(conditionContinuation.successors()).hasSize(2);
    assertThat(conditionContinuation.successors().get(0)).isEqualTo(tree.getYesPattern());
    assertThat(conditionContinuation.successors().get(1)).isEqualTo(tree.getNoPattern());

    assertThat(tree.getYesPattern().continuation()).isInstanceOf(EndOfConditionalSubpatternsState.class);
    assertThat(tree.getYesPattern().continuation().incomingTransitionType()).isEqualTo(AutomatonState.TransitionType.EPSILON);
    assertThat(tree.getYesPattern().continuation().continuation()).isInstanceOf(FinalState.class);

    assertThat(tree.getNoPattern().continuation()).isInstanceOf(EndOfConditionalSubpatternsState.class);
  }

  @Test
  void condition_continuation_without_noPattern() {
    ConditionalSubpatternTree tree = assertConditionalSubpattern("(?(1)())");
    BranchState conditionContinuation = (BranchState) tree.getCondition().continuation();
    assertThat(conditionContinuation.successors().get(0)).isEqualTo(tree.getYesPattern());
    assertThat(conditionContinuation.successors().get(1)).isInstanceOf(FinalState.class);
  }

  @Test
  void conditional_subpattern_without_support() {
    assertFailParsing("(?(a)b)", "Expected flag or ':' or ')', but found '('");
  }

  @Test
  void conditional_subpattern_with_to_many_subpattern() {
    RegexParseResult result = parseRegex("(?(1)a|b|c)", RegexFeature.CONDITIONAL_SUBPATTERN);
    assertThat(result.getSyntaxErrors().stream().map(SyntaxError::getMessage))
      .contains("More than two alternatives in the subpattern");
  }

  @Test
  void conditional_subpattern_with_invalid_reference() {
    RegexParseResult result = parseRegex("(?([1])a)", RegexFeature.CONDITIONAL_SUBPATTERN);
    assertThat(result.getSyntaxErrors().stream().map(SyntaxError::getMessage))
      .contains("Conditional subpattern has invalid condition.");
  }

  private ConditionalSubpatternTree assertConditionalSubpattern(String regex) {
    RegexTree tree = assertSuccessfulParse(regex, RegexFeature.CONDITIONAL_SUBPATTERN);
    return assertType(ConditionalSubpatternTree.class, tree);
  }

  private ConditionalSubpatternTree assertConditionalSubpattern(String regex, boolean hasNoPattern) {
    ConditionalSubpatternTree conditionalSubpattern = assertConditionalSubpattern(regex);
    assertThat(conditionalSubpattern.getYesPattern()).isNotNull();
    if (hasNoPattern) {
      assertThat(conditionalSubpattern.getPipe()).isNotNull();
      assertThat(conditionalSubpattern.getNoPattern()).isNotNull();
    } else {
      assertThat(conditionalSubpattern.getPipe()).isNull();
      assertThat(conditionalSubpattern.getNoPattern()).isNull();
    }
    return conditionalSubpattern;
  }

  private void assertConditionalSubpattern(String regex, Class<?> conditionType, boolean hasNoPattern) {
    ConditionalSubpatternTree conditionalSubpattern = assertConditionalSubpattern(regex, hasNoPattern);
    assertType(conditionType, conditionalSubpattern.getCondition());
  }

  private void assertConditionalSubpattern(String regex, String condition, boolean hasNoPattern) {
    ConditionalSubpatternTree conditionalSubpattern = assertConditionalSubpattern(regex, hasNoPattern);
    ReferenceConditionTree groupReferenceCondition = assertType(ReferenceConditionTree.class, conditionalSubpattern.getCondition());
    assertThat(groupReferenceCondition.getReference()).isEqualTo(condition);
  }

}
