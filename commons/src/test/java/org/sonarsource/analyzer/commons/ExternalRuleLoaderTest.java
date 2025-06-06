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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.internal.DefaultInputProject;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.batch.sensor.issue.internal.DefaultExternalIssue;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonar.api.utils.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonar.api.batch.rule.Severity.BLOCKER;
import static org.sonar.api.batch.rule.Severity.INFO;
import static org.sonar.api.batch.rule.Severity.MAJOR;
import static org.sonar.api.batch.rule.Severity.MINOR;
import static org.sonar.api.issue.impact.Severity.HIGH;
import static org.sonar.api.issue.impact.Severity.LOW;
import static org.sonar.api.issue.impact.Severity.MEDIUM;
import static org.sonar.api.issue.impact.SoftwareQuality.MAINTAINABILITY;
import static org.sonar.api.issue.impact.SoftwareQuality.RELIABILITY;
import static org.sonar.api.issue.impact.SoftwareQuality.SECURITY;
import static org.sonar.api.rules.CleanCodeAttribute.IDENTIFIABLE;
import static org.sonar.api.rules.CleanCodeAttribute.RESPECTFUL;
import static org.sonar.api.rules.RuleType.BUG;
import static org.sonar.api.rules.RuleType.CODE_SMELL;
import static org.sonar.api.rules.RuleType.VULNERABILITY;

public class ExternalRuleLoaderTest {

  private static final SonarRuntime RUNTIME_10_0 = SonarRuntimeImpl.forSonarQube(Version.create(10, 0), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
  private static final SonarRuntime RUNTIME_10_1 = SonarRuntimeImpl.forSonarQube(Version.create(10, 1), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);

  @Test
  public void test_null_runtime() {
    ExternalRuleLoader loader = loadMyLinterJson(null);
    assertThat(loader.isCleanCodeImpactsAndAttributesSupported()).isFalse();
    assertRule(loader, "bug-rule", BUG, MAJOR, 42L);
    assertRule(loader, "identifiable-low-maintainability-rule", BUG, MINOR, 5L);
  }

  @Test
  public void test_repository_10_0() {
    ExternalRuleLoader externalRuleLoader = loadMyLinterJson(RUNTIME_10_0);
    assertThat(externalRuleLoader.isCleanCodeImpactsAndAttributesSupported()).isFalse();
    assertRule(externalRuleLoader, "not-existing-key", CODE_SMELL, MAJOR, 5L);
    assertRule(externalRuleLoader, "bug-rule", BUG, MAJOR, 42L);
    assertRule(externalRuleLoader, "code-smell-rule", CODE_SMELL, MAJOR, 5L);
    assertRule(externalRuleLoader, "vulnerability-rule", VULNERABILITY, INFO, 5L);
    assertRule(externalRuleLoader, "identifiable-low-maintainability-rule", BUG, MINOR, 5L);
    assertRule(externalRuleLoader, "no-type-rule", CODE_SMELL, BLOCKER, 5L);

    RulesDefinition.Context context = new RulesDefinition.Context();
    externalRuleLoader.createExternalRuleRepository(context);

    assertThat(context.repositories()).hasSize(1);

    Repository repository = context.repository("external_my-linter-key");
    assertThat(repository.isExternal()).isTrue();
    assertThat(repository.name()).isEqualTo("MyLinter");
    assertThat(repository.language()).isEqualTo("mylang");
    assertThat(repository.rules()).hasSize(8);

    assertRule(repository,
      "bug-rule",
      "Bug Rule Name",
      "Bug Rule Description",
      BUG,
      "MAJOR",
      "42min",
      null,
      Map.of(RELIABILITY, MEDIUM),
      Set.of()
    );

    assertRule(repository,
      "code-smell-rule",
      "Code Smell Name",
      "See description of MyLinter rule <code>code-smell-rule</code> at the <a href=\"http://www.mylinter.org/code-smell-rule\">MyLinter website</a>.",
      CODE_SMELL,
      "MAJOR",
      "5min",
      null,
      Map.of(MAINTAINABILITY, MEDIUM),
      Set.of("tag1", "tag2")
    );

    assertRule(repository,
      "vulnerability-rule",
      "Vulnerability Name",
      "<p>Bug Rule Description</p> <p>See more at the <a href=\"http://www.mylinter.org/code-smell-rule\">MyLinter website</a>.</p>",
      VULNERABILITY,
      "INFO",
      "5min",
      null,
      Map.of(SECURITY, org.sonar.api.issue.impact.Severity.INFO),
      Set.of()
    );

    assertRule(repository,
      "no-type-rule",
      "No Type Name",
      "This is external rule <code>my-linter-key:no-type-rule</code>. No details are available.",
      CODE_SMELL,
      "BLOCKER",
      "5min",
      null,
      Map.of(MAINTAINABILITY, org.sonar.api.issue.impact.Severity.BLOCKER),
      Set.of()
    );

    assertRule(repository,
      "identifiable-low-maintainability-rule",
      "Identifiable Low Maintainability Name",
      "<p>Identifiable Rule Description</p> <p>See more at the <a href=\"http://www.mylinter.org/identifiable-rule\">MyLinter website</a>.</p>",
      BUG,
      "MINOR",
      "5min",
      null,
      Map.of(RELIABILITY, LOW),
      Set.of()
    );

    assertRule(repository,
      "respectful-reliability-and-security",
      "Respectful Reliability and Security",
      "Respectful Reliability and Security Description",
      VULNERABILITY,
      "MAJOR",
      "5min",
      null,
      Map.of(SECURITY, MEDIUM),
      Set.of()
    );

  }

  @Test
  public void test_repository_10_1() {
    ExternalRuleLoader externalRuleLoader = loadMyLinterJson(RUNTIME_10_1);
    assertThat(externalRuleLoader.isCleanCodeImpactsAndAttributesSupported()).isTrue();
    assertRule(externalRuleLoader, "bug-rule", BUG, MAJOR, 42L);
    assertRule(externalRuleLoader, "identifiable-low-maintainability-rule", BUG, MINOR, 5L);

    RulesDefinition.Context context = new RulesDefinition.Context();
    externalRuleLoader.createExternalRuleRepository(context);

    assertThat(context.repositories()).hasSize(1);

    Repository repository = context.repository("external_my-linter-key");
    assertThat(repository.rules()).hasSize(8);

    assertRule(repository,
      "bug-rule",
      "Bug Rule Name",
      "Bug Rule Description",
      BUG,
      "MAJOR",
      "42min",
      null,
      Map.of(RELIABILITY, MEDIUM),
      Set.of()
    );

    assertRule(repository,
      "identifiable-low-maintainability-rule",
      "Identifiable Low Maintainability Name",
      "<p>Identifiable Rule Description</p> <p>See more at the <a href=\"http://www.mylinter.org/identifiable-rule\">MyLinter website</a>.</p>",
      BUG,
      "MINOR",
      "5min",
      IDENTIFIABLE,
      Map.of(MAINTAINABILITY, LOW),
      Set.of()
    );

    assertRule(repository,
      "respectful-reliability-and-security",
      "Respectful Reliability and Security",
      "Respectful Reliability and Security Description",
      VULNERABILITY,
      "MAJOR",
      "5min",
      RESPECTFUL,
      Map.of(RELIABILITY, MEDIUM, SECURITY, HIGH),
      Set.of()
    );

    assertRule(repository,
      "missing-code-attribute",
      "Missing Code Attribute",
      "This is external rule <code>my-linter-key:missing-code-attribute</code>. No details are available.",
      BUG,
      "MINOR",
      "5min",
      null,
      Map.of(RELIABILITY, LOW),
      Set.of()
    );

    assertRule(repository,
      "missing-impacts",
      "Missing impacts",
      "Missing impacts Description",
      BUG,
      "MINOR",
      "5min",
      null,
      Map.of(RELIABILITY, LOW),
      Set.of()
    );
  }

  private static void assertRule(ExternalRuleLoader externalRuleLoader, String ruleKey,
                                 RuleType expectedRuleType, Severity expectedRuleSeverity, Long expectedDebtMinutes) {
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(externalRuleLoader.ruleType(ruleKey)).as("ruleType of " + ruleKey).isEqualTo(expectedRuleType);
    softly.assertThat(externalRuleLoader.ruleSeverity(ruleKey)).as("ruleSeverity of " + ruleKey).isEqualTo(expectedRuleSeverity);
    softly.assertThat(externalRuleLoader.ruleConstantDebtMinutes(ruleKey))
      .as("ruleConstantDebtMinutes of " + ruleKey).isEqualTo(expectedDebtMinutes);
    softly.assertAll();
  }

  private static void assertRule(Repository repository, String ruleKey, String expectedName, String expectedDescription,
                                 RuleType expectedRuleType, String expectedRuleSeverity, String expectedDebtMinutes,
                                 @Nullable CleanCodeAttribute expectedCodeAttribute,
                                 @Nullable Map<SoftwareQuality, org.sonar.api.issue.impact.Severity> expectedCodeImpacts,
                                 Set<String> expectedTags) {
    SoftAssertions softly = new SoftAssertions();
    Rule rule = repository.rule(ruleKey);
    assertThat(rule.name()).isEqualTo(expectedName);
    assertThat(rule.htmlDescription()).isEqualTo(expectedDescription);
    softly.assertThat(rule.type()).as("type of " + ruleKey).isEqualTo(expectedRuleType);
    softly.assertThat(rule.severity()).as("severity of " + ruleKey).isEqualTo(expectedRuleSeverity);
    softly.assertThat(rule.cleanCodeAttribute()).as("cleanCodeAttribute of " + ruleKey).isEqualTo(expectedCodeAttribute);
    softly.assertThat(rule.defaultImpacts()).as("defaultImpacts of " + ruleKey).isEqualTo(expectedCodeImpacts);
    softly.assertThat(rule.debtRemediationFunction().baseEffort())
      .as("debtRemediationFunction of " + ruleKey).isEqualTo(expectedDebtMinutes);
    softly.assertThat(rule.tags()).as("tags of " + ruleKey).isEqualTo(expectedTags);
    softly.assertAll();
  }


  private static ExternalRuleLoader loadMyLinterJson(@Nullable SonarRuntime sonarRuntime) {
    String linterKey = "my-linter-key";
    String linterName = "MyLinter";
    String pathToMetadata = "org/sonarsource/analyzer/commons/mylinter.json";
    String languageKey = "mylang";
    ExternalRuleLoader loader;
    if (sonarRuntime != null) {
      loader = new ExternalRuleLoader(linterKey, linterName, pathToMetadata, languageKey, sonarRuntime);
    } else {
      loader = new ExternalRuleLoader(linterKey, linterName, pathToMetadata, languageKey);
    }
    assertThat(loader.ruleKeys()).containsOnly(
      "bug-rule",
      "code-smell-rule",
      "vulnerability-rule",
      "identifiable-low-maintainability-rule",
      "respectful-reliability-and-security",
      "missing-code-attribute",
      "missing-impacts",
      "no-type-rule"
    );
    return loader;
  }

}
