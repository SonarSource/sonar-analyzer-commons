/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.analyzer.commons;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.NewBuiltInQualityProfile;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Use to create {@link NewBuiltInQualityProfile} based on json file, for profiles implementing {@link org.sonar.api.server.profile.BuiltInQualityProfilesDefinition}
 *
 * Not designed for multi-threads
 */
public final class BuiltInQualityProfileJsonLoader {

  private BuiltInQualityProfileJsonLoader() {
  }

  public static void load(NewBuiltInQualityProfile profile, String repositoryKey, String jsonProfilePath) {
    Set<String> activeKeys = loadActiveKeysFromJsonProfile(jsonProfilePath);
    for (String activeKey : activeKeys) {
      profile.activateRule(repositoryKey, activeKey);
    }
  }

  public static Set<String> loadActiveKeysFromJsonProfile(String profilePath) {
    JsonParser jsonParser = new JsonParser();
    Map<String, Object> root;
    try {
      root = jsonParser.parse(Resources.toString(profilePath, UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + profilePath, e);
    }
    List<String> ruleKeys = (List<String>) root.get("ruleKeys");
    if (ruleKeys == null) {
      throw new IllegalStateException("missing 'ruleKeys'");
    }
    return new HashSet<>(ruleKeys);
  }

}
