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
import org.junit.rules.ExpectedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProfileDefinitionReaderTest {

  private RuleFinder ruleFinder;

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void load_profile_keys() throws Exception {
    ruleFinder = mock(RuleFinder.class);
    when(ruleFinder.findByKey(anyString(), anyString())).thenAnswer(new Answer<Rule>() {
      @Override
      public Rule answer(InvocationOnMock iom) throws Throwable {
        String repositoryKey = (String) iom.getArguments()[0];
        String ruleKey = (String) iom.getArguments()[1];
        return Rule.create(repositoryKey, ruleKey, ruleKey);
      }
    });

    RulesProfile profile = RulesProfile.create("profile-name", "lang-key");
    ProfileDefinitionReader definitionReader = new ProfileDefinitionReader(ruleFinder);
    definitionReader.activateRules(profile, "repo-key", "org/sonarsource/analyzer/commons/Sonar_way_profile.json");
    assertThat(profile.getActiveRules()).hasSize(2);
    assertThat(profile.getActiveRule("repo-key", "S100")).isNotNull();
    assertThat(profile.getActiveRule("repo-key", "S110")).isNotNull();
    assertThat(profile.getActiveRule("repo-key", "S123")).isNull();
    assertThat(profile.getActiveRule("repo-key", "S666")).isNull();
  }

  @Test
  public void fails_with_non_existing_rule_key() throws Exception {
    ruleFinder = mock(RuleFinder.class);
    when(ruleFinder.findByKey(anyString(), anyString())).thenAnswer(new Answer<Rule>() {
      @Override
      public Rule answer(InvocationOnMock iom) throws Throwable {
        String repositoryKey = (String) iom.getArguments()[0];
        String ruleKey = (String) iom.getArguments()[1];
        if (ruleKey.equals("S666")) {
          return null;
        }
        return Rule.create(repositoryKey, ruleKey, ruleKey);
      }
    });

    RulesProfile profile = RulesProfile.create("profile-name", "lang-key");
    ProfileDefinitionReader definitionReader = new ProfileDefinitionReader(ruleFinder);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failed to activate rule with key 'S666'. No corresponding rule found in repository with key 'repo-key'.");

    definitionReader.activateRules(profile, "repo-key", "org/sonarsource/analyzer/commons/Sonar_way_profile_invalid.json");
  }

}
