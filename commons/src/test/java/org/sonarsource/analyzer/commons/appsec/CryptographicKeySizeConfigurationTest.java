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

import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CryptographicKeySizeConfigurationTest {

  @Test
  void defaultKeySizes_containsAllExpectedAlgorithms() {
    Map<String, Integer> defaults = CryptographicKeySizeConfiguration.parseKeySizes(
      CryptographicKeySizeConfiguration.DEFAULT_KEY_SIZES);

    assertThat(defaults)
      .containsEntry("RSA", 2048)
      .containsEntry("DH", 2048)
      .containsEntry("DIFFIEHELLMAN", 2048)
      .containsEntry("DSA", 2048)
      .containsEntry("AES", 128)
      .containsEntry("EC", 224);
  }

  @Test
  void parseKeySizes_normalizesAlgorithmNamesToUpperCase() {
    Map<String, Integer> result = CryptographicKeySizeConfiguration.parseKeySizes("rsa:4096,aes:256");
    assertThat(result)
      .containsEntry("RSA", 4096)
      .containsEntry("AES", 256);
  }

  @Test
  void parseKeySizes_ignoresInvalidEntries() {
    Map<String, Integer> result = CryptographicKeySizeConfiguration.parseKeySizes("RSA:2048,BROKEN,AES:notanumber,DSA:1024");
    assertThat(result)
      .containsEntry("RSA", 2048)
      .containsEntry("DSA", 1024)
      .doesNotContainKey("BROKEN")
      .doesNotContainKey("AES");
  }

  @Test
  void parseKeySizes_ignoresEntriesWithEmptyAlgorithmName() {
    Map<String, Integer> result = CryptographicKeySizeConfiguration.parseKeySizes(":2048,RSA:4096");
    assertThat(result)
      .containsEntry("RSA", 4096)
      .hasSize(1);
  }

  @Test
  void parseKeySizes_ignoresEntriesWithNonPositiveKeySize() {
    Map<String, Integer> result = CryptographicKeySizeConfiguration.parseKeySizes("RSA:0,DSA:-1,AES:256");
    assertThat(result)
      .containsEntry("AES", 256)
      .doesNotContainKey("RSA")
      .doesNotContainKey("DSA");
  }

  @Test
  void parseKeySizes_toleratesWhitespace() {
    Map<String, Integer> result = CryptographicKeySizeConfiguration.parseKeySizes(" RSA : 4096 , AES : 256 ");
    assertThat(result)
      .containsEntry("RSA", 4096)
      .containsEntry("AES", 256);
  }

  @Test
  void effectiveKeySizes_appliesUserOverridesOnTopOfDefaults() {
    Map<String, Integer> effective = CryptographicKeySizeConfiguration.effectiveKeySizes("RSA:4096,AES:64");

    assertThat(effective)
      .containsEntry("RSA", 4096)   // overridden
      .containsEntry("AES", 64)     // overridden
      .containsEntry("DH", 2048)    // default preserved
      .containsEntry("DSA", 2048)   // default preserved
      .containsEntry("EC", 224);    // default preserved
  }

  @Test
  void effectiveKeySizes_withEmptyUserConfig_returnsDefaults() {
    Map<String, Integer> effective = CryptographicKeySizeConfiguration.effectiveKeySizes("");
    Map<String, Integer> defaults = CryptographicKeySizeConfiguration.parseKeySizes(
      CryptographicKeySizeConfiguration.DEFAULT_KEY_SIZES);

    assertThat(effective).containsAllEntriesOf(defaults);
  }

  @ParameterizedTest
  @CsvSource({
    "secp112r1, 112",
    "secp256r1, 256",
    "prime192v2, 192",
    "sect163k1, 163",
    "c2tnb191v1, 191",
  })
  void extractEcKeySize_recognizesStandardCurves(String curveName, int expectedBits) {
    assertThat(CryptographicKeySizeConfiguration.extractEcKeySize(curveName))
      .isEqualTo(OptionalInt.of(expectedBits));
  }

  @ParameterizedTest
  @ValueSource(strings = {"EC", "some123v123", "primee123v23", "unknown"})
  void extractEcKeySize_returnsEmptyForUnrecognizedNames(String curveName) {
    assertThat(CryptographicKeySizeConfiguration.extractEcKeySize(curveName)).isEmpty();
  }
}
