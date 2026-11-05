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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressCandidateExtractorTest {

  @ParameterizedTest
  @CsvSource({
    // Bare addresses and endpoints
    "192.0.2.1, 192.0.2.1",
    "192.0.2.1:0, 192.0.2.1",
    "192.0.2.1:65535, 192.0.2.1",
    "192.0.2.1:00080, 192.0.2.1",
    "2001:db8::1, 2001:db8::1",
    "::00000:2, ::00000:2",
    "::ffff:192.0.2.1, ::ffff:192.0.2.1",
    "[::ffff:192.0.2.1], ::ffff:192.0.2.1",
    "[2001:db8::1]:65535, 2001:db8::1",
    // URL hosts, including scheme-relative URLs and mixed notation
    "http://192.0.2.1:8080/path, 192.0.2.1",
    "http://192.0.2.1/path with space, 192.0.2.1",
    "git+ssh://192.0.2.1/path, 192.0.2.1",
    "custom-scheme.v1://192.0.2.1/path, 192.0.2.1",
    "//[2001:db8::1]:8080/path, 2001:db8::1",
    "https://[2001:db8::192.0.2.1]:8080/path, 2001:db8::192.0.2.1",
    "http://[::FFFF:7f00:1]:8080/7, ::FFFF:7f00:1",
    // URL user information is separate from the host
    "http://user@192.0.2.1/path, 192.0.2.1",
    "http://user:pass@[2001:db8::1]:8080/path, 2001:db8::1",
    "//user@192.0.2.1/path, 192.0.2.1",
    // An @ outside the URL authority does not introduce user information
    "http://192.0.2.1/path@192.0.2.2, 192.0.2.1",
    "http://192.0.2.1?email=user@example.org, 192.0.2.1",
    "http://192.0.2.1#user@example.org, 192.0.2.1",
    "'http://192.0.2.1,user@example.org', 192.0.2.1",
    "'http://192.0.2.1 user@example.org', 192.0.2.1",
    // URL lists yield only the first host's address
    "'http://192.0.2.1,https://192.0.2.2', 192.0.2.1",
    "http://192.0.2.1 https://192.0.2.2, 192.0.2.1",
    "'http://192.0.2.1:8080/path,https://192.0.2.2/other', 192.0.2.1",
    "https://[2001:db8::1]:8080/path http://192.0.2.2/other, 2001:db8::1",
    // The comma delimiter heuristic can extract an IP from user information rather than the host
    "'http://192.0.2.1,user:pass@192.0.2.2/path', 192.0.2.1",
    // Delimiters after the host
    "'192.0.2.1,192.0.2.2', 192.0.2.1",
    "'192.0.2.1 ', 192.0.2.1",
    "'192.0.2.1\u00a0rest', 192.0.2.1", // Nonbreaking space is a whitespace delimiter
    "http://192.0.2.1?query, 192.0.2.1",
    "192.0.2.1#fragment, 192.0.2.1",
    // Query strings do not affect host extraction
    "http://192.0.2.1?foo=bar&baz=qux, 192.0.2.1",
    "https://[2001:db8::1]:8443?foo=bar;baz=qux, 2001:db8::1",
    "http://192.0.2.1:8080?q=hello+world%20%26%3B&next=%2Fdocs, 192.0.2.1",
    "'https://user:pass@[2001:db8::1]/search?tags=a,b&filter=one;two#results', 2001:db8::1",
    "http://192.0.2.1?redirect=https://user@example.org/path?a=1&b=2, 192.0.2.1",
    // Bare CIDRs retain their prefix; URL paths do not become CIDRs
    "192.0.2.1/0, 192.0.2.1/0",
    "192.0.2.1/32, 192.0.2.1/32",
    "192.0.2.1/032, 192.0.2.1/032",
    "2001:db8::1/0, 2001:db8::1/0",
    "2001:db8::1/64, 2001:db8::1/64",
    "2001:db8::1/064, 2001:db8::1/064",
    "2001:db8::1/128, 2001:db8::1/128",
    "127.0.0.1/7, 127.0.0.1/7",
    "http://127.0.0.1/7, 127.0.0.1",
    "[2001:db8::1]/64, 2001:db8::1/64",
    "//[2001:db8::1]/64, 2001:db8::1",
    "http://192.0.2.1/123456, 192.0.2.1",
    "//[2001:db8::1]/123456, 2001:db8::1",
    // Overlong CIDR suffixes are treated as paths
    "192.0.2.1/1000, 192.0.2.1",
    "192.0.2.1/0032, 192.0.2.1",
    "2001:db8::1/1000, 2001:db8::1",
    "2001:db8::1/0128, 2001:db8::1",
    // A slash after a port starts a path, even when it contains only digits
    "192.0.2.1:8080/0, 192.0.2.1",
    "[2001:db8::1]:8080/0, 2001:db8::1",
    "[2001:db8::1]:8080/64, 2001:db8::1",
    "192.0.2.1:8080/33, 192.0.2.1",
    "[2001:db8::1]:8080/129, 2001:db8::1",
    "192.0.2.1:8080/123456, 192.0.2.1",
    "[2001:db8::1]:8080/123456, 2001:db8::1",
    "192.0.2.1/path, 192.0.2.1",
    "192.0.2.1/３３, 192.0.2.1", // Non-ASCII digits form a path, not an ASCII CIDR suffix
    "192.0.2.1/, 192.0.2.1",
    // Zone suffixes remain opaque and preserve bare CIDRs
    "2001:db8::1%, 2001:db8::1%",
    "[2001:db8::1%接口]/64, 2001:db8::1%接口/64",
    "http://[2001:db8::1%eth0]:8080/path, 2001:db8::1%eth0"
  })
  void shouldExtractCandidate(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extract(content)).hasValue(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "https://example.com/path, example.com",
    "999.2.3.4/33, 999.2.3.4/33",
    "[192.0.2.1:8080], 192.0.2.1:8080", // Port inside the brackets
    // Text appended with punctuation remains part of the candidate
    "192.0.2.1-foo, 192.0.2.1-foo",
    "192.0.2.1~foo, 192.0.2.1~foo",
    "192.0.2.1!x, 192.0.2.1!x",
    "http://192.0.2.1;x/path, 192.0.2.1;x", // Punctuation in the URL host
    "192.0.2.1., 192.0.2.1.", // Trailing dot
    "192.0.2.1.5, 192.0.2.1.5", // Too many octets
    "prefix192.0.2.1, prefix192.0.2.1", // Text prepended to an address
    "192.0.2.1/33, 192.0.2.1/33", // Invalid IPv4 CIDR prefix
    "192.0.2.1/100, 192.0.2.1/100", // Invalid IPv4 CIDR prefix (at most 2 digits possible)
    "2001:db8::1/129, 2001:db8::1/129", // Invalid IPv6 CIDR prefix
    "prefix/192.0.2.1, prefix", // Address does not start the content
    "http://host192.0.2.1/path, host192.0.2.1", // Host name instead of an IP address
    "http://192.0.2.1@example.org/path, example.org" // IP address is user information, not the host
  })
  void couldProduceNonIpAddressResult(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extract(content)).hasValue(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", // No address candidate
    " 192.0.2.1", // Leading whitespace
    "http:///path", // No URL host
    "/64", // CIDR suffix without an address
    "[2001:db8::1", // Missing closing bracket
    "2001:db8::1]", // Missing opening bracket
    "[[2001:db8::1]]", // Nested bracket pairs
    "[2001:db8::1]extra", // Unexpected text after the closing bracket
    "[2001:db8::1]8080", // Missing port separator
    "[192.0.2.1]", // Bracketed IPv4
    "[192.0.2.1]:8080", // Bracketed IPv4 endpoint
    "http://[192.0.2.1]/path", // Bracketed IPv4 URL host
    "2001:db8::1%eth[0", // Stray opening bracket in an opaque zone
    "2001:db8::1%eth0]", // Stray closing bracket in an opaque zone
    "192.0.2.1:", // Empty port
    "192.0.2.1:bad", // Nonnumeric port
    "192.0.2.1:-1", // Negative port
    "192.0.2.1:+80", // Signed port
    "192.0.2.1:８０", // Non-ASCII port digits
    "192.0.2.1:65536", // Port exceeds 65535
    "192.0.2.1:000080", // Port exceeds five digits despite its value fitting the range
    "192.0.2.1:99999999999999999999", // Port exceeds five digits
    "[2001:db8::1]:", // Empty port
    "[2001:db8::1]:-1", // Negative port
    "[2001:db8::1]:65536", // Port exceeds 65535
    "[2001:db8::1]:000000", // Port exceeds five digits
    "http://[2001:db8::1]:path/instead/of/port", // Nonnumeric port
    "http://192.0.2.1:8080;https://192.0.2.2/other" // Limitation: semicolon is not a recognized delimiter
  })
  void shouldRejectMissingCandidateOrMalformedEndpoint(String content) {
    assertThat(IpAddressCandidateExtractor.extract(content)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource
  void shouldExtractAllCandidates(String content, List<String> expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> shouldExtractAllCandidates() {
    return Stream.of(
      // Path segments and surrounding text
      candidates("/registry/ipaddresses/192.168.2.3", "192.168.2.3"),
      candidates("registry/ipaddresses/192.168.2.3/status", "192.168.2.3"),
      candidates("connect to 192.0.2.1 now", "192.0.2.1"),
      candidates("Connect to 192.0.2.1.", "192.0.2.1"), // Full stop after the address
      candidates("Connect to ::1.", "::1"),
      candidates("host=192.0.2.1&port=80", "192.0.2.1"),
      candidates("host:192.0.2.1", "192.0.2.1"),
      candidates("dc:192.0.2.1", "192.0.2.1"), // Not an IPv6 address; the IPv4 address is still found
      candidates("12:30:192.0.2.1", "192.0.2.1"),
      candidates("1:2:3:4:5:6:7:192.0.2.1", "192.0.2.1"), // Too many groups for IPv6
      candidates("1:2.3.4.5", "2.3.4.5"),
      candidates("'192.0.2.1'", "192.0.2.1"),
      candidates("(192.0.2.1)", "192.0.2.1"),
      candidates("\"2001:db8::1\"", "2001:db8::1"),
      candidates(" 192.0.2.1", "192.0.2.1"), // Leading whitespace
      candidates("[192.0.2.1]", "192.0.2.1"),
      candidates("Server 10.0.0.1: connection refused", "10.0.0.1"),
      candidates("ip 192.0.2.1", "192.0.2.1"),
      // Lists with any delimiter
      candidates("192.0.2.1,192.0.2.2", "192.0.2.1", "192.0.2.2"),
      candidates("192.0.2.1;192.0.2.2", "192.0.2.1", "192.0.2.2"),
      candidates("192.0.2.1 | 2001:db8::1 | ::ffff:192.0.2.3", "192.0.2.1", "2001:db8::1", "::ffff:192.0.2.3"),
      candidates("[\"10.0.0.1\", \"10.0.0.2\", \"10.0.0.1\"]", "10.0.0.1", "10.0.0.2", "10.0.0.1"), // Duplicates are retained
      candidates("http://192.0.2.1:8080;https://192.0.2.2/other", "192.0.2.1", "192.0.2.2"),
      candidates("http://192.0.2.1,https://[2001:db8::1]:8443/path", "192.0.2.1", "2001:db8::1"),
      candidates("from 10.0.0.1 to 10.0.0.255", "10.0.0.1", "10.0.0.255"),
      // URLs: user information, paths, queries
      candidates("http://user:pass@192.0.2.1:8080/path", "192.0.2.1"),
      candidates("http://192.0.2.1/path@192.0.2.2", "192.0.2.1", "192.0.2.2"),
      candidates("http://example.org/?next=http://192.0.2.1/7", "192.0.2.1"),
      candidates("https://example.org/proxy/192.0.2.1/status?ip=198.51.100.1#203.0.113.1", "192.0.2.1", "198.51.100.1", "203.0.113.1"),
      candidates("mailto:admin@192.0.2.1", "192.0.2.1"),
      // Ports and brackets are removed
      candidates("192.0.2.1:8080", "192.0.2.1"),
      candidates("[2001:db8::1]:8080", "2001:db8::1"),
      candidates("http://[2001:db8::1%eth0]:8080/path", "2001:db8::1%eth0"),
      candidates("http://[fe80::1%25en0]/", "fe80::1%25en0"),
      candidates("2001:db8::1%", "2001:db8::1%"),
      // CIDR suffixes
      candidates("allow 10.0.0.0/8 and 2001:db8::/32", "10.0.0.0/8", "2001:db8::/32"),
      candidates("/registry/10.0.0.0/8", "10.0.0.0/8"),
      candidates("[2001:db8::1]/64", "2001:db8::1/64"),
      candidates("[2001:db8::1%接口]/64", "2001:db8::1%接口/64"),
      candidates("10.0.0.0/8.", "10.0.0.0/8"),
      candidates("192.0.2.1/33", "192.0.2.1/33"), // Out-of-range prefixes are preserved, like extract
      candidates("192.0.2.1/1000", "192.0.2.1"), // Overlong suffixes are paths
      candidates("192.0.2.1/24abc", "192.0.2.1"),
      candidates("192.0.2.1/３３", "192.0.2.1"), // Non-ASCII digits
      candidates("192.0.2.1/", "192.0.2.1"),
      candidates("192.0.2.1:8080/24", "192.0.2.1"), // Slash after a port starts a path
      candidates("[2001:db8::1]:8080/64", "2001:db8::1"),
      candidates("http://127.0.0.1/7", "127.0.0.1"), // URL hosts are followed by paths
      candidates("//[2001:db8::1]/64", "2001:db8::1"),
      candidates("http://user@192.0.2.1/24", "192.0.2.1"),
      // IPv6 shapes
      candidates("::", "::"),
      candidates("::00000:2", "::00000:2"),
      candidates("1:2:3:4:5:6:7:8", "1:2:3:4:5:6:7:8"),
      candidates("1:2:3:4:5:6:192.0.2.1", "1:2:3:4:5:6:192.0.2.1"),
      candidates("1:2:3:4:5:6:7::", "1:2:3:4:5:6:7::"),
      candidates("::FFFF:7f00:1", "::FFFF:7f00:1"),
      // Candidates are not validated
      candidates("999.2.3.4", "999.2.3.4"),
      candidates("01.02.03.04", "01.02.03.04"),
      candidates("1.0.0.0", "1.0.0.0") // Possibly a version number
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "no address here",
    "https://example.com/path",
    "v1.2.3.4", // Version prefix
    "1.2.3.4.5", // Too many groups
    "1.3.6.1.4.1.311", // OID
    "1.2.3", // Too few groups
    "1234.2.3.4", // Too many digits
    "192.0.2.1234",
    "prefix192.0.2.1",
    "192.0.2.1suffix",
    "host-192.0.2.1",
    "192.0.2.1-SNAPSHOT",
    "my_192.0.2.1",
    "接口192.0.2.1",
    "192.0.2.1接口",
    "http://192.0.2.1@example.org/path", // User information
    "user.192.0.2.1",
    // Product tokens
    "Chrome/120.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "(Agent/1.2.3.4)",
    "a,Agent_1.0/1.2.3.4",
    // Colon-separated tokens that are not IPv6 addresses
    "12:30:45",
    "00:1a:2b:3c:4d:5e", // MAC address
    "1:2:3:4:5:6:7:8:9",
    "1::2::3",
    ":::1",
    "1:2:3:4:5:6:7::8",
    "::ffff:1.2.3",
    "::ffff:192.0.2.1.5",
    ":1:2:3:4:5:6:7",
    "1:2:3:4:5:6:7:",
    "std::vector",
    "Foo::bar",
    "::std",
    "x2001:db8::1",
    "2001:db8::1x",
    "2001:db8::g"
  })
  void shouldExtractNoCandidates(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @Test
  void shouldReturnUnmodifiableList() {
    List<String> candidates = IpAddressCandidateExtractor.extractAll("192.0.2.1");
    assertThat(candidates).isUnmodifiable();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "192.0.2.1:65535",
    "[2001:db8::1]:65535",
    "http://user:pass@[2001:db8::1]:8080/path",
    "http://192.0.2.1,user:pass@192.0.2.2/path",
    "https://[2001:db8::192.0.2.1]:8080/path",
    "192.0.2.1/032",
    "2001:db8::1/128",
    "[::ffff:192.0.2.1]",
    "http://192.0.2.1?redirect=https://user@example.org/path?a=1&b=2",
    "192.0.2.1\u00a0rest",
    "[2001:db8::1%接口]/64"
  })
  void extractAllShouldIncludeParseableExtractResult(String content) {
    String extracted = IpAddressCandidateExtractor.extract(content).orElseThrow();
    assertThat(IpAddressClassifier.parse(extracted)).isPresent();
    assertThat(IpAddressCandidateExtractor.extractAll(content)).contains(extracted);
  }

  private static Arguments candidates(String content, String... expected) {
    return Arguments.of(content, Arrays.asList(expected));
  }
}
