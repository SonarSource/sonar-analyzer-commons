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

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.BuiltInActiveRule;
import static org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.BuiltInQualityProfile;
import static org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.Context;
import static org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.NewBuiltInQualityProfile;

public class BuiltInQualityProfileJsonLoaderTest {

  private static final String PROFILE_PATH = "org/sonarsource/analyzer/commons/Sonar_way_profile.json";
  private static final String REPOSITORY_KEY = "repo-key";
  private static final String PROFILE_NAME = "profile-name";
  private static final String LANGUAGE = "lang-key";

  private Context testContext;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    testContext = new Context();
  }

  @Test
  public void load_profile_keys() {
    NewBuiltInQualityProfile newProfile = testContext.createBuiltInQualityProfile(PROFILE_NAME, LANGUAGE);
    BuiltInQualityProfileJsonLoader.load(newProfile, REPOSITORY_KEY, PROFILE_PATH);
    newProfile.done();

    BuiltInQualityProfile profile = testContext.profile(LANGUAGE, PROFILE_NAME);

    List<BuiltInActiveRule> activeRules = profile.rules();
    assertThat(activeRules).hasSize(2);
    assertThat(profile.rule(RuleKey.of(REPOSITORY_KEY, "S100"))).isNotNull();
    assertThat(profile.rule(RuleKey.of(REPOSITORY_KEY, "S110"))).isNotNull();
    assertThat(profile.rule(RuleKey.of(REPOSITORY_KEY, "S123"))).isNull();
    assertThat(profile.rule(RuleKey.of(REPOSITORY_KEY, "S666"))).isNull();
  }

  @Test
  public void fails_when_activating_rules_more_than_once() {
    NewBuiltInQualityProfile newProfile = testContext.createBuiltInQualityProfile(PROFILE_NAME, LANGUAGE);
    BuiltInQualityProfileJsonLoader.load(newProfile, REPOSITORY_KEY, PROFILE_PATH);

    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("The rule 'repo-key:S100' is already activated");

    BuiltInQualityProfileJsonLoader.load(newProfile, REPOSITORY_KEY, PROFILE_PATH);
  }

  @Test
  public void fails_when_no_profile_found() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Can't read resource: /wrong/path/Sonar_way_profile.json");

    NewBuiltInQualityProfile newProfile = testContext.createBuiltInQualityProfile(PROFILE_NAME, LANGUAGE);
    BuiltInQualityProfileJsonLoader.load(newProfile, REPOSITORY_KEY, "/wrong/path/Sonar_way_profile.json");
  }
}
