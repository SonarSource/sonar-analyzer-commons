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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntropyDetectorTest {

  @ParameterizedTest
  @MethodSource("stringPerLevelForEntropyScore")
  void test_progressive_entropy_score_sensibility(String input, int currentSensibility) {
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
  void test_last_level_entropy_sensibility() {
    EntropyDetector current = new EntropyDetector(10);
    assertFalse(current.hasEnoughEntropy("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567"));
  }

  @Test
  void test_entropy_high_sensibility() {
    EntropyDetector current = new EntropyDetector(7);
    // Low entropy
    assertFalse(current.hasEnoughEntropy("the_the_the_the_the_the_the"));
    // Low entropy
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    // High entropy
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
  }

  @Test
  void test_entropy_low_sensibility() {
    EntropyDetector current = new EntropyDetector(1);
    assertTrue(current.hasEnoughEntropy("my package"));
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    assertTrue(current.hasEnoughEntropy("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
    // Entropy is so low that we still consider it as not random
    assertFalse(current.hasEnoughEntropy("xxxxxxxxxxxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx"));
  }

  @Test
  void test_custom_minimum_secret_length() {
    EntropyDetector current = new EntropyDetector(1, 10, 1.034, 0.6);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx"));
  }

  @Test
  void test_custom_entropy_increase_factor_by_missing_character() {
    EntropyDetector current = new EntropyDetector(1, 25, 1.05, 0.6);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx_xxx"));
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx_xxx"));
  }

  @Test
  void test_custom_entropy_score_increment() {
    EntropyDetector current = new EntropyDetector(1, 25, 1.034, 0.5);
    assertTrue(current.hasEnoughEntropy("xxx_xxx_xxx_xxx_xxx"));
    assertFalse(current.hasEnoughEntropy("xxx_xxx"));
  }
}
