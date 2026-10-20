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
import org.sonarsource.analyzer.commons.appsec.IpAddressClassifier.ParsedIp;

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
    "http://[::FFFF:7f00:1]:8080/7, ::FFFF:7f00:1",
    // URL user information is separate from the host
    "http://user@1.2.3.4/path, 1.2.3.4",
    "http://user:pass@[2003::1]:8080/path, 2003::1",
    "//user@1.2.3.4/path, 1.2.3.4",
    // An @ outside the URL authority does not introduce user information
    "http://1.2.3.4/path@5.6.7.8, 1.2.3.4",
    "http://1.2.3.4?email=user@example.org, 1.2.3.4",
    "http://1.2.3.4#user@example.org, 1.2.3.4",
    "'http://1.2.3.4,user@example.org', 1.2.3.4",
    "'http://1.2.3.4 user@example.org', 1.2.3.4",
    // Delimiters after the host
    "'1.2.3.4,5.6.7.8', 1.2.3.4",
    "'1.2.3.4 ', 1.2.3.4",
    "'1.2.3.4\u00a0rest', 1.2.3.4", // Nonbreaking space is a whitespace delimiter
    "http://1.2.3.4?query, 1.2.3.4",
    "1.2.3.4#fragment, 1.2.3.4",
    // Bare CIDRs retain their prefix; URL paths do not become CIDRs
    "1.2.3.4/0, 1.2.3.4/0",
    "2003::1/0, 2003::1/0",
    "127.0.0.1/7, 127.0.0.1/7",
    "2003::1/64, 2003::1/64",
    "[2003::1]/64, 2003::1/64",
    "http://127.0.0.1/7, 127.0.0.1",
    "//[2003::1]/64, 2003::1",
    // A slash after a port starts a path, even when it contains only digits
    "1.2.3.4:8080/0, 1.2.3.4",
    "[2003::1]:8080/0, 2003::1",
    "[2003::1]:8080/64, 2003::1",
    "1.2.3.4:8080/33, 1.2.3.4",
    "[2003::1]:8080/129, 2003::1",
    "1.2.3.4/path, 1.2.3.4",
    "1.2.3.4/３３, 1.2.3.4", // Non-ASCII digits form a path, not an ASCII CIDR suffix
    "1.2.3.4/, 1.2.3.4",
    // Zone suffixes remain opaque and preserve bare CIDRs
    "2003::1%, 2003::1%",
    "[2003::1%接口]/64, 2003::1%接口/64",
    "http://[2003::1%eth0]:8080/path, 2003::1%eth0"
  })
  void shouldExtractParsedAddress(String content, String expected) {
    assertThat(IpAddressExtractor.extract(content)).map(ParsedIp::literal).hasValue(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", // No address candidate
    " 1.2.3.4", // Leading whitespace
    "[2003::1", // Missing closing bracket
    "2003::1]", // Missing opening bracket
    "[[2003::1]]", // Nested bracket pairs
    "[2003::1]extra", // Unexpected text after the closing bracket
    "1.2.3.4:", // Empty port
    "1.2.3.4:bad", // Nonnumeric port
    "1.2.3.4:-1", // Negative port
    "1.2.3.4:65536", // Port exceeds 65535
    "1.2.3.4:99999999999999999999", // Port overflows an integer
    "[2003::1]:", // Empty port
    "[2003::1]:-1", // Negative port
    "[2003::1]:65536", // Port exceeds 65535
    "http://[2003::1]:invalid/path", // Nonnumeric port
    "1.2.3.4-foo", // Text appended to an address
    "1.2.3.4~foo", // Text appended with punctuation
    "1.2.3.4!x", // Unexpected punctuation
    "http://1.2.3.4;x/path", // Punctuation in the URL host
    "1.2.3.4.", // Trailing dot
    "1.2.3.4.5", // Too many octets
    "prefix1.2.3.4", // Text prepended to an address
    "1.2.3.4/33", // Invalid IPv4 CIDR prefix
    "2003::1/129", // Invalid IPv6 CIDR prefix
    "prefix/1.2.3.4", // Address does not start the content
    "http://host1.2.3.4/path", // Host name instead of an IP address
    "http://1.2.3.4@example.org/path" // IP address is user information, not the host
  })
  void shouldRejectContentWithoutValidAddress(String content) {
    assertThat(IpAddressExtractor.extract(content)).isEmpty();
  }
}
