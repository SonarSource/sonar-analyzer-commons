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

import java.util.Set;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;

import static org.sonarsource.analyzer.commons.BuiltInQualityProfileJsonLoader.loadActiveKeysFromJsonProfile;

/**
 * Use to create {@link RulesProfile} based on json file.
 *
 * Not designed for multi-threads
 *
 * @deprecated since 1.6. Use {@link BuiltInQualityProfileJsonLoader} instead.
 */
@Deprecated
public final class ProfileDefinitionReader {

  private final RuleFinder ruleFinder;

  public ProfileDefinitionReader(RuleFinder ruleFinder) {
    this.ruleFinder = ruleFinder;
  }

  public void activateRules(RulesProfile profile, String repositoryKey, String profilePath) {
    Set<String> activeKeys = loadActiveKeysFromJsonProfile(profilePath);
    for (String activeKey : activeKeys) {
      Rule rule = ruleFinder.findByKey(repositoryKey, activeKey);
      if (rule == null) {
        String errorMessage = "Failed to activate rule with key '%s'. No corresponding rule found in repository with key '%s'.";
        throw new IllegalStateException(String.format(errorMessage, activeKey, repositoryKey));
      }

      profile.activateRule(rule, null);
    }
  }
}
