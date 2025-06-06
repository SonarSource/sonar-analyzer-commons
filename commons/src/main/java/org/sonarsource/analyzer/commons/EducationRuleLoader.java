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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.Context;
import org.sonar.api.server.rule.RuleDescriptionSection;
import org.sonar.api.server.rule.RuleDescriptionSectionBuilder;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.utils.Version;

import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.HOW_TO_FIX_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.INTRODUCTION_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.RESOURCES_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.ROOT_CAUSE_SECTION_KEY;

/**
 * This utility class helps loading the new content for progressive education rules.
 */
class EducationRuleLoader {

  private static final Logger LOG = LoggerFactory.getLogger(EducationRuleLoader.class);
  private static final String CODE_EXAMPLES_HEADER = "<h3>Code examples</h3>";
  private static final String WHY_SECTION_HEADER = "<h2>Why is this an issue\\?</h2>";
  private static final String HOW_TO_FIX_SECTION_HEADER = "<h2>How to fix it</h2>";
  private static final String RESOURCES_SECTION_HEADER = "<h2>Resources</h2>";
  private static final String HOW_TO_FIX_FRAMEWORK_SECTION_REGEX = "<h2>How to fix it in (?:(?:an|a|the)\\s)?(?<displayName>.*)</h2>";
  private static final Pattern HOW_TO_FIX_SECTION_PATTERN = Pattern.compile(HOW_TO_FIX_SECTION_HEADER);
  private static final Pattern HOW_TO_FIX_FRAMEWORK_SECTION_PATTERN = Pattern.compile(HOW_TO_FIX_FRAMEWORK_SECTION_REGEX);
  private static final Pattern WHY_SECTION_HEADER_PATTERN = Pattern.compile(WHY_SECTION_HEADER);

  // Runtime of the product in which the rules will be registered. This is needed as not all products/versions support the new format.
  private final SonarRuntime sonarRuntime;

  public EducationRuleLoader(SonarRuntime sonarRuntime) {
    this.sonarRuntime = sonarRuntime;
  }

  public void setEducationMetadataFromJson(NewRule rule, Map<String, Object> ruleMetadata) {
    if (!isEducationPrinciplesMetadataSupported()) {
      return;
    }

    Object propertyValue = ruleMetadata.get("educationPrinciples");
    if (propertyValue instanceof List) {
      String[] educationPrincipleKeys = ((List<String>) propertyValue).toArray(new String[0]);
      rule.addEducationPrincipleKeys(educationPrincipleKeys);
    }
  }

  public String setEducationDescriptionFromHtml(NewRule rule, String description) {
    if (!isEducationFormat(description)) {
      return description;
    }

    if (isEducationRuleDescriptionSupported()) {
      addEducationalRuleSections(rule, description);
    }
    return fallbackHtmlDescription(description);
  }

  /**
   * Creates a fallback HTML description based on the original education rule description. For products that do not support the new API yet, the descriptions are simplified to
   * only contain a single "How to fix it" section.
   */
  static String fallbackHtmlDescription(String description) {
    Matcher m = HOW_TO_FIX_FRAMEWORK_SECTION_PATTERN.matcher(description);
    // We only need to do something if there are two or more "How to fix it" sections.
    if (m.find() && m.find()) {
      int indexOfResourceSection = description.indexOf(RESOURCES_SECTION_HEADER);
      String resourceSectionDescription = indexOfResourceSection > 0 ? description.substring(indexOfResourceSection) : "";
      // The first part of the updated description is the beginning of the HTML file, up to the second "" found.
      return description.substring(0, m.start()) + resourceSectionDescription;
    }
    return description;
  }

  private static void addEducationalRuleSections(NewRule rule, String description) {
    // The "Why is this an issue?" section is expected.
    String[] split = description.split(WHY_SECTION_HEADER);

    // Adding the introduction section if not empty.
    addSection(rule, INTRODUCTION_SECTION_KEY, split[0]);
    split = split[1].split(RESOURCES_SECTION_HEADER);

    // Filtering out the "<h3>Code examples</h3>" title.
    String rootCauseAndHowToFixItSections = split[0].replace(CODE_EXAMPLES_HEADER, "");

    // Either the generic "How to fix it" section or at least one framework specific "How to fix it in <framework_name>" section is expected.
    Matcher frameworkSpecificHowToFixItSectionMatcher = HOW_TO_FIX_FRAMEWORK_SECTION_PATTERN.matcher(rootCauseAndHowToFixItSections);
    boolean hasFrameworkSpecificHowToFixItSection = frameworkSpecificHowToFixItSectionMatcher.find();
    boolean hasGenericHowToFixItSection = HOW_TO_FIX_SECTION_PATTERN.matcher(rootCauseAndHowToFixItSections).find();
    if (hasGenericHowToFixItSection && hasFrameworkSpecificHowToFixItSection) {
      throw new IllegalStateException(
        String.format("Invalid education rule format for '%s', rule description has both generic and framework-specific 'How to fix it' sections", rule.key()));
    } else if (hasFrameworkSpecificHowToFixItSection) {
      // Splitting by the "How to fix in <displayName>" will return an array where each element after the first is the content related to a given framework.
      String[] innerSplit = rootCauseAndHowToFixItSections.split(HOW_TO_FIX_FRAMEWORK_SECTION_REGEX);
      addSection(rule, ROOT_CAUSE_SECTION_KEY, innerSplit[0]);
      addContextSpecificHowToFixItSection(rule, innerSplit, frameworkSpecificHowToFixItSectionMatcher);
    } else if (hasGenericHowToFixItSection) {
      // Rule has the generic "How to fix it" section.
      String[] innerSplit = rootCauseAndHowToFixItSections.split(HOW_TO_FIX_SECTION_HEADER);
      addSection(rule, ROOT_CAUSE_SECTION_KEY, innerSplit[0]);
      addSection(rule, HOW_TO_FIX_SECTION_KEY, innerSplit[1]);
    } else {
      // No "How to fix it" section for the rule, the only section present is "Why is it an issue".
      addSection(rule, ROOT_CAUSE_SECTION_KEY, rootCauseAndHowToFixItSections);
    }

    // "Resources" section is optional.
    if (split.length > 1) {
      addSection(rule, RESOURCES_SECTION_KEY, split[1]);
    }
  }

  private static void addContextSpecificHowToFixItSection(NewRule rule, String[] split, Matcher m) {
    boolean match = true;
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
      return;
    }

    RuleDescriptionSectionBuilder sectionBuilder = RuleDescriptionSection
      .builder()
      .sectionKey(sectionKey)
      .htmlContent(trimmedContent)
      .context(context);

    rule.addDescriptionSection(sectionBuilder.build());
  }

  private static boolean isEducationFormat(String description) {
    return WHY_SECTION_HEADER_PATTERN.matcher(description).find();
  }

  // Visible for testing
  boolean isEducationRuleDescriptionSupported() {
    return sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(9, 5));
  }

  // Visible for testing
  boolean isEducationPrinciplesMetadataSupported() {
    return sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(9, 8));
  }
}
