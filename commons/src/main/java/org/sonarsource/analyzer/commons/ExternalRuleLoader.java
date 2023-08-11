/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2023 SonarSource SA
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.utils.Version;

/**
 * Creates external rule repository based on json file in the format <code>[ { "key": "...", "name": "..." }, ... ]</code>
 * <p>
 * "key" and "name" are required properties. Also could be provided:
 * <ul>
 * <li>url (link to rule page)</li>
 * <li>description (html description of rule)</li>
 * <li>constantDebtMinutes (e.g. 30, by default 5)</li>
 * <li>tags (array of strings, e.g. <code>"tags": [ "foo", "bar" ]</code>)</li>
 * <li>severity (as in SQ API, e.g. "MAJOR")</li>
 * <li>type (as in SQ API, e.g. "BUG")</li>
 * </ul>
 */
public class ExternalRuleLoader {

  private static final Long DEFAULT_CONSTANT_DEBT_MINUTES = 5L;
  private static final RuleType DEFAULT_ISSUE_TYPE = RuleType.CODE_SMELL;
  private static final Severity DEFAULT_SEVERITY = Severity.MAJOR;
  private static final String DESCRIPTION_ONLY_URL = "See description of %s rule <code>%s</code> at the <a href=\"%s\">%s website</a>.";
  private static final String DESCRIPTION_WITH_URL = "<p>%s</p> <p>See more at the <a href=\"%s\">%s website</a>.</p>";
  private static final String DESCRIPTION_FALLBACK = "This is external rule <code>%s:%s</code>. No details are available.";
  public static final Version API_VERSION_SUPPORTING_CLEAN_CODE_IMPACTS_AND_ATTRIBUTES = Version.create(10, 1);

  private final String linterKey;
  private final String linterName;
  private final String languageKey;
  private final boolean isCleanCodeImpactsAndAttributesSupported;

  private Map<String, ExternalRule> rulesMap = new HashMap<>();

  /**
   * @deprecated Use the constructor that also provide the SonarRuntime argument to determine if you can use
   * the new Clean Code attributes and impacts API.
   * Then you should test "isCleanCodeImpactsAndAttributesSupported()" before using codeAttribute and codeImpacts.
   */
  @Deprecated(since = "2.6")
  public ExternalRuleLoader(String linterKey, String linterName, String pathToMetadata, String languageKey) {
    this(linterKey, linterName, pathToMetadata, languageKey, null);
  }

  public ExternalRuleLoader(String linterKey, String linterName, String pathToMetadata, String languageKey, @Nullable SonarRuntime sonarRuntime) {
    this.linterKey = linterKey;
    this.linterName = linterName;
    this.languageKey = languageKey;

    isCleanCodeImpactsAndAttributesSupported = sonarRuntime != null &&
      sonarRuntime.getApiVersion().isGreaterThanOrEqual(API_VERSION_SUPPORTING_CLEAN_CODE_IMPACTS_AND_ATTRIBUTES);

    loadMetadataFile(pathToMetadata);
  }

  public boolean isCleanCodeImpactsAndAttributesSupported() {
    return isCleanCodeImpactsAndAttributesSupported;
  }

  public void createExternalRuleRepository(org.sonar.api.server.rule.RulesDefinition.Context context) {
    NewRepository externalRepo = context.createExternalRepository(linterKey, languageKey).setName(linterName);

    for (ExternalRule rule : rulesMap.values()) {
      NewRule newRule = externalRepo.createRule(rule.key).setName(rule.name);
      newRule.setHtmlDescription(rule.getDescription(linterKey, linterName));
      newRule.setDebtRemediationFunction(newRule.debtRemediationFunctions().constantPerIssue(rule.constantDebtMinutes + "min"));
      newRule.setType(rule.type);
      newRule.setSeverity(rule.severity.name());
      rule.applyCodeAttributeAndImpact(newRule);

      if (rule.tags != null) {
        newRule.setTags(rule.tags);
      }
    }

    externalRepo.done();
  }

  public Set<String> ruleKeys() {
    return rulesMap.keySet();
  }

  /**
   * Deprecated, use {@link #applyTypeAndCleanCodeAttributes(NewExternalIssue, String)} instead.
   */
  @Deprecated(since = "2.6")
  public RuleType ruleType(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      return externalRule.type;
    } else {
      return DEFAULT_ISSUE_TYPE;
    }
  }

  /**
   * Deprecated, use {@link #applyTypeAndCleanCodeAttributes(NewExternalIssue, String)} instead.
   */
  @Deprecated(since = "2.6")
  public Severity ruleSeverity(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      return externalRule.severity;
    } else {
      return DEFAULT_SEVERITY;
    }
  }

  public NewExternalIssue applyTypeAndCleanCodeAttributes(NewExternalIssue newExternalIssue, String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      newExternalIssue
        .type(externalRule.type)
        .severity(externalRule.severity);
      externalRule.applyCodeAttributeAndImpact(newExternalIssue);
    } else {
      newExternalIssue
        .type(DEFAULT_ISSUE_TYPE)
        .severity(DEFAULT_SEVERITY);
    }
    return newExternalIssue;
  }

  public Long ruleConstantDebtMinutes(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      return externalRule.constantDebtMinutes;
    } else {
      return DEFAULT_CONSTANT_DEBT_MINUTES;
    }
  }

  private void loadMetadataFile(String pathToMetadata) {
    InputStream inputStream = ExternalRuleLoader.class.getClassLoader().getResourceAsStream(pathToMetadata);
    try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

      List<Map<String, Object>> rules = new JsonParser().parseArray(inputStreamReader);
      for (Map<String, Object> rule : rules) {
        ExternalRule externalRule = isCleanCodeImpactsAndAttributesSupported ?
          new ExternalRuleWithCodeAttribute(rule) : new ExternalRule(rule);
        rulesMap.put(externalRule.key, externalRule);
      }

    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + pathToMetadata, e);
    }
  }

  private static class ExternalRule {
    final String key;
    final String name;
    final RuleType type;
    final Severity severity;

    @CheckForNull
    final String url;

    @CheckForNull
    final String description;

    @CheckForNull
    final String[] tags;

    final Long constantDebtMinutes;

    public ExternalRule(Map<String, Object> rule) {
      this.key = (String) rule.get("key");
      this.name = (String) rule.get("name");
      this.url = (String) rule.get("url");
      this.description = (String) rule.get("description");
      this.constantDebtMinutes = (Long) rule.getOrDefault("constantDebtMinutes", DEFAULT_CONSTANT_DEBT_MINUTES);
      JSONArray tagsAsList = (JSONArray) rule.get("tags");
      if (tagsAsList != null) {
        this.tags = (String[]) tagsAsList.toArray(new String[tagsAsList.size()]);
      } else {
        this.tags = null;
      }
      type = getType(rule);
      severity = getSeverity(rule);
    }

    public void applyCodeAttributeAndImpact(NewRule newRule) {
      // only supported by ExternalRuleWithCodeAttribute
    }

    public void applyCodeAttributeAndImpact(NewExternalIssue newExternalIssue) {
      // only supported by ExternalRuleWithCodeAttribute
    }

    private static RuleType getType(Map<String, Object> rule) {
      String strType = (String) rule.get("type");
      if (strType != null) {
        return RuleType.valueOf(strType);
      } else {
        return DEFAULT_ISSUE_TYPE;
      }
    }

    private static Severity getSeverity(Map<String, Object> rule) {
      String strSeverity = (String) rule.get("severity");
      if (strSeverity != null) {
        return Severity.valueOf(strSeverity);
      } else {
        return DEFAULT_SEVERITY;
      }
    }

    String getDescription(String linterKey, String linterName) {
      if (description != null && url != null) {
        return String.format(DESCRIPTION_WITH_URL, description, url, linterName);
      }

      if (description != null) {
        return description;
      }

      if (url != null) {
        return String.format(DESCRIPTION_ONLY_URL, linterName, key, url, linterName);
      }

      return String.format(DESCRIPTION_FALLBACK, linterKey, key);
    }
  }

  private static class ExternalRuleWithCodeAttribute extends ExternalRule {

    @CheckForNull
    final CleanCodeAttribute codeAttribute;

    @CheckForNull
    final Map<SoftwareQuality, org.sonar.api.issue.impact.Severity> codeImpacts;

    public ExternalRuleWithCodeAttribute(Map<String, Object> rule) {
      super(rule);
      codeAttribute = getCodeAttribute(rule);
      codeImpacts = getCodeImpacts(rule);
    }

    @Override
    public void applyCodeAttributeAndImpact(NewRule newRule) {
      if (codeAttribute != null && codeImpacts != null) {
        newRule.setCleanCodeAttribute(codeAttribute);
        codeImpacts.forEach(newRule::addDefaultImpact);
      }
    }

    @Override
    public void applyCodeAttributeAndImpact(NewExternalIssue newExternalIssue) {
      if (codeAttribute != null && codeImpacts != null) {
        newExternalIssue.cleanCodeAttribute(codeAttribute);
        codeImpacts.forEach(newExternalIssue::addImpact);
      }
    }

    @Nullable
    private static CleanCodeAttribute getCodeAttribute(Map<String, Object> rule) {
      JSONObject code = (JSONObject) rule.get("code");
      if (code != null) {
        String attribute = (String) code.get("attribute");
        if (attribute != null) {
          return CleanCodeAttribute.valueOf(attribute);
        }
      }
      return null;
    }

    @Nullable
    private static Map<SoftwareQuality, org.sonar.api.issue.impact.Severity> getCodeImpacts(Map<String, Object> rule) {
      JSONObject code = (JSONObject) rule.get("code");
      if (code != null) {
        JSONObject impacts = (JSONObject) code.get("impacts");
        if (impacts != null) {
          Map<SoftwareQuality, org.sonar.api.issue.impact.Severity> map = new LinkedHashMap<>();
          impacts.forEach(
            (k, v) -> map.put(SoftwareQuality.valueOf((String) k),
              org.sonar.api.issue.impact.Severity.valueOf((String) v)));
          return map;
        }
      }
      return null;
    }

  }

}
