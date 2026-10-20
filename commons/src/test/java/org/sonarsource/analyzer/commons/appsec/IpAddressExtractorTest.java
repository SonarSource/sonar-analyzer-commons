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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressExtractorTest {

  @ParameterizedTest
  @CsvSource({
    // Bare addresses and endpoints
    "1.2.3.4, 1.2.3.4",
    "1.2.3.4:0, 1.2.3.4",
    "1.2.3.4:65535, 1.2.3.4",
    "1.2.3.4:000080, 1.2.3.4",
    "[1.2.3.4], 1.2.3.4",
    "2003::1, 2003::1",
    "::00000:2, ::00000:2",
    "::ffff:1.2.3.4, ::ffff:1.2.3.4",
    "[2003::1]:65535, 2003::1",
    // URL hosts, including scheme-relative URLs and mixed notation
    "http://1.2.3.4:8080/path, 1.2.3.4",
    "git+ssh://1.2.3.4/path, 1.2.3.4",
    "custom-scheme.v1://1.2.3.4/path, 1.2.3.4",
    "//[2003::1]:8080/path, 2003::1",
    "https://[2003::1.2.3.4]:8080/path, 2003::1.2.3.4",
    // Delimiters after the host
    "'1.2.3.4,5.6.7.8', 1.2.3.4",
    "'1.2.3.4 ', 1.2.3.4",
    "http://1.2.3.4?query, 1.2.3.4",
    "1.2.3.4#fragment, 1.2.3.4",
    // Bare CIDRs retain their prefix; URL paths do not become CIDRs
    "127.0.0.1/7, 127.0.0.1/7",
    "2003::1/64, 2003::1/64",
    "[2003::1]/64, 2003::1/64",
    "http://127.0.0.1/7, 127.0.0.1",
    "//[2003::1]/64, 2003::1",
    "1.2.3.4/path, 1.2.3.4",
    "1.2.3.4/, 1.2.3.4",
    // Zone suffixes remain opaque and preserve bare CIDRs
    "2003::1%, 2003::1%",
    "[2003::1%接口]/64, 2003::1%接口/64",
    "http://[2003::1%eth0]:8080/path, 2003::1%eth0",
    // Preserve the complete candidate for numeric validation
    "1.2.3.4-foo, 1.2.3.4-foo",
    "1.2.3.4., 1.2.3.4.",
    "prefix1.2.3.4, prefix1.2.3.4",
    "1.2.3.4/33, 1.2.3.4/33"
  })
  void shouldExtractCompleteAddressCandidate(String content, String expected) {
    assertThat(IpAddressExtractor.extract(content)).hasValue(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    " 1.2.3.4",
    "[2003::1",
    "2003::1]",
    "[[2003::1]]",
    "[2003::1]extra",
    "1.2.3.4:",
    "1.2.3.4:bad",
    "1.2.3.4:-1",
    "1.2.3.4:65536",
    "1.2.3.4:99999999999999999999",
    "[2003::1]:",
    "[2003::1]:-1",
    "[2003::1]:65536",
    "http://[2003::1]:invalid/path"
  })
  void shouldRejectMalformedEndpoint(String content) {
    assertThat(IpAddressExtractor.extract(content)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1.2.3.4-foo",
    "1.2.3.4.",
    "1.2.3.4.5",
    "prefix1.2.3.4",
    "prefix/1.2.3.4",
    "http://host1.2.3.4/path",
    "http://user@1.2.3.4/path",
    "1.2.3.4/33"
  })
  void shouldNotParseAValidPrefixOfAnInvalidCandidate(String content) {
    assertThat(IpAddressExtractor.extract(content).flatMap(IpAddressClassifier::parse)).isEmpty();
  }
}
