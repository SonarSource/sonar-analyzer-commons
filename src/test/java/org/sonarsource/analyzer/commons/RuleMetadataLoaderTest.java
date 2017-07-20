/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.analyzer.commons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.check.Rule;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleMetadataLoaderTest {

  private final String RULE_REPOSITORY_KEY = "rule-definition-reader-test";
  private RulesDefinition.Context context;
  private NewRepository newRepository;
  private RuleMetadataLoader ruleMetadataLoader;

  @Before
  public void setup() {
    context = new RulesDefinition.Context();
    newRepository = context.createRepository(RULE_REPOSITORY_KEY, "magic");
    ruleMetadataLoader = new RuleMetadataLoader("org/sonarsource/analyzer/commons");
  }

  @Test
  public void load_rule_S100() throws Exception {
    @Rule(key = "S100") class TestRule {
    }

    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, list(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Function names should comply with a naming convention");
    assertThat(rule.htmlDescription()).isEqualTo("<p>description S100</p>");
    assertThat(rule.severity()).isEqualTo("MINOR");
    assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rule.status()).isEqualTo(RuleStatus.READY);
    assertThat(rule.tags()).containsExactly("convention");
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.CONSTANT_ISSUE);
    assertThat(remediation.baseEffort()).isEqualTo("5min");
  }

  @Test
  public void load_rule_S110() throws Exception {
    @Rule(key = "S110") class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, list(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S110");
    assertThat(rule).isNotNull();
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.LINEAR_OFFSET);
    assertThat(remediation.baseEffort()).isEqualTo("4h");
    assertThat(remediation.gapMultiplier()).isEqualTo("30min");
    assertThat(rule.gapDescription()).isEqualTo("Number of parents above the defined threshold");
  }

  @Test
  public void load_rules_key_based() throws Exception {
    ruleMetadataLoader.addRulesByRuleKey(newRepository, list("S110", "S100"));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule ruleS110 = repository.rule("S110");
    assertThat(ruleS110).isNotNull();
    assertThat(ruleS110.name()).isEqualTo("Inheritance tree of classes should not be too deep");
    assertThat(ruleS110.htmlDescription()).isEqualTo("<p>description S110</p>");

    RulesDefinition.Rule ruleS100 = repository.rule("S100");
    assertThat(ruleS100).isNotNull();
    assertThat(ruleS100.name()).isEqualTo("Function names should comply with a naming convention");
    assertThat(ruleS100.htmlDescription()).isEqualTo("<p>description S100</p>");
  }

  @Test
  public void load_rule_S123() throws Exception {
    @Rule(key = "S123")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, list(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S123");
    assertThat(rule).isNotNull();
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.LINEAR);
    assertThat(remediation.gapMultiplier()).isEqualTo("10min");
    assertThat(rule.gapDescription()).isNull();
  }

  @Test
  public void load_rule_list() throws Exception {
    @Rule(key = "S100")
    class RuleA {
    }
    @Rule(key = "S110")
    class RuleB {
    }
    List<Class> rules = Arrays.asList(RuleA.class, RuleB.class);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, rules);
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rule("S100")).isNotNull();
    assertThat(repository.rule("S110")).isNotNull();
  }

  private static <T> List<T> list(T ...elements) {
    List<T> list = new ArrayList<T>();
    for (T element : elements) {
      list.add(element);
    }

    return list;
  }
}
