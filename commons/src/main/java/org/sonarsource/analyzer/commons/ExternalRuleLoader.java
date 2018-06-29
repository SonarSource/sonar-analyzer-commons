/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;

@ScannerSide
public class ExternalRuleLoader {

  private static final int DEFAULT_REMEDIATION_COST = 5;
  private static final RuleType DEFAULT_ISSUE_TYPE = RuleType.CODE_SMELL;
  private static final String DESCRIPTION_ONLY_URL = "See the description of %s rule <code>%s</code> at <a href=\"%s\">%s website</a>.";
  private static final String DESCRIPTION_WITH_URL = "%s. See more at <a href=\"%s\">%s website</a>.";
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

  /**
   * IMPORTANT This method should not be used when SonarQube runtime version is less than 7.2
   * because it would trigger calls to {@link org.sonar.api.server.rule.RulesDefinition.Context#createExternalRepository}
   * which was added in SonarQube 7.2.
   */
  public void createExternalRuleRepository(org.sonar.api.server.rule.RulesDefinition.Context context) {
    NewRepository externalRepo = context.createExternalRepository(linterKey, languageKey).setName(linterName);

    for (ExternalRule rule : rulesMap.values()) {
      NewRule newRule = externalRepo.createRule(rule.key).setName(rule.name);
      newRule.setHtmlDescription(rule.getDescription(linterKey, linterName));
      newRule.setDebtRemediationFunction(newRule.debtRemediationFunctions().constantPerIssue(DEFAULT_REMEDIATION_COST + "min"));
      newRule.setType(rule.getRuleType());

      if (rule.tags != null) {
        newRule.setTags(rule.tags);
      }

      if (rule.severity != null) {
        newRule.setSeverity(rule.severity);
      }
    }

    externalRepo.done();
  }

  public RuleType ruleType(String ruleKey) {
    ExternalRule externalRule = rulesMap.get(ruleKey);
    if (externalRule != null) {
      return externalRule.getRuleType();
    } else {
      return DEFAULT_ISSUE_TYPE;
    }
  }

  private void loadMetadataFile(String pathToMetadata) {
    InputStream inputStream = ExternalRuleLoader.class.getClassLoader().getResourceAsStream(pathToMetadata);
    try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

      ExternalRule[] rules = new Gson().fromJson(inputStreamReader, ExternalRule[].class);
      for (ExternalRule rule : rules) {
        rulesMap.put(rule.key, rule);
      }

    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + pathToMetadata, e);
    }

  }

  private static class ExternalRule {
    String key;
    String name;

    @CheckForNull
    String url;

    @CheckForNull
    String description;

    @CheckForNull
    String[] tags;

    @CheckForNull
    String severity;

    @CheckForNull
    String type;

    RuleType sonarType;

    RuleType getRuleType() {
      if (sonarType == null) {
        if (type != null) {
          sonarType = RuleType.valueOf(type);
        } else {
          sonarType = DEFAULT_ISSUE_TYPE;
        }
      }
      return sonarType;
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
