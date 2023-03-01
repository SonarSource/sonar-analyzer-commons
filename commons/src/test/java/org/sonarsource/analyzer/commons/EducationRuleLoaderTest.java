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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.server.rule.RulesDefinition.Context;

public class EducationRuleLoaderTest {

  private static final SonarRuntime RUNTIME = SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
  private static final Path EDUCATION_TEST_FILES_DIRECTORY = Paths.get("src/test/resources/org/sonarsource/analyzer/commons/education");
  private static final String RULE_REPOSITORY_KEY = "rule-definition-test";

  @Rule
  public LogTester logTester = new LogTester();
  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  private Context context;
  private NewRepository newRepository;
  private RulesDefinition.NewRule newRule;

  @Before
  public void setup() {
    context = new Context();
    newRepository = context.createRepository(RULE_REPOSITORY_KEY, "lang");
    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    newRule = newRepository.rule("MyRuleKey");
  }

  @Test
  public void supports_education_rules_descriptions() {
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 5), SonarQubeSide.SERVER, SonarEdition.DEVELOPER)).isEducationRuleDescriptionSupported()).isTrue();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 4), SonarQubeSide.SERVER, SonarEdition.DEVELOPER)).isEducationRuleDescriptionSupported()).isFalse();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 6), SonarQubeSide.SERVER, SonarEdition.SONARCLOUD)).isEducationRuleDescriptionSupported()).isTrue();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarLint(Version.create(9, 6))).isEducationRuleDescriptionSupported()).isTrue();
  }

  @Test
  public void supports_education_principles_metadata() {
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.DEVELOPER)).isEducationPrinciplesMetadataSupported()).isTrue();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 8), SonarQubeSide.SERVER, SonarEdition.SONARCLOUD)).isEducationPrinciplesMetadataSupported()).isTrue();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarLint(Version.create(9, 8))).isEducationPrinciplesMetadataSupported()).isTrue();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarQube(Version.create(9, 7), SonarQubeSide.SERVER, SonarEdition.DEVELOPER)).isEducationPrinciplesMetadataSupported()).isFalse();
    assertThat(new EducationRuleLoader(SonarRuntimeImpl.forSonarLint(Version.create(9, 7))).isEducationPrinciplesMetadataSupported()).isFalse();
  }

  @Test
  public void education_description_content_unsupported_product_runtime() throws IOException {
    SonarRuntime invalidRuntime = SonarRuntimeImpl.forSonarQube(Version.create(9, 4), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(invalidRuntime);
    String testFileContent = getTestFileContent("valid/S100.html");
    String fallbackDescription = educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(rule.ruleDescriptionSections()).isEmpty();
    assertThat(fallbackDescription).isEqualTo(testFileContent);
  }

  @Test
  public void education_description_simple_content() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("valid/S100.html");
    String fallbackDescription = educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(rule.ruleDescriptionSections()).hasSize(4);
    assertThat(rule.ruleDescriptionSections().get(0).getHtmlContent()).isEqualTo("Intro");
    assertThat(rule.ruleDescriptionSections().get(1).getHtmlContent()).isEqualTo("Explanation");
    RuleDescriptionSection ruleDescriptionSection = rule.ruleDescriptionSections().get(2);
    assertThat(ruleDescriptionSection.getContext()).isPresent();
    assertThat(ruleDescriptionSection.getContext().get().getKey()).isEqualTo("framework_1");
    assertThat(ruleDescriptionSection.getContext().get().getDisplayName()).isEqualTo("Framework-1");
    assertThat(ruleDescriptionSection.getHtmlContent()).isEqualTo("Details");
    assertThat(rule.ruleDescriptionSections().get(3).getHtmlContent()).isEqualTo("Links");
    assertThat(fallbackDescription).isEqualTo(testFileContent);
  }

  @Test
  public void education_description_content_with_multiple_sections() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String fallbackDescription = educationRuleLoader.setEducationDescriptionFromHtml(newRule, getTestFileContent("valid/S101.html"));
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

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
    assertThat(fallbackDescription).isEqualTo(getTestFileContent("valid/S101_fallback.html"));
  }

  @Test
  public void education_description_content_with_empty_sections() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("valid/S102.html");
    String fallbackDescription = educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(String.join("\n", logTester.logs(LoggerLevel.DEBUG))).isEqualTo("Skipping section 'introduction' for rule 'MyRuleKey', content is empty\n" +
      "Skipping section 'resources' for rule 'MyRuleKey', content is empty");
    assertThat(rule.ruleDescriptionSections()).hasSize(2);
    assertThat(fallbackDescription).isEqualTo(testFileContent);
  }

  @Test
  public void education_description_content_with_generic_how_to_fix_section() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("valid/S103.html");

    educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(rule.ruleDescriptionSections()).hasSize(4);
    assertThat(rule.ruleDescriptionSections().get(0).getHtmlContent()).isEqualTo("Intro");
    assertThat(rule.ruleDescriptionSections().get(0).getContext()).isEmpty();
    assertThat(rule.ruleDescriptionSections().get(1).getHtmlContent()).isEqualTo("Explanation");
    assertThat(rule.ruleDescriptionSections().get(1).getContext()).isEmpty();
    assertThat(rule.ruleDescriptionSections().get(2).getHtmlContent()).isEqualTo("Generic how to fix it section without framework specific content");
    assertThat(rule.ruleDescriptionSections().get(2).getContext()).isEmpty();
    assertThat(rule.ruleDescriptionSections().get(3).getHtmlContent()).isEqualTo("Links");
    assertThat(rule.ruleDescriptionSections().get(3).getContext()).isEmpty();
  }

  @Test
  public void education_description_content_without_resources_section() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("valid/S104.html");

    educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(rule.ruleDescriptionSections()).hasSize(3);
    assertThat(rule.ruleDescriptionSections().get(0).getHtmlContent()).isEqualTo("Explanation");
    assertThat(rule.ruleDescriptionSections().get(0).getContext()).isEmpty();
    assertThat(rule.ruleDescriptionSections().get(1).getHtmlContent()).isEqualTo("Content-1");
    assertThat(rule.ruleDescriptionSections().get(1).getContext().get().getKey()).isEqualTo("framework_1");
    assertThat(rule.ruleDescriptionSections().get(2).getHtmlContent()).isEqualTo("Content-2");
    assertThat(rule.ruleDescriptionSections().get(2).getContext().get().getKey()).isEqualTo("framework_2");
  }

  @Test
  public void education_description_content_with_non_education_format() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("invalid/S100.html");
    String fallbackDescription = educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
    newRepository.done();
    RulesDefinition.Rule rule = context.repository(RULE_REPOSITORY_KEY).rule("MyRuleKey");

    assertThat(logTester.logs(LoggerLevel.ERROR)).isEmpty();
    assertThat(rule.ruleDescriptionSections()).isEmpty();
    assertThat(fallbackDescription).isEqualTo(testFileContent);
  }

  @Test
  public void education_description_content_with_invalid_format() throws IOException {
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(RUNTIME);
    String testFileContent = getTestFileContent("invalid/S101.html");

    exceptionRule.expect(IllegalStateException.class);
    exceptionRule.expectMessage("Invalid education rule format for 'MyRuleKey', following header is missing: '<h2>How to fix it\\?</h2>'");
    educationRuleLoader.setEducationDescriptionFromHtml(newRule, testFileContent);
  }

  @Test
  public void education_metadata() {
    SonarRuntime invalidRuntime = SonarRuntimeImpl.forSonarQube(Version.create(9, 4), SonarQubeSide.SERVER, SonarEdition.DEVELOPER);

    RulesDefinition.Rule rule = invokeSetEducationMetadataFromJson(invalidRuntime, Map.of("educationPrinciples", List.of("defense_in_depth", "never_trust_user_input")));
    assertThat(rule.educationPrincipleKeys()).isEmpty();

    rule = invokeSetEducationMetadataFromJson(RUNTIME, Map.of());
    assertThat(rule.educationPrincipleKeys()).isEmpty();

    rule = invokeSetEducationMetadataFromJson(RUNTIME, Map.of("educationPrinciples", "notAList"));
    assertThat(rule.educationPrincipleKeys()).isEmpty();

    rule = invokeSetEducationMetadataFromJson(RUNTIME, Map.of("educationPrinciples", List.of("defense_in_depth", "never_trust_user_input")));
    assertThat(rule.educationPrincipleKeys()).containsExactly("defense_in_depth", "never_trust_user_input");
  }

  private static RulesDefinition.Rule invokeSetEducationMetadataFromJson(SonarRuntime runtime, Map<String, Object> ruleMetadata) {
    Context context = new Context();
    NewRepository newRepository = context.createRepository(RULE_REPOSITORY_KEY, "lang");
    new RulesDefinitionAnnotationLoader().load(newRepository, new Class[]{TestRule.class});
    RulesDefinition.NewRule newRule = newRepository.rule("MyRuleKey");
    EducationRuleLoader educationRuleLoader = new EducationRuleLoader(runtime);
    educationRuleLoader.setEducationMetadataFromJson(newRule, ruleMetadata);
    newRepository.done();
    Repository repository = context.repository(RULE_REPOSITORY_KEY);
    return repository.rule("MyRuleKey");
  }

  private static String getTestFileContent(String filePath) throws IOException {
    return Files.readString(EDUCATION_TEST_FILES_DIRECTORY.resolve(filePath));
  }

  @org.sonar.check.Rule(key = "MyRuleKey", name = "MyRule", description = "MyDesc")
  class TestRule {
  }

}
