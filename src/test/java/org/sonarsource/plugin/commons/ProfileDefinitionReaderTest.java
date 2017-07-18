/*
 * SonarQube Plugin Commons
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
package org.sonarsource.plugin.commons;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
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

  @Before
  public void setUp() throws Exception {
    ruleFinder = mock(RuleFinder.class);
    when(ruleFinder.findByKey(anyString(), anyString())).thenAnswer(new Answer<Rule>() {
      @Override
      public Rule answer(InvocationOnMock iom) throws Throwable {
        String repositoryKey = (String) iom.getArguments()[0];
        String ruleKey = (String) iom.getArguments()[1];
        return Rule.create(repositoryKey, ruleKey, ruleKey);
      }
    });
  }

  @Test
  public void load_profile_keys() throws Exception {
    @org.sonar.check.Rule(key = "S100")
    class RuleA {
    }
    @org.sonar.check.Rule(key = "S110")
    class RuleB {
    }
    @org.sonar.check.Rule(key = "S666")
    class RuleC {
    }
    List<Class> rules = Arrays.asList(RuleA.class, RuleB.class, RuleC.class);
    RulesProfile profile = RulesProfile.create("profile-name", "lang-key");
    ProfileDefinitionReader definitionReader = new ProfileDefinitionReader(ruleFinder);
    definitionReader.activateRules(profile, "repo-key", rules, "org/sonarsource/plugin/commons/Sonar_way_profile.json");
    assertThat(profile.getActiveRules()).hasSize(2);
    assertThat(profile.getActiveRule("repo-key", "S100")).isNotNull();
    assertThat(profile.getActiveRule("repo-key", "S110")).isNotNull();
    assertThat(profile.getActiveRule("repo-key", "S666")).isNull();
  }

}
