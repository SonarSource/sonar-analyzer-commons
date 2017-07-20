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

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Use to create {@link RulesProfile} based on json file.
 *
 * Not designed for multi-threads
 */
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

  private Set<String> loadActiveKeysFromJsonProfile(String profilePath) {
    JsonParser jsonParser = new JsonParser();
    Map<String, Object> root;
    try {
      root = jsonParser.parse(Resources.toString(profilePath, UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + profilePath, e);
    }
    Map<String, Object> ruleKeys = (Map<String, Object>) root.get("ruleKeys");
    if (ruleKeys == null) {
      throw new IllegalStateException("missing 'ruleKeys'");
    }
    return ruleKeys.values().stream().map(Object::toString).collect(Collectors.toSet());
  }

}
