/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.batch.sensor.decision.DecisionRulesDefinition;
import org.sonar.api.batch.sensor.decision.DecisionRulesDefinition.NewDecisionRule;
import org.sonar.api.batch.sensor.decision.DecisionRulesDefinition.Policy;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.testfixtures.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class DecisionRuleLoaderTest {

  private static final String RESOURCE_FOLDER = "org/sonarsource/analyzer/commons/decisions";

  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.WARN);

  private final RecordingContext context = new RecordingContext();
  private final DecisionRuleLoader loader = new DecisionRuleLoader(RESOURCE_FOLDER);

  @Test
  public void declares_every_field_and_keeps_the_order_of_the_facts() {
    loader.addDecisionRules(context, "repo", List.of("S1"));

    assertThat(context.saved).hasSize(1);
    RecordedRule rule = context.saved.get(0);
    assertThat(rule.ruleKey).isEqualTo(RuleKey.of("repo", "S1"));
    assertThat(rule.version).isEqualTo(2);
    assertThat(rule.question).isEqualTo("Is this finding a true positive?");
    assertThat(rule.instructions).isEqualTo("Answer from the facts only.");
    assertThat(rule.facts).containsExactly(
      entry("testName", "The name of the unit test"),
      entry("testBody", "The body of the unit test"),
      entry("imports", "List of imports in the file"));
    assertThat(rule.keyFacts).containsExactly("testName", "testBody");
    assertThat(rule.policy).isEqualTo(Policy.PREFER_FALSE_NEGATIVE);
    assertThat(rule.confidenceThreshold).isEqualTo(0.9);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void leaves_optional_fields_unset() {
    loader.addDecisionRules(context, "repo", List.of("S2"));

    assertThat(context.saved).hasSize(1);
    RecordedRule rule = context.saved.get(0);
    assertThat(rule.policy).isEqualTo(Policy.PREFER_FALSE_POSITIVE);
    assertThat(rule.keyFacts).isNull();
    assertThat(rule.confidenceThreshold).isNull();
  }

  @Test
  public void skips_rules_without_a_decision_file() {
    loader.addDecisionRules(context, "repo", List.of("S6", "S2"));

    assertThat(context.saved).extracting(rule -> rule.ruleKey).containsExactly(RuleKey.of("repo", "S2"));
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void logs_and_skips_invalid_decision_files() {
    loader.addDecisionRules(context, "repo", List.of("S3", "S4", "S5", "S2"));

    assertThat(context.saved).extracting(rule -> rule.ruleKey).containsExactly(RuleKey.of("repo", "S2"));
    assertThat(logTester.logs(Level.WARN)).containsExactly(
      "Invalid decision file /" + RESOURCE_FOLDER + "/S3_decision.json: No enum constant "
        + "org.sonar.api.batch.sensor.decision.DecisionRulesDefinition.Policy.PREFER_NOTHING",
      "Invalid decision file /" + RESOURCE_FOLDER + "/S4_decision.json: Could not parse JSON",
      "Invalid decision file /" + RESOURCE_FOLDER + "/S5_decision.json: 'version' must be a number");
  }

  private static class RecordingContext implements DecisionRulesDefinition.Context {
    private final List<RecordedRule> saved = new ArrayList<>();

    @Override
    public NewDecisionRule newDecisionRule(RuleKey ruleKey) {
      return new RecordedRule(ruleKey, saved);
    }
  }

  private static class RecordedRule implements NewDecisionRule {
    private final RuleKey ruleKey;
    private final List<RecordedRule> saved;
    private final Map<String, String> facts = new LinkedHashMap<>();
    private int version;
    private String question;
    private String instructions;
    private List<String> keyFacts;
    private Policy policy;
    private Double confidenceThreshold;

    private RecordedRule(RuleKey ruleKey, List<RecordedRule> saved) {
      this.ruleKey = ruleKey;
      this.saved = saved;
    }

    @Override
    public NewDecisionRule setVersion(int version) {
      this.version = version;
      return this;
    }

    @Override
    public NewDecisionRule setQuestion(String question) {
      this.question = question;
      return this;
    }

    @Override
    public NewDecisionRule setInstructions(String instructions) {
      this.instructions = instructions;
      return this;
    }

    @Override
    public NewDecisionRule addFact(String name, String description) {
      facts.put(name, description);
      return this;
    }

    @Override
    public NewDecisionRule setKeyFacts(List<String> names) {
      this.keyFacts = names;
      return this;
    }

    @Override
    public NewDecisionRule setPolicy(Policy policy) {
      this.policy = policy;
      return this;
    }

    @Override
    public NewDecisionRule setConfidenceThreshold(double threshold) {
      this.confidenceThreshold = threshold;
      return this;
    }

    @Override
    public void save() {
      saved.add(this);
    }
  }
}
