/*
 * SonarSource Analyzers Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.sensor.decision.DecisionRulesDefinition;
import org.sonar.api.batch.sensor.decision.DecisionRulesDefinition.NewDecisionRule;
import org.sonar.api.rule.RuleKey;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Declares the decision rules whose {@code <rule key>_decision.json} files rule-api copied next to the rule metadata.
 * <p>
 * Only use it when the runtime API is 14.4 or later: {@link DecisionRulesDefinition} does not exist on older engines.
 */
public class DecisionRuleLoader {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionRuleLoader.class);

  private final String resourceFolder;
  private final JsonParser jsonParser = new JsonParser();

  /**
   * @param resourceFolder the folder of the rule metadata, for example {@code org/sonar/l10n/java/rules/java}
   */
  public DecisionRuleLoader(String resourceFolder) {
    this.resourceFolder = resourceFolder;
  }

  /**
   * Declares the decision of each rule that has a decision file. A rule without one is skipped. An unreadable or invalid file is logged and
   * skipped, so that the candidates of that rule take the fallback of their kind instead of failing the analysis.
   */
  public void addDecisionRules(DecisionRulesDefinition.Context context, String repositoryKey, Collection<String> ruleKeys) {
    for (String ruleKey : ruleKeys) {
      String path = "/" + resourceFolder + "/" + ruleKey + "_decision.json";
      if (DecisionRuleLoader.class.getResource(path) != null) {
        try {
          declare(context.newDecisionRule(RuleKey.of(repositoryKey, ruleKey)), jsonParser.parseOrdered(Resources.toString(path, UTF_8)));
        } catch (IOException | RuntimeException e) {
          LOG.warn("Invalid decision file {}: {}", path, e.getMessage());
        }
      }
    }
  }

  private static void declare(NewDecisionRule rule, Map<String, Object> decision) {
    rule
      .setVersion(get(decision, "version", Number.class).intValue())
      .setQuestion(get(decision, "question", String.class))
      .setInstructions(get(decision, "instructions", String.class))
      .setPolicy(DecisionRulesDefinition.Policy.valueOf(get(decision, "policy", String.class).toUpperCase(Locale.ROOT)));
    get(decision, "facts", Map.class).forEach((name, fact) -> rule.addFact((String) name, get((Map<?, ?>) fact, "description", String.class)));
    if (decision.containsKey("keyFacts")) {
      rule.setKeyFacts(((List<?>) get(decision, "keyFacts", List.class)).stream().map(String.class::cast).toList());
    }
    if (decision.containsKey("confidenceThresholdOverride")) {
      rule.setConfidenceThreshold(get(decision, "confidenceThresholdOverride", Number.class).doubleValue());
    }
    rule.save();
  }

  private static <T> T get(Map<?, ?> json, String name, Class<T> type) {
    Object value = json.get(name);
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("'" + name + "' must be a " + type.getSimpleName().toLowerCase(Locale.ROOT));
    }
    return type.cast(value);
  }
}
