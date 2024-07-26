/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2024 SonarSource SA
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleScope;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.utils.Version;
import org.sonar.check.Rule;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKey;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.sonar.api.issue.impact.Severity.HIGH;
import static org.sonar.api.issue.impact.Severity.LOW;
import static org.sonar.api.issue.impact.SoftwareQuality.MAINTAINABILITY;
import static org.sonar.api.rules.CleanCodeAttribute.IDENTIFIABLE;

public class RuleMetadataLoaderTest {

  private static final String RESOURCE_FOLDER = "org/sonarsource/analyzer/commons";
  private static final String DEFAULT_PROFILE_PATH = "org/sonarsource/analyzer/commons/Sonar_way_profile.json";
  private static final String RULE_REPOSITORY_KEY = "rule-definition-reader-test";
  private RulesDefinition.Context context;
  private NewRepository newRepository;
  private RuleMetadataLoader ruleMetadataLoader;
  // using SonarLint for simplicity (it requires fewer parameters)
  private static final SonarRuntime SONAR_RUNTIME_9_2 = SonarRuntimeImpl.forSonarLint(Version.create(9, 2));
  private static final SonarRuntime SONAR_RUNTIME_9_3 = SonarRuntimeImpl.forSonarLint(Version.create(9, 3));
  private static final SonarRuntime SONAR_RUNTIME_9_5 = SonarRuntimeImpl.forSonarLint(Version.create(9, 5));
  private static final SonarRuntime SONAR_RUNTIME_9_9 = SonarRuntimeImpl.forSonarLint(Version.create(9, 9));
  private static final SonarRuntime SONAR_RUNTIME_10_1 = SonarRuntimeImpl.forSonarLint(Version.create(10, 1));
  private static final SonarRuntime SONAR_RUNTIME_10_10 = SonarRuntimeImpl.forSonarLint(Version.create(10, 10));

  @Before
  public void setup() {
    context = new RulesDefinition.Context();
    newRepository = context.createRepository(RULE_REPOSITORY_KEY, "magic");
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_3);
  }

  @Test
  public void load_taxonomy_rule_with_supported_product_runtime() {
    @Rule(key = "taxonomy_rule")
    class TestRule {
    }

    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_10_1);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("taxonomy_rule");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Taxonomy rule with code block properties");
    assertThat(rule.htmlDescription()).isEqualTo("<p>description taxonomy rule</p>");
    assertThat(rule.severity()).isEqualTo("MINOR");
    assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rule.cleanCodeAttribute()).isEqualTo(IDENTIFIABLE);
    assertThat(rule.defaultImpacts()).isEqualTo(Map.of(MAINTAINABILITY, HIGH));
    assertThat(rule.status()).isEqualTo(RuleStatus.READY);
    assertThat(rule.tags()).containsExactly("convention");
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.CONSTANT_ISSUE);
    assertThat(remediation.baseEffort()).isEqualTo("5min");
    assertThat(rule.deprecatedRuleKeys()).isEmpty();
  }

  @Test
  public void load_rule_without_taxonomy_with_supported_product_runtime() {
    @Rule(key = "S100")
    class TestRule {
    }

    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_10_1);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Function names should comply with a naming convention");
    assertThat(rule.htmlDescription()).isEqualTo("<p>description S100</p>");
    assertThat(rule.severity()).isEqualTo("MINOR");
    assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rule.cleanCodeAttribute()).isNull();
    assertThat(rule.defaultImpacts()).isNotEmpty();
    rule.defaultImpacts().forEach((softwareQuality, severity) -> {
      assertThat(softwareQuality.name()).isEqualTo("MAINTAINABILITY");
      assertThat(severity.name()).isEqualTo("LOW");
    });
    assertThat(rule.status()).isEqualTo(RuleStatus.READY);
    assertThat(rule.tags()).containsExactly("convention");
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.CONSTANT_ISSUE);
    assertThat(remediation.baseEffort()).isEqualTo("5min");
    assertThat(rule.deprecatedRuleKeys()).isEmpty();
  }

  @Test
  public void load_taxonomy_rule_with_unsupported_product_runtime() {
    @Rule(key = "taxonomy_rule")
    class TestRule {
    }

    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_5);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("taxonomy_rule");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Taxonomy rule with code block properties");
    assertThat(rule.htmlDescription()).isEqualTo("<p>description taxonomy rule</p>");
    assertThat(rule.severity()).isEqualTo("MINOR");
    assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rule.cleanCodeAttribute()).isNull();
    assertThat(rule.defaultImpacts()).isEqualTo(Map.of(MAINTAINABILITY, LOW));
    assertThat(rule.status()).isEqualTo(RuleStatus.READY);
    assertThat(rule.tags()).containsExactly("convention");
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.CONSTANT_ISSUE);
    assertThat(remediation.baseEffort()).isEqualTo("5min");
    assertThat(rule.deprecatedRuleKeys()).isEmpty();
  }

  @Test
  public void not_valid_taxonomy_rule_with_supported_product_runtime_throws_exception() {
    @Rule(key = "not_valid_taxonomy_rule")
    class TestRule {
    }

    List<Class<?>> classes = singletonList(TestRule.class);
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_10_1);
    assertThrows("Invalid property: impacts", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void not_valid_taxonomy_rule_with_impacts_empty_with_supported_product_runtime_throws_exception() {
    @Rule(key = "not_valid_taxonomy_rule_2")
    class TestRule {
    }

    List<Class<?>> classes = singletonList(TestRule.class);
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_10_1);
    assertThrows("Invalid property: impacts", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void load_rule_S100() {
    @Rule(key = "S100")
    class TestRule {
    }

    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Function names should comply with a naming convention");
    assertThat(rule.htmlDescription()).isEqualTo("<p>description S100</p>");
    assertThat(rule.severity()).isEqualTo("MINOR");
    assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rule.status()).isEqualTo(RuleStatus.READY);
    assertThat(rule.tags()).containsExactly("convention");
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.CONSTANT_ISSUE);
    assertThat(remediation.baseEffort()).isEqualTo("5min");
    assertThat(rule.deprecatedRuleKeys()).isEmpty();
  }

  @Test
  public void load_education_rule_S102_with_unsupported_product_runtime() throws IOException {
    @Rule(key = "S102")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S102");
    String expectedHtmlDescription = Files.readString(Paths.get("src/test/resources/org/sonarsource/analyzer/commons/S102_fallback.html"));
    assertThat(rule).isNotNull();
    assertThat(rule.htmlDescription()).isEqualTo(expectedHtmlDescription);
    assertThat(rule.ruleDescriptionSections()).isEmpty();
  }

  @Test
  public void load_education_rule_S102_with_supported_product_runtime() throws IOException {
    @Rule(key = "S102")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_9);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S102");
    String expectedHtmlDescription = Files.readString(Paths.get("src/test/resources/org/sonarsource/analyzer/commons/S102_fallback.html"));
    assertThat(rule).isNotNull();
    assertThat(rule.htmlDescription()).isEqualTo(expectedHtmlDescription);
    assertThat(rule.ruleDescriptionSections()).hasSize(5);
  }

  @Test
  public void load_rule_S110() {
    @Rule(key = "S110")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S110");
    assertThat(rule).isNotNull();
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.LINEAR_OFFSET);
    assertThat(remediation.baseEffort()).isEqualTo("4h");
    assertThat(remediation.gapMultiplier()).isEqualTo("30min");
    assertThat(rule.gapDescription()).isEqualTo("Number of parents above the defined threshold");
  }

  @Test
  public void load_rules_key_based() {
    ruleMetadataLoader.addRulesByRuleKey(newRepository, Arrays.asList("S110", "S100"));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule ruleS110 = repository.rule("S110");
    assertThat(ruleS110).isNotNull();
    assertThat(ruleS110.name()).isEqualTo("Inheritance tree of classes should not be too deep");
    assertThat(ruleS110.htmlDescription()).isEqualTo("<p>description S110</p>");

    RulesDefinition.Rule ruleS100 = repository.rule("S100");
    assertThat(ruleS100).isNotNull();
    assertThat(ruleS100.name()).isEqualTo("Function names should comply with a naming convention");
    assertThat(ruleS100.htmlDescription()).isEqualTo("<p>description S100</p>");
  }

  @Test
  public void load_missing_rule_key_based() {
    List<String> ruleList = List.of("");
    assertThrows("Empty key", IllegalStateException.class, () -> ruleMetadataLoader.addRulesByRuleKey(newRepository, ruleList));
  }

  @Test
  public void load_rule_S123() {
    @Rule(key = "S123")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    RulesDefinition.Rule rule = repository.rule("S123");
    assertThat(rule).isNotNull();
    DebtRemediationFunction remediation = rule.debtRemediationFunction();
    assertThat(remediation).isNotNull();
    assertThat(remediation.type()).isEqualTo(DebtRemediationFunction.Type.LINEAR);
    assertThat(remediation.gapMultiplier()).isEqualTo("10min");
    assertThat(rule.gapDescription()).isNull();
  }

  @Test
  public void load_rule_with_deprecated_key() {
    @Rule(key = "S123")
    @DeprecatedRuleKey(repositoryKey = "oldRepo", ruleKey = "oldKey")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rule("S123").deprecatedRuleKeys()).containsExactlyInAnyOrder(RuleKey.of("oldRepo", "oldKey"));
  }

  @Test
  public void load_rule_with_many_deprecated_keys() {
    @Rule(key = "S123")
    @DeprecatedRuleKey(repositoryKey = "oldRepo1", ruleKey = "oldKey1")
    @DeprecatedRuleKey(repositoryKey = "oldRepo2", ruleKey = "oldKey2")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rule("S123").deprecatedRuleKeys()).containsExactlyInAnyOrder(RuleKey.of("oldRepo1", "oldKey1"), RuleKey.of("oldRepo2", "oldKey2"));
  }

  @Test
  public void load_rule_with_deprecated_key_without_repo() {
    @Rule(key = "S123")
    @DeprecatedRuleKey(ruleKey = "oldKey")
    class TestRule {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();

    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rule("S123").deprecatedRuleKeys()).containsOnly(RuleKey.of(RULE_REPOSITORY_KEY, "oldKey"));
  }

  @Test
  public void load_rule_list() {
    @Rule(key = "S100")
    class RuleA {
    }
    @Rule(key = "S110")
    class RuleB {
    }
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, Arrays.asList(RuleA.class, RuleB.class));
    newRepository.done();
    RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rule("S100")).isNotNull();
    assertThat(repository.rule("S110")).isNotNull();
  }

  @Test
  public void no_profile() {
    @Rule(key = "S100")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_3);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("S100");
    assertThat(rule.activatedByDefault()).isFalse();
  }

  @Test
  public void rule_not_in_default_profile() {
    @Rule(key = "S123")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, DEFAULT_PROFILE_PATH, SONAR_RUNTIME_9_3);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("S123");
    assertThat(rule.activatedByDefault()).isFalse();
  }

  @Test
  public void rule_in_default_profile() {
    @Rule(key = "S100")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, DEFAULT_PROFILE_PATH, SONAR_RUNTIME_9_3);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("S100");
    assertThat(rule.activatedByDefault()).isTrue();
  }

  @Test
  public void test_rule_without_annotation() {
    class TestRule {
    }

    List<Class<?>> classes = singletonList(TestRule.class);
    assertThrows("No Rule annotation was found on TestRule", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void test_rule_without_key() {
    @Rule
    class TestRule {
    }

    List<Class<?>> classes = singletonList(TestRule.class);
    assertThrows("Empty key", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void test_missing_rule_key() {
    @Rule(key = "S404")
    class TestRule {
    }

    List<Class<?>> classes = List.of(TestRule.class);
    assertThrows("Rule not found: S404", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void test_not_valid_json_rule() {
    @Rule(key = "notvalid")
    class TestRule {
    }

    List<Class<?>> classes = List.of(TestRule.class);
    assertThrows("Can't read resource: org/sonarsource/analyzer/commons/notvalid.json", IllegalStateException.class,
      () -> ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, classes));
  }

  @Test
  public void getStringArray() {
    Map<String, Object> map = singletonMap("key", Arrays.asList("x", "y"));
    assertThat(RuleMetadataLoader.getStringArray(map, "key")).containsExactly("x", "y");
  }

  @Test
  public void getScopeIfPresent() {
    Optional<RuleScope> scope;

    scope = RuleMetadataLoader.getScopeIfPresent(emptyMap(), "scope");
    assertThat(scope).isNotPresent();

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", null), "scope");
    assertThat(scope).isNotPresent();

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", false), "scope");
    assertThat(scope).isNotPresent();

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", "main"), "scope");
    assertThat(scope).contains(RuleScope.MAIN);

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", "Main"), "scope");
    assertThat(scope).contains(RuleScope.MAIN);

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", "Test"), "scope");
    assertThat(scope).contains(RuleScope.TEST);

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", "Tests"), "scope");
    assertThat(scope).contains(RuleScope.TEST);

    scope = RuleMetadataLoader.getScopeIfPresent(singletonMap("scope", "All"), "scope");
    assertThat(scope).contains(RuleScope.ALL);

    Map<String, Object> map = singletonMap("scope", "Unknown");
    assertThatThrownBy(() -> RuleMetadataLoader.getScopeIfPresent(map, "scope"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test(expected = IllegalStateException.class)
  public void getStringArray_with_invalid_type() {
    Map<String, Object> map = singletonMap("key", "x");
    RuleMetadataLoader.getStringArray(map, "key");
  }

  @Test
  public void getStringArray_without_property() {
    assertThat(RuleMetadataLoader.getStringArray(emptyMap(), "key")).isEmpty();
  }

  @Test
  public void test_security_hotspot() {
    @Rule(key = "S2092")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, DEFAULT_PROFILE_PATH, SONAR_RUNTIME_9_3);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("S2092");
    assertThat(rule.type()).isEqualTo(RuleType.SECURITY_HOTSPOT);
    assertThat(rule.securityStandards())
      .containsExactlyInAnyOrder("cwe:311", "cwe:315", "cwe:614", "owaspTop10:a2", "owaspTop10:a3", "owaspTop10-2021:a4", "owaspTop10-2021:a5");
  }

  @Test
  public void test_security_standards_on_10_10_return_asvs() {
    Set<String> securityStandards = getSecurityStandards(SONAR_RUNTIME_10_10);
    assertThat(securityStandards).containsExactlyInAnyOrder(
      "cwe:311", "cwe:315", "cwe:614",
      "owaspTop10:a2", "owaspTop10:a3",
      "owaspTop10-2021:a4", "owaspTop10-2021:a5",
      "pciDss-3.2:1.1.1", "pciDss-3.2:1.1.2",
      "owaspAsvs-4.0:2.1.1", "owaspAsvs-4.0:2.1.2",
      "stig-ASD_V5R3:V-222612"
    );
  }

  @Test
  public void test_security_standards_on_9_9_return_asvs() {
    Set<String> securityStandards = getSecurityStandards(SONAR_RUNTIME_9_9);
    assertThat(securityStandards).containsExactlyInAnyOrder(
      "cwe:311", "cwe:315", "cwe:614",
      "owaspTop10:a2", "owaspTop10:a3",
      "owaspTop10-2021:a4", "owaspTop10-2021:a5",
      "pciDss-3.2:1.1.1", "pciDss-3.2:1.1.2",
      "owaspAsvs-4.0:2.1.1", "owaspAsvs-4.0:2.1.2");
  }

  @Test
  public void test_security_standards_on_9_5_return_pci_dss() {
    Set<String> securityStandards = getSecurityStandards(SONAR_RUNTIME_9_5);
    assertThat(securityStandards).containsExactlyInAnyOrder(
      "cwe:311", "cwe:315", "cwe:614",
      "owaspTop10:a2", "owaspTop10:a3",
      "owaspTop10-2021:a4", "owaspTop10-2021:a5",
      "pciDss-3.2:1.1.1", "pciDss-3.2:1.1.2");
  }

  @Test
  public void test_security_standards_on_9_3_return_owasp_2021() {
    Set<String> securityStandards = getSecurityStandards(SONAR_RUNTIME_9_3);
    assertThat(securityStandards).containsExactlyInAnyOrder(
      "cwe:311", "cwe:315", "cwe:614",
      "owaspTop10:a2", "owaspTop10:a3",
      "owaspTop10-2021:a4", "owaspTop10-2021:a5");
  }

  @Test
  public void test_security_standards_before_9_3() {
    Set<String> securityStandards = getSecurityStandards(SONAR_RUNTIME_9_2);
    assertThat(securityStandards).containsExactlyInAnyOrder(
      "cwe:311", "cwe:315", "cwe:614",
      "owaspTop10:a2", "owaspTop10:a3");
  }

  @Test
  public void test_invalid_json_string() {
    @Rule(key = "rule_missing_title")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_3);
    try {
      ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
      fail("Should have failed");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
      assertThat(e).hasMessage("Invalid property: title");
    }
  }

  @Test
  public void test_invalid_json_string_array() {
    @Rule(key = "rule_wrong_tag")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SONAR_RUNTIME_9_3);
    try {
      ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
      fail("Should have failed");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
      assertThat(e).hasMessage("Invalid property: tags");
    }
  }

  @Test
  public void test_invalid_json_int_array() {
    @Rule(key = "rule_wrong_cwe")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, "org/sonarsource/analyzer/commons/profile_wrong_cwe.json", SONAR_RUNTIME_9_3);
    try {
      ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
      fail("Should have failed");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
      assertThat(e).hasMessage("Invalid property: CWE");
    }
  }

  private Set<String> getSecurityStandards(SonarRuntime sonarRuntime) {
    @Rule(key = "S2092")
    class TestRule {
    }
    ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, DEFAULT_PROFILE_PATH, sonarRuntime);
    ruleMetadataLoader.addRulesByAnnotatedClass(newRepository, singletonList(TestRule.class));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("S2092");
    return rule.securityStandards();
  }

}
