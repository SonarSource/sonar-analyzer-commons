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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressCandidateExtractorTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    // Complete-string search, ordering, and repeated occurrences
    "/registry/ipaddresses/192.168.2.3 | 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.2 | 10.0.0.1 10.0.0.2",
    "10.0.0.1;2001:db8::1,10.0.0.1 | 10.0.0.1 2001:db8::1 10.0.0.1",
    "10.0.0.1\u00a010.0.0.2 | 10.0.0.1 10.0.0.2",
    "address=10.0.0.1 | 10.0.0.1",
    "{10.0.0.1}!~10.0.0.2 | 10.0.0.1 10.0.0.2",
    "http://example.org/path@10.0.0.1 | 10.0.0.1",
    "\"10.0.0.1\" (2001:db8::1) | 10.0.0.1 2001:db8::1",
    "'10.0.0.1' | 10.0.0.1",
    // Validate syntax, not security policy
    "0.0.0.0,127.0.0.1,192.0.2.1,8.8.8.8 | 0.0.0.0 127.0.0.1 192.0.2.1 8.8.8.8",
    "bad 999.0.0.1 10.0.0.1 host.example ::1 | 10.0.0.1 ::1",
    // Complete IPv6 addresses, spelling, and opaque zones
    ":: 2001:DB8:0:0:0:0:0:1 ::00000:2 | :: 2001:DB8:0:0:0:0:0:1 ::00000:2",
    "::ffff:192.0.2.1;2001:db8::192.0.2.2 | ::ffff:192.0.2.1 2001:db8::192.0.2.2",
    "fe80::1%eth0 fe80::2%接口 fe80::3% fe80::4%zone!name | fe80::1%eth0 fe80::2%接口 fe80::3% fe80::4%zone!name",
    "[fe80::1%25eth0]:80 | fe80::1%25eth0",
    // Endpoint ports are never stripped from unbracketed IPv6
    "10.0.0.1:0 10.0.0.2:65535 10.0.0.3:00080 | 10.0.0.1 10.0.0.2 10.0.0.3",
    "[2001:db8::1]:0 [::ffff:10.0.0.1]:65535 | 2001:db8::1 ::ffff:10.0.0.1",
    "2001:db8::1:80 | 2001:db8::1:80",
    // Search URL paths, queries, and fragments even with a named host
    "https://example.org/registry/192.168.2.3?q=10.0.0.1#2001:db8::1 | 192.168.2.3 10.0.0.1 2001:db8::1",
    "http://10.0.0.1:80/10.0.0.2?ip=10.0.0.3&other=10.0.0.4#10.0.0.5 | 10.0.0.1 10.0.0.2 10.0.0.3 10.0.0.4 10.0.0.5",
    "git+ssh://[2001:DB8::1]:80/path //10.0.0.1/path | 2001:DB8::1 10.0.0.1",
    "http://10.0.0.9:secret@example.org/10.0.0.1 | 10.0.0.1",
    "http://user:10.0.0.9@[2001:db8::1]/path | 2001:db8::1",
    "//10.0.0.9@example.org/path http://example.org/10.0.0.1 | 10.0.0.1",
    "http://10.0.0.1;https://10.0.0.2 http://10.0.0.1 | 10.0.0.1 10.0.0.2 10.0.0.1",
    // CIDR only on standalone entries, validated against the native address family
    "10.0.0.1/0 10.0.0.2/32 10.0.0.3/032 | 10.0.0.1/0 10.0.0.2/32 10.0.0.3/032",
    "2001:db8::1/128 [fe80::1%接口]/064 ::ffff:10.0.0.1/120 | 2001:db8::1/128 fe80::1%接口/064 ::ffff:10.0.0.1/120",
    "ip=10.0.0.1/24 | 10.0.0.1/24",
    "http://10.0.0.1/33 //example.org/2001:db8::1/129 | 10.0.0.1 2001:db8::1",
    "http://example.org?q=10.0.0.1/24#2001:db8::1/64 | 10.0.0.1 2001:db8::1",
    "/10.0.0.1/33 registry/2001:db8::1/129 | 10.0.0.1 2001:db8::1",
    "10.0.0.1/path 10.0.0.2:80/33 [2001:db8::1]:80/129 | 10.0.0.1 10.0.0.2 2001:db8::1",
    "/10.0.0.1/10.0.0.2/2001:db8::1 | 10.0.0.1 10.0.0.2 2001:db8::1"
  })
  void shouldExtractAllAddresses(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected.split(" "));
  }

  @Test
  void shouldExtractAddressesAcrossLines() {
    assertThat(IpAddressCandidateExtractor.extractAll("  10.0.0.1\t10.0.0.2\n10.0.0.1"))
      .containsExactly("10.0.0.1", "10.0.0.2", "10.0.0.1");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "ordinary text", "example.org", "http://example.org/path",
    "http://10.0.0.1@example.org/path", "http://user:10.0.0.1@example.org",
    "scheme1.2.3.4://example.org", "custom-scheme.v1://example.org",
    // Never salvage a shorter valid address from an invalid address or identifier
    "version1.2.3.4", "1.2.3.4.5", ".1.2.3.4", "1.2.3.4.",
    "999.1.2.3.4", "010.0.0.1", "10.0.0.01", "10.0.0.1-host", "host-10.0.0.1",
    "10.0.0.1_name", "name_10.0.0.1", "接口10.0.0.1", "10.0.0.1接口",
    "gggg::10.0.0.1", "1:2:3:4:5:6:7:8:9", "2001:db8:::1", "::ffff:010.0.0.1",
    "10.0.0.1%eth0",
    // Invalid standalone CIDR entries, including overflow, signs, and non-ASCII digits
    "10.0.0.1/33", "10.0.0.1/1000", "10.0.0.1/99999999999999999999",
    "10.0.0.1/-1", "10.0.0.1/+24", "10.0.0.1/２４", "10.0.0.1/24x", "10.0.0.1/24.", "10.0.0.1/24.5",
    "2001:db8::1/129", "[2001:db8::1]/129", "::ffff:10.0.0.1/129",
    // Invalid endpoints suppress the entire endpoint, including any embedded IPv4 tail
    "[10.0.0.1]", "[10.0.0.1]:80", "[2001:db8::1", "2001:db8::1]",
    "[[2001:db8::1]]", "[2001:db8::1]extra", "[2001:db8::1]8080",
    "[::ffff:10.0.0.1]:bad", "[2001:db8::1]:", "[2001:db8::1]:65536",
    "10.0.0.1:", "10.0.0.1:bad", "10.0.0.1:-1", "10.0.0.1:+80", "10.0.0.1:８０",
    "10.0.0.1:65536", "10.0.0.1:000080", "10.0.0.1:99999999999999999999"
  })
  void shouldNotExtractInvalidAddressesOrPartialMatches(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]", "[2001:db8::1]extra",
    "[10.0.0.1]:80", "[::ffff:10.0.0.1]:bad", "10.0.0.1:65536", "10.0.0.1/33",
    "http://[2001:db8::1]:bad", "http://10.0.0.1:bad/path"
  })
  void malformedEntriesShouldNotHideLaterAddresses(String malformed) {
    assertThat(IpAddressCandidateExtractor.extractAll(malformed + " 10.0.0.2;[2001:db8::2]"))
      .containsExactly("10.0.0.2", "2001:db8::2");
  }

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
}
