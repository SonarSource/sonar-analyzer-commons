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

class IpAddressCandidateExtractorTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    // Complete content, not just the start; preserve order and repeated occurrences.
    "/registry/ipaddresses/192.168.2.3 | 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.2 | 10.0.0.1 10.0.0.2",
    "' 192.0.2.1' | 192.0.2.1",
    "'192.0.2.1\u00a0198.51.100.2' | 192.0.2.1 198.51.100.2",
    "192.0.2.1,198.51.100.2;192.0.2.1 | 192.0.2.1 198.51.100.2 192.0.2.1",
    "ip=192.0.2.1 | 192.0.2.1",
    "\"192.0.2.1\" (2001:db8::1) | 192.0.2.1 2001:db8::1",
    "'address=192.0.2.1' | 192.0.2.1",
    // Validate syntax without applying security-rule exemptions or normalizing spelling.
    "0.0.0.0 127.0.0.1 192.0.2.1 8.8.8.8 | 0.0.0.0 127.0.0.1 192.0.2.1 8.8.8.8",
    "2001:DB8:0:0:0:0:0:1 | 2001:DB8:0:0:0:0:0:1",
    "::00000:2 | ::00000:2",
    ":: ::1 | :: ::1",
    "::ffff:192.0.2.1 | ::ffff:192.0.2.1",
    "2001:db8::192.0.2.1 | 2001:db8::192.0.2.1",
    "fe80::1%eth0 fe80::2%接口 fe80::3% | fe80::1%eth0 fe80::2%接口 fe80::3%",
    "[fe80::1%eth_0]:80 | fe80::1%eth_0",
    // Only IPv4 and bracketed IPv6 have a separate port.
    "192.0.2.1:0 192.0.2.1:65535 192.0.2.1:00080 | 192.0.2.1 192.0.2.1 192.0.2.1",
    "[2001:db8::1]:0 [2001:db8::2]:65535 | 2001:db8::1 2001:db8::2",
    "[::ffff:192.0.2.1]:8080 | ::ffff:192.0.2.1",
    "2001:db8::1:8080 | 2001:db8::1:8080",
    // Search all URL components except the scheme and authority user information.
    "http://192.0.2.1:80/198.51.100.2?ip=203.0.113.3#2001:db8::1 | 192.0.2.1 198.51.100.2 203.0.113.3 2001:db8::1",
    "http://example.org/192.0.2.1?ip=198.51.100.2#203.0.113.3 | 192.0.2.1 198.51.100.2 203.0.113.3",
    "url=https://[2001:DB8::1]:443/path | 2001:DB8::1",
    "custom-scheme.v1://192.0.2.1/path | 192.0.2.1",
    "//[fe80::1%25eth0]:80/192.0.2.1 | fe80::1%25eth0 192.0.2.1",
    "http://192.0.2.1@198.51.100.2/path | 198.51.100.2",
    "http://192.0.2.1:secret@[2001:db8::1]/path | 2001:db8::1",
    "http://192.0.2.1,secret@example.org/198.51.100.2 | 198.51.100.2",
    "http://192.0.2.1;secret@example.org/198.51.100.2 | 198.51.100.2",
    "http://192.0.2.1/path@198.51.100.2?ip=203.0.113.3 | 192.0.2.1 198.51.100.2 203.0.113.3",
    "http://example.org?redirect=http://192.0.2.1@198.51.100.2/path | 198.51.100.2",
    "http://192.0.2.1,http://198.51.100.2;https://[2001:db8::1] | 192.0.2.1 198.51.100.2 2001:db8::1",
    // Standalone CIDRs, including value entries, retain their exact valid prefix.
    "192.0.2.1/0 192.0.2.1/32 192.0.2.1/032 | 192.0.2.1/0 192.0.2.1/32 192.0.2.1/032",
    "2001:db8::1/128 [2001:db8::2]/064 | 2001:db8::1/128 2001:db8::2/064",
    "::ffff:192.0.2.1/128 | ::ffff:192.0.2.1/128",
    "ip=192.0.2.1/24 | 192.0.2.1/24",
    "[fe80::1%接口]/64 | fe80::1%接口/64",
    // Slashes in URLs and path entries are never CIDR prefixes.
    "ipaddresses/192.168.2.3 | 192.168.2.3",
    "registry/192.0.2.1 | 192.0.2.1",
    "example.org/192.0.2.1 | 192.0.2.1",
    "localhost/2001:db8::1 | 2001:db8::1",
    "192.0.2.1/198.51.100.2 | 192.0.2.1 198.51.100.2",
    "192.0.2.1/2001:db8::1 | 192.0.2.1 2001:db8::1",
    "192.0.2.1/198.51.100.2:80 | 192.0.2.1 198.51.100.2",
    "http://192.0.2.1/33 | 192.0.2.1",
    "http://example.org?ip=192.0.2.1/33 | 192.0.2.1",
    "http://example.org/192.0.2.1/24 | 192.0.2.1",
    "/registry/192.0.2.1/33 | 192.0.2.1",
    "prefix/192.0.2.1/24/198.51.100.2 | 192.0.2.1 198.51.100.2",
    "192.0.2.1/path/198.51.100.2 | 192.0.2.1 198.51.100.2",
    "192.0.2.1/path | 192.0.2.1",
    "192.0.2.1/ | 192.0.2.1",
    "192.0.2.1:8080/33 [2001:db8::1]:80/129 | 192.0.2.1 2001:db8::1"
  })
  void shouldExtractAllAddresses(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected.split(" "));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "ordinary text", "example.org", "example.org/24", "999.0.2.1/24", "1.2.3", "999.0.2.1", "010.0.0.1", "192.0.2.01",
    // Never recover a shorter address from an identifier, version, or malformed literal.
    "prefix192.0.2.1", "192.0.2.1suffix", "x-192.0.2.1", "192.0.2.1-x", "x_192.0.2.1", "192.0.2.1_x",
    "接口192.0.2.1", "192.0.2.1接口", "1192.0.2.1", "192.0.2.1234",
    "1.192.0.2.1", "192.0.2.1.2", ".192.0.2.1", "192.0.2.1.", "v1.2.3.4",
    ":::192.0.2.1", "invalid::192.0.2.1", "::ffff:010.0.0.1", "2001:db8:::1", "2001:db8::1.",
    // Ports: ASCII, unsigned, at most five digits and within range; no fallback to host or dotted tail.
    "192.0.2.1:", "192.0.2.1:bad", "192.0.2.1:-1", "192.0.2.1:+80", "192.0.2.1:８０",
    "192.0.2.1:65536", "192.0.2.1:000080", "192.0.2.1:99999999999999999999", "192.0.2.1:80:90",
    "192.0.2.1:80foo", "192.0.2.1:80.",
    "[2001:db8::1]:", "[2001:db8::1]:bad", "[2001:db8::1]:-1", "[2001:db8::1]:+80", "[2001:db8::1]:８０",
    "[2001:db8::1]:65536", "[2001:db8::1]:000080", "[::ffff:192.0.2.1]:65536",
    // Brackets are endpoint syntax only for IPv6 and must be balanced and unnested.
    "[192.0.2.1]", "[192.0.2.1]:80", "[192.0.2.1:80]", "[2001:db8::1", "2001:db8::1]",
    "[[2001:db8::1]]", "[2001:db8::1]extra", "[2001:db8::1]8080", "[2001:db8::1].",
    "fe80::1%eth[0", "fe80::1%eth0]",
    // Invalid standalone CIDRs invalidate the whole entry, not just its suffix.
    "192.0.2.1/33", "192.0.2.1/1000", "192.0.2.1/-1", "192.0.2.1/+24", "192.0.2.1/２４",
    "192.0.2.1/𝟚𝟜", "192.0.2.1/24x", "192.0.2.1/24.", "192.0.2.1/99999999999999999999999999",
    "2001:db8::1/129", "[2001:db8::1]/129", "::ffff:192.0.2.1/129",
    // Exclude user information and host names, even when an address appears inside them.
    "http://192.0.2.1@example.org/path", "http://host192.0.2.1/path", "http://192.0.2.1.example.org/path",
    "http://192.0.2.1:bad/path", "http://[2001:db8::1]:65536/path"
  })
  void shouldNotExtractPartialAddressesAndShouldRecoverAtNextEntry(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
    for (String delimiter : new String[] {" ", ",", ";", "\n", "\u00a0"}) {
      assertThat(IpAddressCandidateExtractor.extractAll(content + delimiter + "198.51.100.2"))
        .as("recovery after %s with delimiter %s", content, delimiter)
        .containsExactly("198.51.100.2");
    }
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
