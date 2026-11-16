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

  @Test
  void shouldHandleLongNonAddressEntries() {
    assertThat(IpAddressCandidateExtractor.extractAll("a".repeat(20_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?q=".repeat(2_000))).isEmpty();
  }

  @Test
  void shouldFindQuotedAndWhitespaceDelimitedValuesWithoutChangingLegacyExtraction() {
    String content = " \t\n'10.0.0.1' (\"[2001:db8::1]:80\")\u00a010.0.0.1 ";
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly("10.0.0.1", "2001:db8::1", "10.0.0.1");
    assertThat(IpAddressCandidateExtractor.extract(content)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "/registry/ipaddresses/192.168.2.3 | 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.2 | 10.0.0.1;10.0.0.2",
    "  10.0.0.1\t10.0.0.2;10.0.0.1 | 10.0.0.1;10.0.0.2;10.0.0.1",
    "10.0.0.1\u00a010.0.0.2 | 10.0.0.1;10.0.0.2",
    "ip=10.0.0.1 | 10.0.0.1",
    "ip=\"10.0.0.1\" | 10.0.0.1",
    "(10.0.0.1);(2001:db8::1) | 10.0.0.1;2001:db8::1",
    "10.0.0.1/0,10.0.0.2/32 | 10.0.0.1/0;10.0.0.2/32",
    "network=10.0.0.1/032 | 10.0.0.1/032",
    "10.0.0.1/00032 | 10.0.0.1/00032",
    "2001:db8::1/0;[2001:db8::2]/128 | 2001:db8::1/0;2001:db8::2/128",
    "2001:db8::1/064 | 2001:db8::1/064",
    "2001:DB8:0000::AbCd | 2001:DB8:0000::AbCd",
    "::00000:2 | ::00000:2",
    "::;::1 | ::;::1",
    "::ffff:192.0.2.1 | ::ffff:192.0.2.1",
    "2001:db8::192.0.2.1 | 2001:db8::192.0.2.1",
    "[::FFFF:192.0.2.1]:443 | ::FFFF:192.0.2.1",
    "fe80::1%eth0 | fe80::1%eth0",
    "[fe80::1%接口]:80 | fe80::1%接口",
    "fe80::1%3/64 | fe80::1%3/64",
    "fe80::1% | fe80::1%",
    "[fe80::1%eth0.]:80 | fe80::1%eth0.",
    "fe80::1%eth0./64 | fe80::1%eth0./64",
    "[fe80::1%eth-0]/64 | fe80::1%eth-0/64",
    "10.0.0.1:0;10.0.0.2:65535;10.0.0.3:00080 | 10.0.0.1;10.0.0.2;10.0.0.3",
    "[2001:db8::1]:0;[2001:db8::2]:65535 | 2001:db8::1;2001:db8::2",
    "2001:db8::1:8080 | 2001:db8::1:8080",
    "http://10.0.0.1:8080/path | 10.0.0.1",
    "visit https://example.org/10.0.0.1 | 10.0.0.1",
    "git+ssh://[2001:db8::1]:22/10.0.0.1 | 2001:db8::1;10.0.0.1",
    "custom-scheme.v1://10.0.0.1/33 | 10.0.0.1",
    "//[2001:db8::1]/129 | 2001:db8::1",
    "http://10.0.0.1/10.0.0.2?ip=10.0.0.3#10.0.0.4 | 10.0.0.1;10.0.0.2;10.0.0.3;10.0.0.4",
    "http://example.org?ip=10.0.0.1&peer=[2001:db8::1]:80#10.0.0.2 | 10.0.0.1;2001:db8::1;10.0.0.2",
    "http://example.org?ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/logs//10.0.0.1@tag | 10.0.0.1",
    "http://example.org/logs/(//10.0.0.1@tag) | 10.0.0.1",
    "http://example.org/logs/key=//10.0.0.1@tag | 10.0.0.1",
    "http://example.org/logs/key&//10.0.0.1@tag | 10.0.0.1",
    "http://example.org#key=//10.0.0.1@tag | 10.0.0.1",
    "http://example.org#part?ip=//10.0.0.1@tag | 10.0.0.1",
    "http://example.org#part??ip=//10.0.0.1@tag&peer=10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://example.org?next=http://example.com/path#part?ip=//10.0.0.1@tag | 10.0.0.1",
    "folder/key=10.0.0.1/24 | 10.0.0.1",
    "http://example.org?next=(//10.0.0.9@example.com/10.0.0.1) | 10.0.0.1",
    "http://example.org?first=http://10.0.0.1/path&second=http://10.0.0.9@example.com/10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://example.org?first=http://10.0.0.1&second=http://10.0.0.9@example.com/10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://example.org?first=//10.0.0.1/path&second=//10.0.0.9@example.com/10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://example.org?next=http://example.com/key=//10.0.0.1@tag | 10.0.0.1",
    "http://example.org?next=http://example.com#key=//10.0.0.1@tag | 10.0.0.1",
    "(http://10.0.0.1) | 10.0.0.1",
    "((http://[2001:db8::1]:80)) | 2001:db8::1",
    "ip=('http://10.0.0.1') | 10.0.0.1",
    "http://example.org?next=//10.0.0.9@example.com/10.0.0.1 | 10.0.0.1",
    "http://example.org/(10.0.0.1/33) | 10.0.0.1",
    "http://example.org/(10.0.0.1/24) | 10.0.0.1",
    "http://example.org/'10.0.0.1/24' | 10.0.0.1",
    "/folder/(10.0.0.1/33) | 10.0.0.1",
    "http://10.0.0.9:secret@10.0.0.1/10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://10.0.0.9,secret@10.0.0.1/path | 10.0.0.1",
    "http://10.0.0.9;secret@10.0.0.1/path | 10.0.0.1",
    "http://10.0.0.9@example.org/10.0.0.1 | 10.0.0.1",
    "http://10.0.0.9(foo)@example.org/10.0.0.1 | 10.0.0.1",
    "http://10.0.0.9(foo)@10.0.0.1/33 | 10.0.0.1",
    "http://name'10.0.0.9@example.org/10.0.0.1 | 10.0.0.1",
    "http://example.org/path@10.0.0.1?email=user@10.0.0.2 | 10.0.0.1;10.0.0.2",
    "http://10.0.0.1,http://10.0.0.2;https://example.org/10.0.0.3 | 10.0.0.1;10.0.0.2;10.0.0.3",
    "http://example.org?redirect=http://10.0.0.9@example.com/10.0.0.1 | 10.0.0.1",
    "/10.0.0.1/33 | 10.0.0.1",
    "folder/10.0.0.1/24 | 10.0.0.1",
    "10.0.0.1:80/33 | 10.0.0.1",
    "[2001:db8::1]:80/129 | 2001:db8::1",
    "[2001:db8::1 10.0.0.1 | 10.0.0.1",
    "[2001:db8::1]:bad,10.0.0.1 | 10.0.0.1",
    "10.0.0.9:65536;10.0.0.1 | 10.0.0.1",
    "http://[bad/10.0.0.1 | 10.0.0.1",
    "10.0.0.9/33 10.0.0.1 | 10.0.0.1",
    "8.8.8.8 127.0.0.1 224.0.0.1 | 8.8.8.8;127.0.0.1;224.0.0.1"
  })
  void shouldExtractAllAddresses(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected.split(";"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "example.org", "no addresses here", "http://example.org/path?name=localhost",
    "999.2.3.4", "256.0.0.1", "192.00.2.1", "127.1", "0x7f000001",
    "1.2.3.4.5", "v1.2.3.4", "1.2.3.4.", ".1.2.3.4", "11.2.3.456",
    "prefix192.0.2.1", "192.0.2.1suffix", "192.0.2.1-foo", "foo-192.0.2.1",
    "192.0.2.1_foo", "foo_192.0.2.1", "é192.0.2.1", "192.0.2.1接口", "١192.0.2.1",
    "2001:db8::1suffix", "prefix2001:db8::1", "2001:db8::1.", "2001:db8::1-foo",
    "fe80::1%eth0.", "::ffff:192.0.2.1%zone.",
    "2001:db8:::1", "2001:db8::1::2", "1:2:3:4:5:6:7:8:9", "::ffff:999.2.3.4",
    "::ffff:192.00.2.1", "::ffff:192.0.2.1.5", "bad::ffff:192.0.2.1:80",
    "[192.0.2.1]", "[192.0.2.1]:80", "[192.0.2.1:8080]",
    "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]", "[2001:db8::1]extra",
    "[2001:db8::1]8080", "2001:db8::1%eth[0", "2001:db8::1%eth0]",
    "192.0.2.1:", "192.0.2.1:bad", "192.0.2.1:-1", "192.0.2.1:+80", "192.0.2.1:８０",
    "192.0.2.1:65536", "192.0.2.1:000080", "192.0.2.1:99999999999999999999",
    "[2001:db8::1]:", "[2001:db8::1]:bad", "[2001:db8::1]:-1", "[2001:db8::1]:+80",
    "[2001:db8::1]:８０", "[2001:db8::1]:65536", "[2001:db8::1]:000080",
    "192.0.2.1/33", "192.0.2.1/100", "192.0.2.1/", "192.0.2.1/-1", "192.0.2.1/+24",
    "192.0.2.1/２４", "192.0.2.1/24x", "192.0.2.1/not-a-prefix",
    "192.0.2.1/99999999999999999999", "2001:db8::1/129", "[2001:db8::1]/129",
    "2001:db8::1/", "2001:db8::1/-1", "2001:db8::1/+64", "2001:db8::1/６４",
    "http://192.0.2.1@example.org", "http://[2001:db8::1]@example.org",
    "http://192.0.2.1:65536/path", "http://[2001:db8::1]:bad/path",
    "http://10.0.0.1(foo)/", "http://[2001:db8::1]:80(foo)/"
  })
  void shouldRejectInvalidAddressesWithoutHidingLaterEntries(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll(content + " 198.51.100.1")).containsExactly("198.51.100.1");
    assertThat(IpAddressCandidateExtractor.extractAll(content + ",198.51.100.1")).containsExactly("198.51.100.1");
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
