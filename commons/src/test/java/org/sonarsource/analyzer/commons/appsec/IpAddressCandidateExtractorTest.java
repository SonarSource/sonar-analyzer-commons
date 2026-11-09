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

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
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
  @MethodSource("allCandidates")
  void shouldExtractAllCandidates(String content, List<String> expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> allCandidates() {
    return Stream.of(
      // Problem examples
      of("/registry/ipaddresses/192.168.2.3", "192.168.2.3"),
      of("connect to 10.0.0.1, 10.0.0.2", "10.0.0.1", "10.0.0.2"),
      // Bare values
      of("192.0.2.1", "192.0.2.1"),
      of("  \t192.0.2.1\n", "192.0.2.1"),
      of("2001:db8::1", "2001:db8::1"),
      of("::", "::"),
      of("::ffff:192.0.2.1", "::ffff:192.0.2.1"),
      of("2001:db8::192.0.2.1", "2001:db8::192.0.2.1"),
      of("fe80::1%eth0", "fe80::1%eth0"),
      of("fe80::1%", "fe80::1%"),
      of("2001:db8::1:80", "2001:db8::1:80"),
      // Lists, in order and with duplicates
      of("1.1.1.1 2.2.2.2 1.1.1.1", "1.1.1.1", "2.2.2.2", "1.1.1.1"),
      of("1.1.1.1,2.2.2.2;3.3.3.3, ::1", "1.1.1.1", "2.2.2.2", "3.3.3.3", "::1"),
      of("a=1.1.1.1&b=2.2.2.2", "1.1.1.1", "2.2.2.2"),
      of("dns = 8.8.8.8", "8.8.8.8"),
      of("--host=10.0.0.1", "10.0.0.1"),
      of("'10.0.0.1' \"10.0.0.2\" (10.0.0.3)", "10.0.0.1", "10.0.0.2", "10.0.0.3"),
      of("ip(10.0.0.1)", "10.0.0.1"),
      of("<10.0.0.1>", "10.0.0.1"),
      // Delimiters
      of("/a/10.0.0.1/b/::1/c", "10.0.0.1", "::1"),
      of("a/10.0.0.1:8080/b", "10.0.0.1"),
      // Endpoints
      of("10.0.0.1:8080", "10.0.0.1"),
      of("10.0.0.1:0 10.0.0.2:65535 10.0.0.3:00080", "10.0.0.1", "10.0.0.2", "10.0.0.3"),
      of("[2001:db8::1]:8080", "2001:db8::1"),
      of("[2001:db8::1]", "2001:db8::1"),
      of("[fe80::1%eth0]:80", "fe80::1%eth0"),
      of("[::ffff:192.0.2.1]:80", "::ffff:192.0.2.1"),
      // CIDR
      of("10.0.0.0/8", "10.0.0.0/8"),
      of("10.0.0.1/32 10.0.0.0/0", "10.0.0.1/32", "10.0.0.0/0"),
      of("2001:db8::/32", "2001:db8::/32"),
      of("2001:db8::/128", "2001:db8::/128"),
      of("[2001:db8::]/32", "2001:db8::/32"),
      of("fe80::1%eth0/64", "fe80::1%eth0/64"),
      of("allow=10.0.0.0/8,192.168.0.0/16", "10.0.0.0/8", "192.168.0.0/16"),
      of("10.0.0.0/33"),
      of("10.0.0.0/99999"),
      of("2001:db8::/129"),
      of("10.0.0.0/-1", "10.0.0.0"),
      of("10.0.0.0/", "10.0.0.0"),
      of("[::1]:80/129", "::1"),
      of("10.0.0.1:80/24", "10.0.0.1"),
      of("10.0.0.1/path", "10.0.0.1"),
      of("10.0.0.1/24/path", "10.0.0.1"),
      of("/10.0.0.1/24", "10.0.0.1"),
      of("10.0.0.1/24.", "10.0.0.1"),
      // URLs
      of("http://192.0.2.1", "192.0.2.1"),
      of("http://192.0.2.1:8080/path", "192.0.2.1"),
      of("http://user:pass@192.0.2.1/path", "192.0.2.1"),
      of("http://::1@192.0.2.1/path", "192.0.2.1"),
      of("http://::1@example.org/path"),
      of("http://user@example.org/10.0.0.1/path", "10.0.0.1"),
      of("//[2001:db8::1]:8080/path", "2001:db8::1"),
      of("https://[2001:db8::1]/", "2001:db8::1"),
      of("http://example.org/10.0.0.1", "10.0.0.1"),
      of("http://example.org/a?ip=10.0.0.1&other=10.0.0.2#10.0.0.3", "10.0.0.1", "10.0.0.2", "10.0.0.3"),
      of("http://192.0.2.1/10.0.0.0/8", "192.0.2.1", "10.0.0.0"),
      of("http://192.0.2.1?ip=10.0.0.0/8", "192.0.2.1", "10.0.0.0"),
      of("/api?ip=10.0.0.0/8#frag", "10.0.0.0"),
      of("http://192.0.2.1/path@192.0.2.2", "192.0.2.1", "192.0.2.2"),
      of("http://10.0.0.1,https://10.0.0.2 ftp://10.0.0.3", "10.0.0.1", "10.0.0.2", "10.0.0.3"),
      of("see <http://10.0.0.1/>.", "10.0.0.1"),
      of("redirect=http://user@10.0.0.1:80/", "10.0.0.1"),
      of("git+ssh://10.0.0.1/path", "10.0.0.1"),
      of("http://localhost:8080/a"),
      of("http://"),
      // Malformed endpoints and brackets do not hide a later address
      of("[10.0.0.1 10.0.0.2", "10.0.0.2"),
      of("[2001:db8::1 10.0.0.2", "10.0.0.2"),
      of("10.0.0.1:99999 10.0.0.2", "10.0.0.2"),
      of("[::1]:99999,10.0.0.2", "10.0.0.2"),
      of("::1] 10.0.0.2", "10.0.0.2"),
      of("[[::1]] 10.0.0.2", "10.0.0.2"));
  }

  private static Arguments of(String content, String... expected) {
    return Arguments.of(content, List.of(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    " ",
    "no address here",
    "example.org",
    "http://example.org/path",
    "1.2.3",
    "1.2.3.4.5",
    "1.2.3.4.",
    "see 1.2.3.4.",
    ".1.2.3.4",
    "..1.2.3.4",
    "1.2.3.4..5",
    "v1.2.3.4",
    "a1.2.3.4",
    "1.2.3.4a",
    "1.2.3.4-beta",
    "1.2.3.4_x",
    "x-1.2.3.4",
    "x_1.2.3.4",
    "11.2.3.4.5.6",
    "256.1.1.1",
    "01.2.3.4",
    "1.2.3.04",
    "1.2.3.4:",
    "1.2.3.4:80:90",
    "1.2.3.4:99999",
    "1.2.3.4:65536",
    "1.2.3.4:000080",
    "1.2.3.4:-1",
    "1.2.3.4:８０",
    "[1.2.3.4]",
    "[1.2.3.4]:80",
    "[1.2.3.4, 5.6.7.8]",
    "[::1]:",
    "[::1]:99999",
    "[::1]80",
    "[::1]x",
    "::1.",
    "::1x",
    "12:30:45",
    "2001:db8::1::2",
    "1:2:3:4:5:6:7:8:9",
    "１.２.３.４",
    "é1.2.3.4",
    "1.2.3.4é",
    "1.2.3.4/33",
    "::1/129"
  })
  void shouldNotExtractAnyCandidate(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "'connect to 10.0.0.1, 10.0.0.2', connect",
    "192.0.2.1 10.0.0.2, 192.0.2.1"
  })
  void shouldKeepSingleCandidateResultsUnchanged(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extract(content)).contains(expected);
  }

  @Test
  void shouldKeepSingleCandidateEmptyForPath() {
    assertThat(IpAddressCandidateExtractor.extract("/registry/ipaddresses/192.168.2.3")).isEmpty();
  }
}
