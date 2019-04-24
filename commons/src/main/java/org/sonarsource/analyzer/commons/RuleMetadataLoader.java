/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.sonar.api.SonarRuntime;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.DebtRemediationFunctions;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.server.rule.RulesDefinitionAnnotationLoader;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.Version;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Not designed for multi-threads
 */
public class RuleMetadataLoader {

  private static final char RESOURCE_SEP = '/';
  private static final String SECURITY_HOTSPOT = "SECURITY_HOTSPOT";
  private static final Version SQ_7_3 = Version.create(7, 3);
  private final String resourceFolder;
  private final Set<String> activatedByDefault;
  private boolean supportsSecurityHotspots = false;
  private JsonParser jsonParser;
  private SonarRuntime sonarRuntime;

  public RuleMetadataLoader(String resourceFolder) {
    this(resourceFolder, Collections.emptySet());
  }

  /**
   * This constructor should not be used when SonarQube runtime version is less than 6.0
   * because it would trigger calls to {@link org.sonar.api.server.rule.RulesDefinition.NewRule#setActivatedByDefault(boolean)}
   * which was added in SonarQube 6.0.
   */
  public RuleMetadataLoader(String resourceFolder, String defaultProfilePath) {
    this(resourceFolder, BuiltInQualityProfileJsonLoader.loadActiveKeysFromJsonProfile(defaultProfilePath));
  }

  /**
   * Constructor used to deal with Security Hotspots rules:
   *
   * - SQ Version greather than 7.2: Security Hotspots Rules are supported
   * - otherwise: Security Hotspots Rules are considered as Vulnerability rules
   */
  public RuleMetadataLoader(String resourceFolder, String defaultProfilePath, SonarRuntime sonarRuntime) {
    this(resourceFolder, defaultProfilePath);
    this.sonarRuntime = sonarRuntime;
    this.supportsSecurityHotspots = securityHotspotsSupported();
  }

  private RuleMetadataLoader(String resourceFolder, Set<String> activatedByDefault) {
    this.resourceFolder = resourceFolder;
    this.jsonParser = new JsonParser();
    this.activatedByDefault = activatedByDefault;
  }

  public void addRulesByAnnotatedClass(NewRepository repository, List<Class> ruleClasses) {
    for (Class<?> ruleClass : ruleClasses) {
      addRuleByAnnotatedClass(repository, ruleClass);
    }
  }

  public void addRulesByRuleKey(NewRepository repository, List<String> ruleKeys) {
    for (String ruleKey : ruleKeys) {
      addRuleByRuleKey(repository, ruleKey);
    }
  }

  private NewRule addRuleByAnnotatedClass(NewRepository repository, Class<?> ruleClass) {
    NewRule rule = addAnnotatedRule(repository, ruleClass);
    setDescriptionFromHtml(rule);
    setMetadataFromJson(rule);
    setDefaultActivation(rule);
    return rule;
  }

  private void setDefaultActivation(NewRule rule) {
    if (activatedByDefault.contains(rule.key())) {
      // We should NOT call setActivatedByDefault if no default profile was provided:
      // this is how plugins should use this class when the runtime version of SQ does not support setActivatedByDefault
      rule.setActivatedByDefault(true);
    }
  }

  private static NewRule addAnnotatedRule(NewRepository repository, Class<?> ruleClass) {
    org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getAnnotation(ruleClass, org.sonar.check.Rule.class);
    if (ruleAnnotation == null) {
      throw new IllegalStateException("No Rule annotation was found on " + ruleClass.getName());
    }
    String ruleKey = ruleAnnotation.key();
    if (ruleKey.length() == 0) {
      throw new IllegalStateException("Empty key");
    }
    new RulesDefinitionAnnotationLoader().load(repository, ruleClass);
    NewRule rule = repository.rule(ruleKey);
    if (rule == null) {
      throw new IllegalStateException("Rule not found: " + ruleKey);
    }
    return rule;
  }

  private NewRule addRuleByRuleKey(NewRepository repository, String ruleKey) {
    if (ruleKey.length() == 0) {
      throw new IllegalStateException("Empty key");
    }
    NewRule rule = repository.createRule(ruleKey);
    setDescriptionFromHtml(rule);
    setMetadataFromJson(rule);
    setDefaultActivation(rule);
    return rule;
  }

  private void setDescriptionFromHtml(NewRule rule) {
    String htmlPath = resourceFolder + RESOURCE_SEP + rule.key() + ".html";
    String description;
    try {
      description = Resources.toString(htmlPath, UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + htmlPath, e);
    }
    rule.setHtmlDescription(description);
  }

  private void setMetadataFromJson(NewRule rule) {
    Map<String, Object> ruleMetadata = getMetadata(rule.key());
    rule.setName(getString(ruleMetadata, "title"));
    rule.setSeverity(getUpperCaseString(ruleMetadata, "defaultSeverity"));
    String type = getUpperCaseString(ruleMetadata, "type");
    if (isSecurityHotspot(ruleMetadata) && !supportsSecurityHotspots) {
      rule.setType(RuleType.VULNERABILITY);
    } else {
      rule.setType(RuleType.valueOf(type));
    }
    rule.setStatus(RuleStatus.valueOf(getUpperCaseString(ruleMetadata, "status")));
    rule.setTags(getStringArray(ruleMetadata, "tags"));

    Object remediation = ruleMetadata.get("remediation");
    if (remediation != null) {
      setRemediationFromJson(rule, (Map<String, Object>) remediation);
    }

    Object securityStandards = ruleMetadata.get("securityStandards");
    if (securityStandards != null && supportsSecurityHotspots) {
      setSecurityStandardsFromJson(rule, (Map<String, Object>) securityStandards);
    }
  }

  Map<String, Object> getMetadata(String ruleKey) {
    String jsonPath = resourceFolder + RESOURCE_SEP + ruleKey + ".json";
    try {
      return jsonParser.parse(Resources.toString(jsonPath, UTF_8));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Can't read resource: " + jsonPath, e);
    }
  }

  private static void setSecurityStandardsFromJson(NewRule rule, Map<String, Object> securityStandards) {
    if (securityStandards.get("OWASP") != null) {
      for (String standard : getStringArray(securityStandards, "OWASP")) {
        rule.addOwaspTop10(RulesDefinition.OwaspTop10.valueOf(standard));
      }
    }
    if (securityStandards.get("CWE") != null) {
      rule.addCwe(getIntArray(securityStandards, "CWE"));
    }
  }

  private static void setRemediationFromJson(NewRule rule, Map<String, Object> remediation) {
    String func = getString(remediation, "func");
    DebtRemediationFunctions remediationBuilder = rule.debtRemediationFunctions();
    if (func.startsWith("Constant")) {
      String constantCost = getString(remediation, "constantCost");
      rule.setDebtRemediationFunction(remediationBuilder.constantPerIssue(constantCost.replace("mn", "min")));
    } else if ("Linear".equals(func)) {
      String linearFactor = getString(remediation, "linearFactor");
      rule.setDebtRemediationFunction(remediationBuilder.linear(linearFactor.replace("mn", "min")));
    } else {
      String linearFactor = getString(remediation, "linearFactor");
      String linearOffset = getString(remediation, "linearOffset");
      rule.setDebtRemediationFunction(remediationBuilder.linearWithOffset(
        linearFactor.replace("mn", "min"),
        linearOffset.replace("mn", "min")));
    }
    if (remediation.get("linearDesc") != null) {
      rule.setGapDescription(getString(remediation, "linearDesc"));
    }
  }

  private static String getUpperCaseString(Map<String, Object> map, String propertyName) {
    return getString(map, propertyName).toUpperCase(Locale.ROOT);
  }

  private static String getString(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (!(propertyValue instanceof String)) {
      throw new IllegalStateException("Invalid property '" + propertyName + "'");
    }
    return (String) propertyValue;
  }

  static String[] getStringArray(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (!(propertyValue instanceof List)) {
      throw new IllegalStateException("Invalid property: " + propertyName);
    }
    return ((List<String>) propertyValue).toArray(new String[0]);
  }

  private static int[] getIntArray(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (!(propertyValue instanceof List)) {
      throw new IllegalStateException("Invalid property: " + propertyName);
    }
    return ((List<Number>) propertyValue).stream().mapToInt(Number::intValue).toArray();
  }

  boolean securityHotspotsSupported() {
    return sonarRuntime.getApiVersion().isGreaterThanOrEqual(SQ_7_3);
  }

  boolean isSecurityHotspot(Map<String, Object> ruleMetadata) {
    String type = getUpperCaseString(ruleMetadata, "type");
    return SECURITY_HOTSPOT.equals(type);
  }

}
