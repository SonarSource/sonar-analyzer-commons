/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.NewBuiltInQualityProfile;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Use to create {@link NewBuiltInQualityProfile} based on json file, for profiles implementing {@link org.sonar.api.server.profile.BuiltInQualityProfilesDefinition}
 *
 * Not designed for multi-threads
 */
public final class BuiltInQualityProfileJsonLoader {

  public static class Builder {

    private Builder() {
    }

    @Nullable
    private Function<String, InputStream> resourceProvider;

    public Builder withResourceProvider(Function<String, InputStream> resourceProvider) {
      this.resourceProvider = Objects.requireNonNull(resourceProvider);
      return this;
    }

    public BuiltInQualityProfileJsonLoader build() {
      return new BuiltInQualityProfileJsonLoader(resourceProvider);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BuiltInQualityProfileJsonLoader loader() {
    return new BuiltInQualityProfileJsonLoader(null);
  }

  @Nullable
  private final Function<String, InputStream> resourceProvider;

  private BuiltInQualityProfileJsonLoader(@Nullable Function<String, InputStream> resourceProvider) {
    this.resourceProvider = resourceProvider;
  }

  /**
   * @deprecated since 1.27 - Use {@link #loader()}.{@link #loadProfile(NewBuiltInQualityProfile, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public static void load(NewBuiltInQualityProfile profile, String repositoryKey, String jsonProfilePath) {
    new BuiltInQualityProfileJsonLoader(null).loadProfile(profile, repositoryKey, jsonProfilePath);
  }

  public void loadProfile(NewBuiltInQualityProfile profile, String repositoryKey, String jsonProfilePath) {
    Set<String> activeKeys = activeKeysFromJsonProfile(jsonProfilePath);
    for (String activeKey : activeKeys) {
      profile.activateRule(repositoryKey, activeKey);
    }
  }

  /**
   * @deprecated since 1.27 - Use {@link #loader()}.{@link #activeKeysFromJsonProfile(String)} instead
   */
  @Deprecated(forRemoval = true)
  public static Set<String> loadActiveKeysFromJsonProfile(String profilePath) {
    return new BuiltInQualityProfileJsonLoader(null).activeKeysFromJsonProfile(profilePath);
  }

  public Set<String> activeKeysFromJsonProfile(String profilePath) {
    JsonParser jsonParser = new JsonParser();
    Map<String, Object> root;
    try {
      root = jsonParser.parse(Resources.toString(resourceProvider, profilePath, UTF_8));
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
