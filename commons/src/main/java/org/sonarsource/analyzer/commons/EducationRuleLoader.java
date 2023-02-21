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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.Context;
import org.sonar.api.server.rule.RuleDescriptionSection;
import org.sonar.api.server.rule.RuleDescriptionSectionBuilder;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.HOW_TO_FIX_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.INTRODUCTION_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.RESOURCES_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.ROOT_CAUSE_SECTION_KEY;

/**
 * This utility class helps loading the new content for progressive education rules.
 * However, as the API around educational rules is still unstable and evolving, it makes it easier to keep the logic inside this analyzer for the moment.
 */
public class EducationRuleLoader {

  private static final Logger LOG = Loggers.get(EducationRuleLoader.class);
  private static final char RESOURCE_SEP = '/';
  private static final String HTML_EXTENSION = ".html";
  private static final String WHY_SECTION_HEADER = "<h2>Why is this an issue\\?</h2>";
  private static final String HOW_TO_FIX_SECTION_HEADER = "<h2>How to fix it\\?</h2>";
  private static final String RESOURCES_SECTION_HEADER = "<h2>Resources</h2>";

  private static final String HOW_TO_FIX_SECTION_REGEX = "<h3>How to fix it in (?:(?:an|a|the)\\s)?(?<displayName>.*)</h3>";
  private static final Pattern HOW_TO_FIX_SECTION_PATTERN = Pattern.compile(HOW_TO_FIX_SECTION_REGEX);

  // Path to the folder holding the html files related to the education rules.
  private final String educationResourceFolder;
  // Path to the folder holding the html and json files related to the standard rules.
  private final String standardResourceFolder;
  // Runtime of the product in which the rules will be registered. This is needed as not all products/versions support the new format.
  private final SonarRuntime sonarRuntime;

  private final JSONParser parser = new JSONParser();

  public EducationRuleLoader(String educationResourceFolder, String standardResourceFolder, SonarRuntime sonarRuntime) {
    this.educationResourceFolder = educationResourceFolder;
    this.standardResourceFolder = standardResourceFolder;
    this.sonarRuntime = sonarRuntime;
  }

  static boolean runtimeSupportsEducationRules(SonarRuntime sonarRuntime) {
    return SonarProduct.SONARQUBE == sonarRuntime.getProduct() && sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(9, 5));
  }

  /**
   * It is expected that rules for which the educational rule description content should be added were already initialized and added to the repository before calling this method.
   */
  public void addEducationRulesByAnnotatedClass(NewRepository repository, List<Class<?>> ruleClasses) {
    if (!runtimeSupportsEducationRules(sonarRuntime)) {
      return;
    }

    for (Class<?> ruleClass : ruleClasses) {
      addRuleByAnnotatedClass(repository, ruleClass);
    }
  }

  private void addRuleByAnnotatedClass(NewRepository repository, Class<?> ruleClass) {
    NewRule rule = findAnnotatedRule(repository, ruleClass);
    this.setEducationDescriptionFromHtml(rule);

    // Specific educational rules metadata API was added in sonar-api 9.8.
    if (sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(9, 8))) {
      this.setEducationMetadataFromJson(rule);
    }
  }

  private static NewRule findAnnotatedRule(NewRepository repository, Class<?> ruleClass) {
    org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getAnnotation(ruleClass, org.sonar.check.Rule.class);
    if (ruleAnnotation == null) {
      throw new IllegalStateException("No Rule annotation was found on " + ruleClass.getName());
    }
    String ruleKey = ruleAnnotation.key();
    if (ruleKey.length() == 0) {
      throw new IllegalStateException("Empty rule key");
    }
    NewRule rule = repository.rule(ruleKey);
    if (rule == null) {
      throw new IllegalStateException("Rule not found: " + ruleKey);
    }
    return rule;
  }

  private void setEducationDescriptionFromHtml(NewRule rule) {
    String htmlPath = educationResourceFolder + RESOURCE_SEP + rule.key() + HTML_EXTENSION;
    String description;
    try (InputStream resource = EducationRuleLoader.class.getClassLoader().getResourceAsStream(htmlPath)) {
      if (resource == null) {
        LOG.debug("No educational rule content for " + rule.key());
        return;
      }
      description = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      addEducationalRuleSections(rule, description);
    } catch (IOException e1) {
      LOG.error("Can't read resource: " + htmlPath, e1);
    } catch (IllegalStateException e2) {
      LOG.error(String.format("Unable to load education rule: %s. %s", htmlPath, e2.getMessage()));
    }
  }

  private static void addEducationalRuleSections(NewRule rule, String description) {
    String[] split = description.split(WHY_SECTION_HEADER);
    checkValidSplit(split, rule, WHY_SECTION_HEADER);
    addSection(rule, INTRODUCTION_SECTION_KEY, split[0]);

    split = split[1].split(HOW_TO_FIX_SECTION_HEADER);
    checkValidSplit(split, rule, HOW_TO_FIX_SECTION_HEADER);
    addSection(rule, ROOT_CAUSE_SECTION_KEY, split[0]);

    split = split[1].split(RESOURCES_SECTION_HEADER);
    checkValidSplit(split, rule, RESOURCES_SECTION_HEADER);

    addContextSpecificHowToFixItSection(rule, split[0]);

    addSection(rule, RESOURCES_SECTION_KEY, split[1]);
  }

  private static void addContextSpecificHowToFixItSection(NewRule rule, String content) {
    Matcher m = HOW_TO_FIX_SECTION_PATTERN.matcher(content);
    boolean match = m.find();
    if (!match) {
      throw new IllegalStateException(String.format("Invalid education rule format for %s, context based patch is missing.", rule.key()));
    }
    // Splitting by the "How to fix in <displayName>" will return an array where each element is the content related to a given framework.
    String[] split = content.split(HOW_TO_FIX_SECTION_REGEX);
    // We skip index 0 because it will be an empty string.
    int splitIndex = 1;
    while (match) {
      String displayName = m.group("displayName").trim();
      String contextSpecificContent = split[splitIndex];
      String key = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
      addSection(rule, HOW_TO_FIX_SECTION_KEY, contextSpecificContent, new Context(key, displayName));
      match = m.find();
      splitIndex++;
    }
  }

  private static void addSection(NewRule rule, String sectionKey, String content) {
    addSection(rule, sectionKey, content, null);
  }

  private static void addSection(NewRule rule, String sectionKey, String content, @Nullable Context context) {
    String trimmedContent = content.trim();
    if (trimmedContent.isEmpty()) {
      LOG.debug(String.format("Skipping section '%s' for rule '%s', content is empty", sectionKey, rule.key()));
      return;
    }

    RuleDescriptionSectionBuilder sectionBuilder = RuleDescriptionSection
      .builder()
      .sectionKey(sectionKey)
      .htmlContent(trimmedContent)
      .context(context);

    rule.addDescriptionSection(sectionBuilder.build());
  }

  private static void checkValidSplit(String[] split, NewRule rule, String missingHeader) {
    if (split.length != 2) {
      throw new IllegalStateException(String.format("Invalid education rule format for %s, following header is missing: '%s'", rule.key(), missingHeader));
    }
  }

  private void setEducationMetadataFromJson(NewRule rule) {
    String[] educationPrincipleKeys = educationPrincipleKeys(rule.key());
    if (educationPrincipleKeys != null) {
      rule.addEducationPrincipleKeys(educationPrincipleKeys);
    }
  }

  @CheckForNull
  String[] educationPrincipleKeys(String ruleKey) {
    Map<String, Object> ruleMetadata = getMetadata(ruleKey);
    Object propertyValue = ruleMetadata.get("educationPrinciples");
    if (!(propertyValue instanceof List)) {
      return null;
    }
    return ((List<String>) propertyValue).toArray(new String[0]);
  }

  private Map<String, Object> getMetadata(String ruleKey) {
    String jsonPath = standardResourceFolder + RESOURCE_SEP + ruleKey + ".json";
    try (InputStream resource = EducationRuleLoader.class.getClassLoader().getResourceAsStream(jsonPath)) {
      if (resource == null) {
        LOG.debug("No metadata found for rule " + ruleKey);
        return Collections.emptyMap();
      }
      String jsonContent = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      return (Map<String, Object>) parser.parse(jsonContent);
    } catch (IOException | IllegalStateException | ParseException e) {
      LOG.error("Can't read metadata: " + jsonPath, e);
      return Collections.emptyMap();
    }
  }
}
