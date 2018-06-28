/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2018 SonarSource SA
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

import org.junit.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class ExternalRuleLoaderTest {

  @Test
  public void test() throws Exception {
    RulesDefinition.Context context = new RulesDefinition.Context();
    ExternalRuleLoader externalRuleLoader = new ExternalRuleLoader(
      "my-linter-key",
      "MyLinter",
      "org/sonarsource/analyzer/commons/mylinter.json",
      "mylang");

    assertThat(externalRuleLoader.ruleType("not-existing-key")).isEqualTo(RuleType.CODE_SMELL);

    externalRuleLoader.createExternalRuleRepository(context);

    assertThat(externalRuleLoader.ruleType("not-existing-key")).isEqualTo(RuleType.CODE_SMELL);
    assertThat(externalRuleLoader.ruleType("bug-rule")).isEqualTo(RuleType.BUG);
    assertThat(externalRuleLoader.ruleType("code-smell-rule")).isEqualTo(RuleType.CODE_SMELL);
    assertThat(externalRuleLoader.ruleType("vulnerability-rule")).isEqualTo(RuleType.VULNERABILITY);
    assertThat(externalRuleLoader.ruleType("no-type-rule")).isEqualTo(RuleType.CODE_SMELL);

    assertThat(context.repositories()).hasSize(1);
    Repository repository = context.repository("external_my-linter-key");
    assertThat(repository.isExternal()).isTrue();
    assertThat(repository.name()).isEqualTo("MyLinter");
    assertThat(repository.language()).isEqualTo("mylang");
    assertThat(repository.rules()).hasSize(4);

    Rule rule1 = repository.rule("bug-rule");
    Rule rule2 = repository.rule("code-smell-rule");
    Rule rule3 = repository.rule("vulnerability-rule");
    Rule rule4 = repository.rule("no-type-rule");

    assertThat(rule1.htmlDescription()).isEqualTo("Bug Rule Description");
    assertThat(rule2.htmlDescription()).isEqualTo("See the description of MyLinter rule <code>code-smell-rule</code> at <a href=\"http://www.mylinter.org/code-smell-rule\">MyLinter website</a>.");
    assertThat(rule3.htmlDescription()).isEqualTo("Bug Rule Description. See more at <a href=\"http://www.mylinter.org/code-smell-rule\">MyLinter website</a>.");
    assertThat(rule4.htmlDescription()).isEqualTo("This is external rule <code>my-linter-key:no-type-rule</code>. No details are available.");

    assertThat(rule1.tags()).isEmpty();
    assertThat(rule2.tags()).containsOnly("tag1", "tag2");

    assertThat(rule2.severity()).isEqualTo("MAJOR");
    assertThat(rule3.severity()).isEqualTo("INFO");
    assertThat(rule4.severity()).isEqualTo("BLOCKER");
  }
}
