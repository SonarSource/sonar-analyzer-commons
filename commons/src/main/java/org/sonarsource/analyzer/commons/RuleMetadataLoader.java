/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.sonar.api.SonarRuntime;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleScope;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.DebtRemediationFunctions;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.server.rule.RulesDefinition.OwaspAsvsVersion;
import org.sonar.api.server.rule.RulesDefinition.OwaspLlmTop10Version;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10Version;
import org.sonar.api.server.rule.RulesDefinition.OwaspMobileTop10Version;
import org.sonar.api.server.rule.RulesDefinition.PciDssVersion;
import org.sonar.api.server.rule.RulesDefinition.StigVersion;
import org.sonar.api.server.rule.RulesDefinitionAnnotationLoader;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.Version;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKey;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKeys;
import org.sonarsource.analyzer.commons.domain.RuleManifest;

import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Not designed for multi-threads
 */
public class RuleMetadataLoader {

  private static final String INVALID_PROPERTY_MESSAGE = "Invalid property: %s";
  private static final char RESOURCE_SEP = '/';
  private final String resourceFolder;
  private final Set<String> activatedByDefault;
  private final JsonParser jsonParser;
  private final SonarRuntime sonarRuntime;
  private final EducationRuleLoader educationRuleLoader;

  private static final String OWASP_MOBILE_2024 = "OWASP Mobile Top 10 2024";
  private static final String OWASP_2025 = "OWASP Top 10 2025";
  private static final String OWASP_2021 = "OWASP Top 10 2021";
  private static final String OWASP_2017 = "OWASP";
  private static final String PCI_DSS_PREFIX = "PCI DSS ";
  private static final String ASVS_PREFIX = "ASVS ";
  private static final String STIG_PREFIX = "STIG ";
  private static final String IMPACTS = "impacts";
  private static final String LINEAR_FACTOR = "linearFactor";
  private static final String LINEAR_DESCRIPTION = "linearDesc";

  public RuleMetadataLoader(SonarRuntime sonarRuntime) {
    this(null, Collections.emptySet(), sonarRuntime);
  }

  public RuleMetadataLoader(String resourceFolder, SonarRuntime sonarRuntime) {
    this(resourceFolder, Collections.emptySet(), sonarRuntime);
  }

  public RuleMetadataLoader(String resourceFolder, String defaultProfilePath, SonarRuntime sonarRuntime) {
    this(resourceFolder, BuiltInQualityProfileJsonLoader.loadActiveKeysFromJsonProfile(defaultProfilePath), sonarRuntime);
  }

  private RuleMetadataLoader(@Nullable String resourceFolder, Set<String> activatedByDefault, SonarRuntime sonarRuntime) {
    this.resourceFolder = resourceFolder;
    this.jsonParser = new JsonParser();
    this.activatedByDefault = activatedByDefault;
    this.sonarRuntime = sonarRuntime;
    this.educationRuleLoader = new EducationRuleLoader(sonarRuntime);
  }

  public void addRulesByAnnotatedClass(NewRepository repository, List<Class<?>> ruleClasses) {
    for (Class<?> ruleClass : ruleClasses) {
      addRuleByAnnotatedClass(repository, ruleClass);
    }
  }

  public void addRulesByRuleKey(NewRepository repository, List<String> ruleKeys) {
    for (String ruleKey : ruleKeys) {
      addRuleByRuleKey(repository, ruleKey);
    }
  }

  private void addRuleByAnnotatedClass(NewRepository repository, Class<?> ruleClass) {
    NewRule rule = addAnnotatedRule(repository, ruleClass);
    setDescriptionFromHtmlFile(rule);
    setMetadataFromJsonFile(rule);
    setDefaultActivation(rule);
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
    if (ruleKey.isEmpty()) {
      throw new IllegalStateException("Empty key");
    }
    new RulesDefinitionAnnotationLoader().load(repository, ruleClass);
    NewRule rule = repository.rule(ruleKey);
    if (rule == null) {
      throw new IllegalStateException("Rule not found: " + ruleKey);
    }

    DeprecatedRuleKeys deprecatedRuleKeys = AnnotationUtils.getAnnotation(ruleClass, DeprecatedRuleKeys.class);
    if (deprecatedRuleKeys != null) {
      Arrays.stream(deprecatedRuleKeys.value()).forEach(deprecatedRuleKey -> addDeprecatedRuleKey(repository, rule, deprecatedRuleKey));
    } else {
      DeprecatedRuleKey deprecatedRuleKey = AnnotationUtils.getAnnotation(ruleClass, DeprecatedRuleKey.class);
      if (deprecatedRuleKey != null) {
        addDeprecatedRuleKey(repository, rule, deprecatedRuleKey);
      }
    }

    return rule;
  }

  private static void addDeprecatedRuleKey(NewRepository repository, NewRule rule, DeprecatedRuleKey deprecatedRuleKey) {
    String repoKey = deprecatedRuleKey.repositoryKey().isEmpty() ? repository.key() : deprecatedRuleKey.repositoryKey();
    rule.addDeprecatedRuleKey(repoKey, deprecatedRuleKey.ruleKey());
  }

  private void addRuleByRuleKey(NewRepository repository, String ruleKey) {
    if (ruleKey.isEmpty()) {
      throw new IllegalStateException("Empty key");
    }
    NewRule rule = repository.createRule(ruleKey);
    setDescriptionFromHtmlFile(rule);
    setMetadataFromJsonFile(rule);
    setDefaultActivation(rule);
  }

  private void setDescriptionFromHtml(NewRule rule, String description) {
    description = educationRuleLoader.setEducationDescriptionFromHtml(rule, description);
    rule.setHtmlDescription(description);
  }

  private void setDescriptionFromHtmlFile(NewRule rule) {
    String htmlPath = resourceFolder + RESOURCE_SEP + rule.key() + ".html";
    String description;
    try {
      description = Resources.toString(htmlPath, UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + htmlPath, e);
    }
    this.setDescriptionFromHtml(rule, description);
  }

  private void setMetadataFromJson(NewRule rule, Map<String, Object> ruleMetadata) {
    rule.setName(getString(ruleMetadata, "title"));
    rule.setSeverity(getUpperCaseString(ruleMetadata, "defaultSeverity"));
    String type = getUpperCaseString(ruleMetadata, "type");
    rule.setType(RuleType.valueOf(type));

    if (isSupported(10, 1)) {
      Object code = ruleMetadata.get("code");
      if (code != null) {
        setCodeAttributeFromJson(rule, (Map<String, Object>) code);
      }
    }
    rule.setStatus(RuleStatus.valueOf(getUpperCaseString(ruleMetadata, "status")));
    rule.setTags(getStringArray(ruleMetadata, "tags"));
    getScopeIfPresent(ruleMetadata, "scope").ifPresent(rule::setScope);

    Object remediation = ruleMetadata.get("remediation");
    if (remediation != null) {
      setRemediationFromJson(rule, (Map<String, Object>) remediation);
    }

    Object securityStandards = ruleMetadata.get("securityStandards");
    if (securityStandards != null) {
      setSecurityStandardsFromJson(rule, (Map<String, Object>) securityStandards);
    }

    educationRuleLoader.setEducationMetadataFromJson(rule, ruleMetadata);
  }

  private void setMetadataFromJsonFile(NewRule rule) {
    Map<String, Object> ruleMetadata = getMetadataFromFile(rule.key());

    this.setMetadataFromJson(rule, ruleMetadata);
  }

  Map<String, Object> getMetadataFromFile(String ruleKey) {
    String jsonPath = resourceFolder + RESOURCE_SEP + ruleKey + ".json";
    try {
      return jsonParser.parse(Resources.toString(jsonPath, UTF_8));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Can't read resource: " + jsonPath, e);
    }
  }

  private void setCodeAttributeFromJson(NewRule rule, Map<String, Object> code) {
    String attribute = getString(code, "attribute");
    rule.setCleanCodeAttribute(CleanCodeAttribute.valueOf(attribute));

    Map<String, String> impacts = (Map<String, String>) code.get(IMPACTS);
    if (impacts == null || impacts.isEmpty()) {
      throw new IllegalStateException(String.format(INVALID_PROPERTY_MESSAGE, IMPACTS) + " for rule: " + rule.key());
    }
    impacts.forEach((softwareQuality, severity) ->
      rule.addDefaultImpact(SoftwareQuality.valueOf(softwareQuality), getCleanCodeTaxanomySeverity(severity)));
  }

  private Severity getCleanCodeTaxanomySeverity(String severity) {
    if (isSupported(10, 11)) {
      return Severity.valueOf(severity);
    }

    switch (severity) {
      case "BLOCKER":
        return Severity.BLOCKER;
      case "CRITICAL":
        return Severity.HIGH;
      case "MAJOR":
        return Severity.MEDIUM;
      case "MINOR":
        return Severity.LOW;
      case "INFO":
        return Severity.INFO;
      default:
        return Severity.MEDIUM;
    }
  }



  private void setSecurityStandardsFromJson(NewRule rule, Map<String, Object> securityStandards) {
    if (securityStandards.get("CWE") != null) {
      rule.addCwe(getIntArray(securityStandards, "CWE"));
    }

    addMasvs(rule, securityStandards);
    addOwasp(rule, securityStandards);
    addOwaspAsvs(rule, securityStandards);
    addOwaspLlm(rule, securityStandards);
    addOwaspMobile(rule, securityStandards);
    addPciDss(rule, securityStandards);
    addStig(rule, securityStandards);
  }

  private void addMasvs(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(13, 3)) {
      return;
    }
    for (RulesDefinition.MasvsVersion masvsVersion : RulesDefinition.MasvsVersion.values()) {
      rule.addMasvs(masvsVersion, getStringArray(securityStandards, masvsVersion.label()));
    }
  }

  private void addOwasp(NewRule rule, Map<String, Object> securityStandards) {
    boolean isOwaspByVersionSupported = isSupported(9, 3);
    RulesDefinition.OwaspTop10[] valuesFor2017 = getOwaspTop10Values(securityStandards, OWASP_2017);
    if (isOwaspByVersionSupported) {
      rule.addOwaspTop10(OwaspTop10Version.Y2017, valuesFor2017);
      RulesDefinition.OwaspTop10[] valuesFor2021 = getOwaspTop10Values(securityStandards, OWASP_2021);
      rule.addOwaspTop10(OwaspTop10Version.Y2021, valuesFor2021);
      if (isSupported(13, 3)) {
        RulesDefinition.OwaspTop10[] valuesFor2025 = getOwaspTop10Values(securityStandards, OWASP_2025);
        rule.addOwaspTop10(OwaspTop10Version.Y2025, valuesFor2025);
      }
    } else {
      rule.addOwaspTop10(valuesFor2017);
    }
  }

  private RulesDefinition.OwaspTop10[] getOwaspTop10Values(Map<String, Object> securityStandards, String owaspVersionLabel) {
    return Arrays.stream(getStringArray(securityStandards, owaspVersionLabel))
      .map(RulesDefinition.OwaspTop10::valueOf)
      .toArray(RulesDefinition.OwaspTop10[]::new);
  }

  private void addOwaspMobile(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(11, 4)) {
      return;
    }
    for (String standard : getStringArray(securityStandards, OWASP_MOBILE_2024)) {
      rule.addOwaspMobileTop10(OwaspMobileTop10Version.Y2024, RulesDefinition.OwaspMobileTop10.valueOf(standard));
    }
  }

  private void addOwaspLlm(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(13, 3)) {
      return;
    }
    for (OwaspLlmTop10Version owaspLlmTop10Version : OwaspLlmTop10Version.values()) {
      RulesDefinition.OwaspLlmTop10[] llmTop10Values = Arrays.stream(getStringArray(securityStandards, owaspLlmTop10Version.label()))
        .map(RulesDefinition.OwaspLlmTop10::valueOf)
        .toArray(RulesDefinition.OwaspLlmTop10[]::new);
      rule.addOwaspLlmTop10(owaspLlmTop10Version, llmTop10Values);
    }
  }

  private void addPciDss(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(9, 5)) {
      return;
    }

    for (PciDssVersion pciDssVersion : PciDssVersion.values()) {
      String pciDssKey = PCI_DSS_PREFIX + pciDssVersion.label();
      rule.addPciDss(pciDssVersion, getStringArray(securityStandards, pciDssKey));
    }
  }

  private void addOwaspAsvs(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(9, 9)) {
      return;
    }

    for (OwaspAsvsVersion asvsVersion : OwaspAsvsVersion.values()) {
      String asvsKey = ASVS_PREFIX + asvsVersion.label();
      rule.addOwaspAsvs(asvsVersion, getStringArray(securityStandards, asvsKey));
    }
  }

  private void addStig(NewRule rule, Map<String, Object> securityStandards) {
    if (!isSupported(10, 10)) {
      return;
    }

    for (StigVersion stigVersion : StigVersion.values()) {
      String stigKey = STIG_PREFIX + stigVersion.label();
      rule.addStig(stigVersion, getStringArray(securityStandards, stigKey));
    }
  }

  private boolean isSupported(int minMajor, int minMinor) {
    return sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(minMajor, minMinor));
  }

  private static void setRemediationFromJson(NewRule rule, Map<String, Object> remediation) {
    String func = getString(remediation, "func");
    DebtRemediationFunctions remediationBuilder = rule.debtRemediationFunctions();
    if (func.startsWith("Constant")) {
      String constantCost = getString(remediation, "constantCost");
      rule.setDebtRemediationFunction(remediationBuilder.constantPerIssue(constantCost.replace("mn", "min")));
    } else if ("Linear".equals(func)) {
      String linearFactor = getString(remediation, LINEAR_FACTOR);
      rule.setDebtRemediationFunction(remediationBuilder.linear(linearFactor.replace("mn", "min")));
    } else {
      String linearFactor = getString(remediation, LINEAR_FACTOR);
      String linearOffset = getString(remediation, "linearOffset");
      rule.setDebtRemediationFunction(remediationBuilder.linearWithOffset(
        linearFactor.replace("mn", "min"),
        linearOffset.replace("mn", "min")));
    }
    if (remediation.get(LINEAR_DESCRIPTION) != null) {
      rule.setGapDescription(getString(remediation, LINEAR_DESCRIPTION));
    }
  }

  private static String getUpperCaseString(Map<String, Object> map, String propertyName) {
    return getString(map, propertyName).toUpperCase(Locale.ROOT);
  }

  private static String getString(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (!(propertyValue instanceof String)) {
      throw new IllegalStateException(String.format(INVALID_PROPERTY_MESSAGE, propertyName));
    }
    return (String) propertyValue;
  }

  static String[] getStringArray(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (propertyValue == null) {
      return new String[0];
    }
    if (!(propertyValue instanceof List)) {
      throw new IllegalStateException(String.format(INVALID_PROPERTY_MESSAGE, propertyName));
    }
    return ((List<String>) propertyValue).toArray(new String[0]);
  }

  /*
   * Unlike the other metadata getter methods, It won't throw an exception if the value is missing but if the value is not a valid rule
   * scope. The method ignores case and the value "Tests" is mapped to RuleScope.TEST.
   */
  static Optional<RuleScope> getScopeIfPresent(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    return Optional.ofNullable(propertyValue)
      .filter(String.class::isInstance)
      .map(value -> ((String) value).toUpperCase(Locale.ROOT))
      .map(scope -> "TESTS".equals(scope) ? "TEST" : scope)
      .map(RuleScope::valueOf);
  }

  private static int[] getIntArray(Map<String, Object> map, String propertyName) {
    Object propertyValue = map.get(propertyName);
    if (!(propertyValue instanceof List)) {
      throw new IllegalStateException(String.format(INVALID_PROPERTY_MESSAGE, propertyName));
    }
    return ((List<Number>) propertyValue).stream().mapToInt(Number::intValue).toArray();
  }

  public NewRule createRuleFromRuleManifest(NewRepository repository, RuleManifest ruleManifest) {
    var newRule = repository.createRule(ruleManifest.name());

    Map<String, Object> map = new HashMap<>();
    Map<String, Object> remediationMap = new HashMap<>();

    var remediation = ruleManifest.remediation();

    if (remediation != null) {
      remediationMap.put("func", remediation.func());
      remediationMap.put("constantCost", remediation.constantCost());
      remediationMap.put(LINEAR_DESCRIPTION, remediation.linearDescription());
      remediationMap.put(LINEAR_FACTOR, remediation.linearFactor());
      remediationMap.put("linearOffset", remediation.linearOffset());

      map.put("remediation", remediationMap);
    }

    Map<String, Object> codeMap = new HashMap<>();

    var code = ruleManifest.code();

    if (code != null) {
      codeMap.put(IMPACTS, code.impacts());
      codeMap.put("attribute", code.attribute());

      map.put("code", codeMap);
    }

    map.put("defaultSeverity", ruleManifest.defaultSeverity());
    map.put("scope", ruleManifest.scope());
    map.put("status", ruleManifest.status());
    map.put("tags", ruleManifest.tags());
    map.put("title", ruleManifest.title());
    map.put("type", ruleManifest.type());

    this.setMetadataFromJson(newRule, map);
    this.setDescriptionFromHtml(newRule, ruleManifest.htmlDocumentation());

    for (var parameter: ruleManifest.parameters()) {
      newRule.createParam(parameter.name())
        .setName(parameter.name())
        .setDescription(parameter.description())
        .setDefaultValue(parameter.defaultValue())
        .setType(RuleParamType.parse(parameter.type()));
    }

    return newRule;
  }
}
