/*
 * SonarSource Analyzers Commons Configurations
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
package org.sonarsource.analyzer.commons.appsec.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

/**
 * Generates the validation corpus published next to {@code secret-patterns.json}: the sample values whose
 * classification the patterns must reproduce, in every engine that consumes the export.
 *
 * <p>Exporting the patterns alone is not enough to keep non-JVM analyzers in sync. A regex that fails to compile, or
 * that behaves differently, in .NET or JavaScript changes what gets suppressed with no signal at all - SonarJS, for
 * one, drops patterns its engine rejects. The corpus turns that into a test: {@code knownNonSecrets} must all be
 * suppressed, and {@code secretCandidates} must all survive. The second half matters as much as the first, because a
 * pattern that is too broad in a foreign engine hides real hardcoded secrets rather than merely adding noise.
 *
 * <p>Output is deterministic and pretty-printed so the artifact diffs cleanly. Run via {@code exec:java} at build time;
 * {@code main} takes the target file path as its single argument.
 */
public final class SecretExclusionCorpusExporter {

  private static final String DESCRIPTION = "Validation corpus for secret-patterns.json, generated from " +
    "SecretClassifier in sonar-analyzer-commons. Do not edit by hand. Every \"knownNonSecrets\" value must be " +
    "suppressed by the patterns in secret-patterns.json, and no \"secretCandidates\" value may be. A known " +
    "non-secret's \"category\" records which pattern group suppresses it in the JVM implementation, which is " +
    "first-match-wins; it is informational and not part of the contract.";

  private SecretExclusionCorpusExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: SecretExclusionCorpusExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  /**
   * Builds the JSON document from the classifier's exported samples. The known non-secrets are flattened to one entry
   * per value, each carrying its category, rather than nested per category: consumers assert on the values and would
   * otherwise all have to flatten the groups themselves.
   */
  public static String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("description", DESCRIPTION);

    JsonArray knownNonSecrets = new JsonArray();
    for (SecretClassifier.SampleGroupView group : SecretClassifier.exportKnownNonSecretSamples()) {
      for (String value : group.values()) {
        JsonObject sample = new JsonObject();
        sample.addProperty("value", value);
        sample.addProperty("category", group.category());
        knownNonSecrets.add(sample);
      }
    }
    root.add("knownNonSecrets", knownNonSecrets);
    root.add("secretCandidates", JsonExports.toArray(SecretClassifier.exportSecretCandidateSamples()));

    return JsonExports.GSON.toJson(root) + "\n";
  }
}
