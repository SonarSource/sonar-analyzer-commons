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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.RuleAnnotationUtils;
import org.sonar.api.rules.RuleFinder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Not designed for multi-threads
 */
public final class ProfileDefinitionReader {

  private final RuleFinder ruleFinder;
  private JsonParser jsonParser;

  public ProfileDefinitionReader(RuleFinder ruleFinder) {
    this.ruleFinder = ruleFinder;
    jsonParser = new JsonParser();
  }

  public void activateRules(RulesProfile profile, String repositoryKey, List<Class> ruleClasses, String profilePath) {
    Set<String> activeKeys = loadActiveKeysFromJsonProfile(profilePath);
    for (Class ruleClass : ruleClasses) {
      String ruleKey = RuleAnnotationUtils.getRuleKey(ruleClass);
      if (activeKeys.contains(ruleKey)) {
        profile.activateRule(ruleFinder.findByKey(repositoryKey, ruleKey), null);
      }
    }
  }

  private Set<String> loadActiveKeysFromJsonProfile(String profilePath) {
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
