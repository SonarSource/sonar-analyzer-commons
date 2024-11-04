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

public class EntropyDetector {
  private static final int DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY = 25;
  private static final double DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER = 1.034;
  private static final double DEFAULT_ENTROPY_SCORE_INCREMENT = 0.6;

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
