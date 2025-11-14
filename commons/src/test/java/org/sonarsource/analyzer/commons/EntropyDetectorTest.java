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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.analyzer.commons.EntropyDetector.DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER;
import static org.sonarsource.analyzer.commons.EntropyDetector.DEFAULT_ENTROPY_SCORE_INCREMENT;
import static org.sonarsource.analyzer.commons.EntropyDetector.DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY;

class EntropyDetectorTest {

  @ParameterizedTest
  @MethodSource("stringPerLevelForEntropyScore")
  void testProgressiveEntropyScoreSensibility(String input, int currentSensibility) {
    // The higher the sensibility, the more we filter.
    // We want to test that the current level accept the string, but not the level above.
    EntropyDetector current = new EntropyDetector(currentSensibility);
    assertTrue(current.hasEnoughEntropy(input));
    EntropyDetector above = new EntropyDetector(currentSensibility + 1);
    assertFalse(above.hasEnoughEntropy(input));
  }

  static Stream<Arguments> stringPerLevelForEntropyScore() {
    return Stream.of(
      Arguments.of("____________________________________________________________", 0),
      Arguments.of("abcdef______________________________________________________", 1),
      Arguments.of("abcdefghijkl________________________________________________", 2),
      Arguments.of("abcdefghijklmnopqr__________________________________________", 3),
      Arguments.of("abcdefghijklmnopqrstuvwx____________________________________", 4),
      Arguments.of("abcdefghijklmnopqrstuvwxyzABCD______________________________", 5),
      Arguments.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJ________________________", 6),
      Arguments.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP__________________", 7),
      Arguments.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV____________", 8),
      Arguments.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01______", 9)
    );
  }

  @Test
  void testLastLevelEntropySensibility() {
    EntropyDetector current = new EntropyDetector(10);
    assertFalse(current.hasEnoughEntropy("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567"));
  }

  @Test
  void testEntropyHighSensibility() {
    EntropyDetector current = new EntropyDetector(7);
    // Low entropy
    assertFalse(current.hasEnoughEntropy("the_the_the_the_the_the_the"));
    // Low entropy
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    // High entropy
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
  }

  @Test
  void testEntropyLowSensibility() {
    EntropyDetector current = new EntropyDetector(1);
    assertTrue(current.hasEnoughEntropy("my package"));
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    assertTrue(current.hasEnoughEntropy("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
    // Entropy is so low that we still consider it as not random
    assertFalse(current.hasEnoughEntropy("xxxxxxxxxxxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx"));
  }

  @Test
  void testCustomMinimumSecretLength() {
    EntropyDetector current = new EntropyDetector(
      1,
      10,
      DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER,
      DEFAULT_ENTROPY_SCORE_INCREMENT);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx"));
  }

  @Test
  void testCustomEntropyIncreaseFactorByMissingCharacter() {
    EntropyDetector current = new EntropyDetector(
      1,
      DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY,
      1.05,
      DEFAULT_ENTROPY_SCORE_INCREMENT);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx"));
  }

  @Test
  void testCustomEntropyScoreIncrement() {
    EntropyDetector current = new EntropyDetector(
      1,
      DEFAULT_MIN_SECRET_LENGTH_FOR_GIVEN_ENTROPY,
      DEFAULT_ENTROPY_INCREASE_FACTOR_BY_MISSING_CHARACTER,
      0.5);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx"));
  }
}
