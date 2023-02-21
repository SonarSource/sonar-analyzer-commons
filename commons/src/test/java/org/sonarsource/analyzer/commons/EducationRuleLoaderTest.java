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

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.server.rule.RuleDescriptionSection;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinitionAnnotationLoader;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.check.Rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.api.server.rule.RulesDefinition.Context;
import static org.sonarsource.analyzer.commons.EducationRuleLoader.runtimeSupportsEducationRules;

public class EducationRuleLoaderTest {

  private static final SonarRuntime RUNTIME = SonarRuntimeImpl.forSonarQube(Version.create(9, 7), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
  private static final String RULE_REPOSITORY_KEY = "rule-definition-test";
  private static final String RESOURCE_PATH = "valid";

  @org.junit.Rule
  public LogTester logTester = new LogTester();
  private Context context;
  private NewRepository newRepository;

  @Before
  public void setup() {
    context = new Context();
    newRepository = context.createRepository(RULE_REPOSITORY_KEY, "lang");
  }

  @Test
  public void supports_education_rules() {
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarQube(Version.create(9, 6), SonarQubeSide.SERVER, SonarEdition.DEVELOPER))).isTrue();
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarQube(Version.create(9, 5), SonarQubeSide.SERVER, SonarEdition.DEVELOPER))).isTrue();
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarQube(Version.create(9, 4), SonarQubeSide.SERVER, SonarEdition.DEVELOPER))).isFalse();
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarQube(Version.create(9, 6), SonarQubeSide.SERVER, SonarEdition.SONARCLOUD))).isTrue();
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarLint(Version.create(9, 6)))).isFalse();
    assertThat(runtimeSupportsEducationRules(SonarRuntimeImpl.forSonarLint(Version.create(9, 6)))).isFalse();
  }

  @Test
  public void add_education_rules_invalid_runtime() {
    @Rule(key = "S100", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    SonarRuntime invalidRuntime = SonarRuntimeImpl.forSonarQube(Version.create(9, 4), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, invalidRuntime);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule.htmlDescription()).isEqualTo("MyDesc");
    assertThat(rule.ruleDescriptionSections()).isEmpty();
  }

  @Test
  public void add_education_rules_simple() {
    @Rule(key = "S100", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule.htmlDescription()).isEqualTo("MyDesc");
    assertThat(rule.ruleDescriptionSections()).hasSize(4);
    assertThat(rule.ruleDescriptionSections().get(0).getHtmlContent()).isEqualTo("Intro");
    assertThat(rule.ruleDescriptionSections().get(1).getHtmlContent()).isEqualTo("Explanation");
    RuleDescriptionSection ruleDescriptionSection = rule.ruleDescriptionSections().get(2);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("framework_1");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("Framework-1");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details");
    assertThat(rule.ruleDescriptionSections().get(3).getHtmlContent()).isEqualTo("Links");
  }

  @Test
  public void add_education_rules_how_to_fix_section() {
    @Rule(key = "S101", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S101");
    assertThat(rule.ruleDescriptionSections()).hasSize(7);
    RuleDescriptionSection ruleDescriptionSection = rule.ruleDescriptionSections().get(2);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("framework_with_space_in_the_name");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("Framework With Space In The Name");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details of framework with space in the name");
    ruleDescriptionSection = rule.ruleDescriptionSections().get(3);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("framework_with__pec_al_ch_r_cters_in_n_me__");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("Framework.with.$pec!al.ch@r@cters.in.n@me!?");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details of framework with special characters in the name");
    ruleDescriptionSection = rule.ruleDescriptionSections().get(4);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("framework_name_with_trailing_spaces");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("Framework-name-with-trailing-spaces");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details of framework with name with trailing spaces");
    ruleDescriptionSection = rule.ruleDescriptionSections().get(5);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("another_frameworkname");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("another FrameworkName");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details of framework with simple name");
  }

  @Test
  public void add_education_rules_invalid_how_to_fix_section() {
    @Rule(key = "S101", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader("invalid", "invalid", RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    assertThat(String.join("\n", logTester.logs(LoggerLevel.ERROR))).isEqualTo(
      "Unable to load education rule: invalid/S101.html. Invalid education rule format for S101, context based patch is missing.");
  }

  @Test
  public void add_education_rules_invalid_format() {
    @Rule(key = "S100", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader("invalid", "invalid", RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    assertThat(String.join("\n", logTester.logs(LoggerLevel.ERROR))).isEqualTo(
      "Unable to load education rule: invalid/S100.html. Invalid education rule format for S100, following header is missing: '<h2>Why is this an issue\\?</h2>'"
    );
  }

  @Test
  public void add_education_rules_no_rule_annotation() {
    class TestRuleUnique {
    }

    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    List<Class<?>> ruleClasses = List.of(TestRuleUnique.class);
    assertThatThrownBy(() -> educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, ruleClasses))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No Rule annotation was found on org.sonarsource.analyzer.commons.EducationRuleLoaderTest$1TestRuleUnique");
  }

  @Test
  public void add_education_rules_no_rule_key_in_annotation() {
    @Rule
    class TestRule {
    }

    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    List<Class<?>> ruleClasses = List.of(TestRule.class);
    assertThatThrownBy(() -> educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, ruleClasses))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Empty rule key");
  }

  @Test
  public void add_education_rules_no_rule_created() {
    @Rule(key = "S100")
    class TestRule {
    }

    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    List<Class<?>> ruleClasses = List.of(TestRule.class);
    assertThatThrownBy(() -> educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, ruleClasses))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Rule not found: S100");
  }

  @Test
  public void add_education_rules_no_rule_content() {
    @Rule(key = "S100")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader("", "", RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));

    assertThat(String.join("\n", logTester.logs(LoggerLevel.DEBUG))).isEqualTo("No educational rule content for S100");
  }

  @Test
  public void education_principle_keys() {
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    EducationRuleLoader validMetadataEducationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, "metadata/valid", runtime);

    assertThat(validMetadataEducationRuleLoader.educationPrincipleKeys("S100")).containsExactlyInAnyOrder("defense_in_depth", "never_trust_user_input");
    assertThat(validMetadataEducationRuleLoader.educationPrincipleKeys("S101")).isNull();

    EducationRuleLoader invalidMetadataEducationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, "metadata/invalid", runtime);
    assertThat(invalidMetadataEducationRuleLoader.educationPrincipleKeys("S100")).isNull();
    assertThat(invalidMetadataEducationRuleLoader.educationPrincipleKeys("S101")).isNull();
  }

  @Test
  public void add_education_rules_with_education_metadata_supported() {
    @Rule(key = "S102", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, runtime);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S102");
    assertThat(rule.educationPrincipleKeys()).containsExactly("defense_in_depth", "never_trust_user_input");
  }

  @Test
  public void add_education_rules_with_education_metadata_supported_but_not_present() {
    @Rule(key = "S100", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, runtime);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S100");
    assertThat(rule.educationPrincipleKeys()).isEmpty();
  }

  @Test
  public void add_education_rules_with_empty_sections() {
    @Rule(key = "S103", name = "MyRule", description = "MyDesc")
    class TestRule {
    }

    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RESOURCE_PATH, RESOURCE_PATH, RUNTIME);
    educationRuleLoader.addEducationRulesByAnnotatedClass(newRepository, List.of(TestRule.class));
    newRepository.done();

    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    assertThat(repository.rules()).hasSize(1);
    RulesDefinition.Rule rule = repository.rule("S103");
    assertThat(rule.ruleDescriptionSections()).hasSize(2);
    assertThat(String.join("\n", logTester.logs(LoggerLevel.DEBUG))).isEqualTo("Skipping section 'introduction' for rule 'S103', content is empty\n" +
      "Skipping section 'resources' for rule 'S103', content is empty");
  }

}
