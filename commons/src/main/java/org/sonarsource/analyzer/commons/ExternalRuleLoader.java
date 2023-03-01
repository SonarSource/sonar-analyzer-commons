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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.json.simple.JSONArray;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;

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

  private final String linterKey;
  private final String linterName;
  private final String languageKey;

  private Map<String, ExternalRule> rulesMap = new HashMap<>();

  public ExternalRuleLoader(String linterKey, String linterName, String pathToMetadata, String languageKey) {
    this.linterKey = linterKey;
    this.linterName = linterName;
    this.languageKey = languageKey;

    loadMetadataFile(pathToMetadata);
  }

  public void createExternalRuleRepository(org.sonar.api.server.rule.RulesDefinition.Context context) {
    NewRepository externalRepo = context.createExternalRepository(linterKey, languageKey).setName(linterName);

    for (ExternalRule rule : rulesMap.values()) {
      NewRule newRule = externalRepo.createRule(rule.key).setName(rule.name);
      newRule.setHtmlDescription(rule.getDescription(linterKey, linterName));
      newRule.setDebtRemediationFunction(newRule.debtRemediationFunctions().constantPerIssue(rule.constantDebtMinutes + "min"));
      newRule.setType(rule.type);

      if (rule.tags != null) {
        newRule.setTags(rule.tags);
      }

      if (rule.severity != null) {
        newRule.setSeverity(rule.severity);
      }
    }

    externalRepo.done();
  }

  public Set<String> ruleKeys() {
    return rulesMap.keySet();
  }

  public RuleType ruleType(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      return externalRule.type;
    } else {
      return DEFAULT_ISSUE_TYPE;
    }
  }

  public Severity ruleSeverity(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null && externalRule.severity != null) {
      return Severity.valueOf(externalRule.severity);
    } else {
      return DEFAULT_SEVERITY;
    }
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
        ExternalRule externalRule = new ExternalRule(rule);
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

    @CheckForNull
    final String url;

    @CheckForNull
    final String description;

    @CheckForNull
    final String[] tags;

    @CheckForNull
    final String severity;

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
      this.severity = (String) rule.get("severity");
      String inputType = (String) rule.get("type");
      if (inputType != null) {
        type = RuleType.valueOf(inputType);
      } else {
        type = DEFAULT_ISSUE_TYPE;
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

}
