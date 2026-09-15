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
package org.sonarsource.analyzer.commons.appsec;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides default minimum key sizes for common cryptographic algorithms and utilities
 * for parsing and merging user-supplied key-size configuration.
 *
 * <p>The configuration format is a comma-separated list of {@code ALGORITHM:minKeySize} pairs,
 * for example {@code "RSA:2048,AES:256"}. Algorithm names are case-insensitive.</p>
 *
 * <p>The {@link #effectiveKeySizes(String)} method applies user-supplied overrides on top of
 * the {@link #DEFAULT_KEY_SIZES defaults}, so only the explicitly listed algorithms are
 * overridden — the rest keep their defaults.</p>
 */
public final class CryptographicKeySizeConfiguration {

  /**
   * Default minimum key sizes for common cryptographic algorithms, in {@code ALGORITHM:minKeySize} format.
   * This string is a compile-time constant and can be used as a {@code @RuleProperty} default value.
   */
  public static final String DEFAULT_KEY_SIZES = "RSA:2048,DH:2048,DIFFIEHELLMAN:2048,DSA:2048,AES:128,EC:224";

  /**
   * Pattern that matches standard EC curve names (e.g. {@code secp256r1}, {@code prime192v2})
   * and captures the key-bit count as group 2.
   */
  public static final Pattern EC_CURVE_KEY_PATTERN = Pattern.compile("^(secp|prime|sect|c2tnb)(\\d+)", Pattern.CASE_INSENSITIVE);

  private CryptographicKeySizeConfiguration() {
  }

  /**
   * Parses a comma-separated list of {@code ALGORITHM:minKeySize} pairs into a map.
   * Algorithm names are normalized to upper case. Invalid or malformed entries are silently ignored.
   *
   * @param input a string like {@code "RSA:2048,AES:256"}
   * @return a mutable map from upper-cased algorithm name to minimum key size
   */
  public static Map<String, Integer> parseKeySizes(String input) {
    Map<String, Integer> result = new HashMap<>();
    for (String entry : input.split(",")) {
      String[] parts = entry.trim().split(":");
      if (parts.length == 2) {
        try {
          String algorithm = parts[0].trim().toUpperCase(Locale.ROOT);
          int keySize = Integer.parseInt(parts[1].trim());
          if (!algorithm.isEmpty() && keySize > 0) {
            result.put(algorithm, keySize);
          }
        } catch (NumberFormatException e) {
          // ignore invalid entries
        }
      }
    }
    return result;
  }

  /**
   * Returns the effective key-size map: starts from {@link #DEFAULT_KEY_SIZES} and applies
   * {@code userConfig} overrides on top. Only the algorithms listed in {@code userConfig} are
   * overridden; others keep their default values.
   *
   * @param userConfig a string in the same format as {@link #DEFAULT_KEY_SIZES}
   * @return a mutable map from upper-cased algorithm name to minimum key size
   */
  public static Map<String, Integer> effectiveKeySizes(String userConfig) {
    Map<String, Integer> map = parseKeySizes(DEFAULT_KEY_SIZES);
    map.putAll(parseKeySizes(userConfig));
    return map;
  }

  /**
   * Extracts the key-bit count from a standard EC curve name.
   * Recognizes curves with prefixes {@code secp}, {@code prime}, {@code sect}, and {@code c2tnb}
   * (e.g. {@code secp256r1} → 256, {@code prime192v2} → 192).
   *
   * @param curveName the EC curve name string
   * @return the parsed key size in bits, or empty if the curve name does not match the expected pattern
   */
  public static OptionalInt extractEcKeySize(String curveName) {
    Matcher matcher = EC_CURVE_KEY_PATTERN.matcher(curveName);
    if (matcher.find()) {
      try {
        return OptionalInt.of(Integer.parseInt(matcher.group(2)));
      } catch (NumberFormatException e) {
        // digit run exceeds Integer range — treat as unrecognized curve
      }
    }
    return OptionalInt.empty();
  }
}
