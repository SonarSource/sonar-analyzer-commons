/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

public class EntropyDetector {
  public static final int DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY = 25;
  public static final double DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER = 1.034;
  public static final double DEFAULT_ENTROPY_SCORE_INCREMENT = 0.6;

  private final double minEntropyThreshold;
  private final int minimumSecretLengthForGivenEntropy;
  private final double entropyIncreaseFactorByMissingCharacter;

  /**
   * Randomness sensibility should be between 0 and 10.
   */
  public EntropyDetector(double randomnessSensibility) {
    this(randomnessSensibility, DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY, DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER, DEFAULT_ENTROPY_SCORE_INCREMENT);
  }

  public EntropyDetector(double randomnessSensibility,
    int minimumSecretLengthForGivenEntropy,
    double entropyIncreaseFactorByMissingCharacter,
    double entropyScoreIncrement) {
    this.minimumSecretLengthForGivenEntropy = minimumSecretLengthForGivenEntropy;
    this.entropyIncreaseFactorByMissingCharacter = entropyIncreaseFactorByMissingCharacter;
    this.minEntropyThreshold = randomnessSensibility * entropyScoreIncrement;
  }

  public boolean hasEnoughEntropy(String literal) {
    double effectiveMinEntropyThreshold = minEntropyThreshold;
    if (literal.length() < minimumSecretLengthForGivenEntropy) {
      int missingCharacterCount = minimumSecretLengthForGivenEntropy - literal.length();
      // increase the entropy threshold constraint when there's not enough characters
      effectiveMinEntropyThreshold *= Math.pow(entropyIncreaseFactorByMissingCharacter, missingCharacterCount);
    }
    return ShannonEntropy.calculate(literal) >= effectiveMinEntropyThreshold;
  }
}
