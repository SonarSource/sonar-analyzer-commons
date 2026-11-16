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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

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

  private static Arguments found(String content, String... expected) {
    return Arguments.of(content, List.of(expected));
  }

  static Stream<Arguments> addressesFound() {
    return Stream.of(
      // Addresses beyond the start of the content
      found("/registry/ipaddresses/192.168.2.3", "192.168.2.3"),
      found("connect to 10.0.0.1, 10.0.0.2", "10.0.0.1", "10.0.0.2"),
      found("   192.0.2.1", "192.0.2.1"),
      found("ip=192.0.2.1", "192.0.2.1"),
      found("\"192.0.2.1\"", "192.0.2.1"),
      found("'192.0.2.1'", "192.0.2.1"),
      found("(192.0.2.1)", "192.0.2.1"),
      found("a=1.1.1.1;b=2.2.2.2", "1.1.1.1", "2.2.2.2"),
      found("192.0.2.1 192.0.2.2\t192.0.2.1;192.0.2.3,192.0.2.1", "192.0.2.1", "192.0.2.2", "192.0.2.1", "192.0.2.3", "192.0.2.1"),
      found("/a/192.0.2.1/b/192.0.2.2", "192.0.2.1", "192.0.2.2"),
      // IPv6, embedded IPv4 and zones are single matches
      found("addr 2001:db8::1 end", "2001:db8::1"),
      found("::1", "::1"),
      found("::ffff:192.0.2.1", "::ffff:192.0.2.1"),
      found("2001:db8::192.0.2.1,2001:db8::2", "2001:db8::192.0.2.1", "2001:db8::2"),
      found("fe80::1%eth0 fe80::2%", "fe80::1%eth0", "fe80::2%"),
      found("[fe80::1%接口]:80", "fe80::1%接口"),
      found("fe80::1%\uD835\uDC00 192.0.2.1", "fe80::1%\uD835\uDC00", "192.0.2.1"),
      found("\uD835\uDC00 192.0.2.1 \uD835\uDFCE", "192.0.2.1"),
      found("2001:db8::1%eth[0 192.0.2.1", "192.0.2.1"),
      // Zones are opaque: punctuation inside one does not split the address
      found("fe80::1%eth0|10.0.0.1", "fe80::1%eth0|10.0.0.1"),
      found("http://x?ip=fe80::1%eth0|10.0.0.1&y=192.0.2.1", "fe80::1%eth0|10.0.0.1", "192.0.2.1"),
      found("fe80::1%eth0|x,192.0.2.1", "fe80::1%eth0|x", "192.0.2.1"),
      found("192.0.2.1|192.0.2.2 192.0.2.3%20|192.0.2.4", "192.0.2.1", "192.0.2.2", "192.0.2.4"),
      found("http://example.org/a%20|192.0.2.1", "192.0.2.1"),
      found("fe80::1%eth0/|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      // A closing bracket ends the zone, so punctuation after it separates entries
      found("[fe80::1%eth0]|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      found("[fe80::1%eth0]:8080|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      found("http://[fe80::1%eth0]|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      found("http://x?ip=[fe80::1%eth0]:80|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      found("[fe80::1%eth0|x]|192.0.2.1", "fe80::1%eth0|x", "192.0.2.1"),
      found("fe80::1%eth0+x 192.0.2.1", "fe80::1%eth0+x", "192.0.2.1"),
      // Only a percent sign after an IPv6 address starts a zone
      found("load:50%|192.0.2.1", "192.0.2.1"),
      found("cpu:90%|192.0.2.1,mem:10%|192.0.2.2", "192.0.2.1", "192.0.2.2"),
      found("abc:def:ghi%|192.0.2.1", "192.0.2.1"),
      found("1:2:3:4:5:6:7:8:9%|192.0.2.1", "192.0.2.1"),
      found("192.0.2.1:80%|192.0.2.2", "192.0.2.2"),
      found("http://192.0.2.1:80%|192.0.2.2", "192.0.2.2"),
      found("http://example.org:80/a%|192.0.2.1", "192.0.2.1"),
      found("http://user:pass@[fe80::1%eth0]|192.0.2.1", "fe80::1%eth0", "192.0.2.1"),
      found("fe80::1%a|b%c|192.0.2.1", "fe80::1%a|b%c|192.0.2.1"),
      found("[fe80::1%a|b]:80", "fe80::1%a|b"),
      // An at sign inside a zone belongs to the zone; before it, it ends user information
      found("fe80::1%eth0@x|192.0.2.1 192.0.2.2", "fe80::1%eth0@x|192.0.2.1", "192.0.2.2"),
      found("[fe80::1%a@b|c]", "fe80::1%a@b|c"),
      found("[fe80::1%a@b|c]:80|192.0.2.1", "fe80::1%a@b|c", "192.0.2.1"),
      found("http://user@[fe80::1%a|b]/x", "fe80::1%a|b"),
      // User information is separate from a host that can contain an at sign in its zone
      found("http://[fe80::1%a@b]/x", "fe80::1%a@b"),
      found("http://[fe80::1%a@b]:8080/x", "fe80::1%a@b"),
      found("http://user:pass@[fe80::1%a@b]:8080/x", "fe80::1%a@b"),
      found("http://u@v@[fe80::1%a@b]/x", "fe80::1%a@b"),
      found("//u@[fe80::1%a@b]", "fe80::1%a@b"),
      found("http://u@v@192.0.2.1:80/x", "192.0.2.1"),
      found("http://u[x@192.0.2.1/x", "192.0.2.1"),
      found("http://x?r=//u@[fe80::1%a@b]/y", "fe80::1%a@b"),
      found("http://[fe80::1%a@b/x 192.0.2.1", "192.0.2.1"),
      found("http://[user@192.0.2.1/x", "192.0.2.1"),
      found("http://u@[x@192.0.2.1:80/x", "192.0.2.1"),
      found("http://[fe80::1%a@b/x"),
      found("http://u@[fe80::1%a@b 192.0.2.1", "192.0.2.1"),
      found("http://u@[fe80::1%a@b]x/y/192.0.2.1", "192.0.2.1"),
      // Scheme-like runs
      found("a+b+c://192.0.2.1/x", "192.0.2.1"),
      found("1+a://192.0.2.1", "192.0.2.1"),
      found("1+1+192.0.2.1 192.0.2.2", "192.0.2.2"),
      // Endpoints
      found("192.0.2.1:8080", "192.0.2.1"),
      found("192.0.2.1:0 192.0.2.2:65535 192.0.2.3:00080", "192.0.2.1", "192.0.2.2", "192.0.2.3"),
      found("[2001:db8::1]:8080", "2001:db8::1"),
      found("[2001:db8::1]", "2001:db8::1"),
      found("2001:db8::1:8080", "2001:db8::1:8080"),
      found("x/192.0.2.1:80/y", "192.0.2.1"),
      // CIDR on standalone entries only
      found("10.0.0.0/8 2001:db8::/32", "10.0.0.0/8", "2001:db8::/32"),
      found("192.0.2.1/0,192.0.2.1/32,2001:db8::1/128,fe80::1%eth0/64", "192.0.2.1/0", "192.0.2.1/32", "2001:db8::1/128", "fe80::1%eth0/64"),
      found("[2001:db8::1]/64", "2001:db8::1/64"),
      found("192.0.2.1/path", "192.0.2.1"),
      found("192.0.2.1/+8/x", "192.0.2.1"),
      found("http://192.0.2.1/-1 http://[2001:db8::1]/+64", "192.0.2.1", "2001:db8::1"),
      found("192.0.2.1/+8 192.0.2.2", "192.0.2.2"),
      found("192.0.2.1/24/more", "192.0.2.1"),
      found("192.0.2.1:80/24", "192.0.2.1"),
      found("192.0.2.1/", "192.0.2.1"),
      found("192.0.2.1/３３", "192.0.2.1"),
      // URLs
      found("http://192.0.2.1:8080/path", "192.0.2.1"),
      found("git+ssh://user:pass@192.0.2.1/repo", "192.0.2.1"),
      found("//[2001:db8::1]:8080/path", "2001:db8::1"),
      found("http://192.0.2.1/24", "192.0.2.1"),
      found("http://192.0.2.1/7 http://192.0.2.2", "192.0.2.1", "192.0.2.2"),
      found("http://example.org/a/192.0.2.1/b", "192.0.2.1"),
      found("http://example.org?ip=192.0.2.1&other=192.0.2.2#192.0.2.3", "192.0.2.1", "192.0.2.2", "192.0.2.3"),
      found("http://example.org?ip=192.0.2.0/24", "192.0.2.0"),
      found("http://192.0.2.1?redirect=https://user@example.org/path?a=192.0.2.2", "192.0.2.1", "192.0.2.2"),
      found("http://192.0.2.1@example.org/192.0.2.2", "192.0.2.2"),
      found("http://a@b@192.0.2.1", "192.0.2.1"),
      found("see (http://192.0.2.1/x) and https://[2001:db8::1]/y", "192.0.2.1", "2001:db8::1"),
      found("http://[2001:db8::1]:bad/192.0.2.1", "192.0.2.1"),
      found("http://192.0.2.1;https://192.0.2.2", "192.0.2.1", "192.0.2.2"),
      // Malformed brackets and endpoints do not hide later addresses
      found("[2001:db8::1 192.0.2.1", "192.0.2.1"),
      found("[192.0.2.1] 192.0.2.2", "192.0.2.2"),
      found("[[2001:db8::1]] 192.0.2.2", "192.0.2.2"),
      found("192.0.2.1:99999 192.0.2.2", "192.0.2.2"),
      found("[2001:db8::1]:65536,192.0.2.2", "192.0.2.2"));
  }

  @ParameterizedTest
  @MethodSource("addressesFound")
  void shouldExtractAllAddresses(String content, List<String> expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "no address here",
    "/registry/ipaddresses",
    "1.2.3",
    "1.2.3.4.5",
    "v1.2.3.4",
    "a192.0.2.1",
    "192.0.2.1a",
    "192.0.2.1\uD835\uDC00", // Supplementary letter (mathematical bold A)
    "\uD835\uDC00192.0.2.1",
    "192.0.2.1\uD835\uDFCE", // Supplementary digit (mathematical bold zero)
    "\uD835\uDFCE192.0.2.1",
    "http://example.org/\uD835\uDC00192.0.2.1",
    "2001:db8::1\uD835\uDC00",
    "2001:db8::1%eth[0",
    "2001:db8::1%eth0]",
    "2001:db8::1%[",
    "[2001:db8::1%eth[0]:80",
    "[2001:db8::1%eth0]]",
    "[[2001:db8::1%eth0]",
    "2001:db8::1%eth[0/64",
    "http://[2001:db8::1%a]b]/x",
    "192.0.2.1-foo",
    "foo-192.0.2.1",
    "192.0.2.1_x",
    "x_192.0.2.1",
    "192.0.2.1.",
    ".192.0.2.1",
    "Connect to 192.0.2.1.",
    "999.2.3.4",
    "01.2.3.4",
    "2001:db8::g",
    "2001:db8::1::2",
    "192.0.2.1/33",
    "192.0.2.1/+8",
    "192.0.2.1/-1",
    "192.0.2.1/-0",
    "2001:db8::1/+64",
    "2001:db8::1/-1",
    "[2001:db8::1]/+64",
    "fe80::1%eth0/-1",
    "192.0.2.1/100",
    "192.0.2.1/1000",
    "2001:db8::1/129",
    "[192.0.2.1]",
    "[192.0.2.1]:8080",
    "192.0.2.1:",
    "192.0.2.1:bad",
    "192.0.2.1:+80",
    "192.0.2.1:８０",
    "192.0.2.1:65536",
    "192.0.2.1:000080",
    "[2001:db8::1]:",
    "[2001:db8::1]:000080",
    "[2001:db8::1]extra",
    "[2001:db8::1",
    "2001:db8::1]",
    "http://example.org/path",
    "http://192.0.2.1@example.org/path",
    "http://user:192.0.2.1@example.org",
    "http://[192.0.2.1]/path",
    "http://192.0.2.1.5/path"
  })
  void shouldNotExtractAnyAddress(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @Test
  void extractAllShouldNotChangeSingleCandidateExtraction() {
    assertThat(IpAddressCandidateExtractor.extract("/registry/ipaddresses/192.168.2.3")).isEmpty();
    assertThat(IpAddressCandidateExtractor.extract("connect to 10.0.0.1, 10.0.0.2")).hasValue("connect");
  }

  @Test
  void extractedAddressesShouldBeParsable() {
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.0/8 [fe80::1%eth0]:80 http://192.0.2.1:80/x"))
      .allSatisfy(address -> assertThat(IpAddressClassifier.parse(address)).isPresent());
  }

  @Test
  void shouldNotOverflowTheStackOnLongInput() {
    assertThat(IpAddressCandidateExtractor.extractAll("/".repeat(200_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("http:" + "/".repeat(200_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("?" + "//".repeat(100_000) + "?x")).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org/?a=" + "//a".repeat(50_000) + "&b=192.0.2.1"))
      .containsExactly("192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("a+".repeat(200_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("1+".repeat(200_000) + " 192.0.2.1")).containsExactly("192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("a:b:c%".repeat(100_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("::1%".repeat(100_000))).hasSize(1); // One long opaque zone
    assertThat(IpAddressCandidateExtractor.extractAll("http://" + "@[".repeat(100_000))).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll("http://" + "a@".repeat(100_000) + "192.0.2.1")).containsExactly("192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("a/".repeat(100_000) + "192.0.2.1")).containsExactly("192.0.2.1");
  }

  @Test
  void shouldFindAddressesInChainedAuthorities() {
    assertThat(IpAddressCandidateExtractor.extractAll("http://192.0.2.1//192.0.2.2///192.0.2.3/x"))
      .containsExactly("192.0.2.1", "192.0.2.2", "192.0.2.3");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?next=//[2001:db8::1]:80//192.0.2.2:8080/p"))
      .containsExactly("2001:db8::1", "192.0.2.2");
  }
}
