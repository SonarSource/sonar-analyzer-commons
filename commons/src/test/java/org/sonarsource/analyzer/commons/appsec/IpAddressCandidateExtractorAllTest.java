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

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class IpAddressCandidateExtractorAllTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "/registry/ipaddresses/192.168.2.3 | 192.168.2.3",
    "connect to 10.0.0.1 | 10.0.0.1",
    "'  10.0.0.1' | 10.0.0.1",
    "'\t10.0.0.1\u00a0' | 10.0.0.1",
    "address=10.0.0.1 | 10.0.0.1",
    "(10.0.0.1) | 10.0.0.1",
    "'\"10.0.0.1\"' | 10.0.0.1",
    "'''10.0.0.1''' | 10.0.0.1",
    "10.0.0.1! | 10.0.0.1",
    "10.0.0.1+other | 10.0.0.1",
    "2001:DB8:0000::1 | 2001:DB8:0000::1",
    "::00000:2 | ::00000:2",
    ":: | ::",
    "::ffff:192.0.2.1 | ::ffff:192.0.2.1",
    "2001:db8::192.0.2.1 | 2001:db8::192.0.2.1",
    "[::ffff:192.0.2.1] | ::ffff:192.0.2.1",
    "fe80::1%eth0 | fe80::1%eth0",
    "fe80::1%接口 | fe80::1%接口",
    "fe80::1% | fe80::1%",
    "fe80::1%eth~0 | fe80::1%eth~0",
    "fe80::1%eth=0 | fe80::1%eth=0",
    "fe80::1%eth?0 | fe80::1%eth?0",
    "fe80::1%eth#0 | fe80::1%eth#0",
    "fe80::1%eth&0 | fe80::1%eth&0",
    "[fe80::1%eth=0]:80 | fe80::1%eth=0",
    "[fe80::1%eth?0]:80 | fe80::1%eth?0",
    "[fe80::1%eth#0]:80 | fe80::1%eth#0",
    "[fe80::1%eth&0]:80 | fe80::1%eth&0",
    "[fe80::1%eth,0]:80 | fe80::1%eth,0",
    "[fe80::1%eth;0]:80 | fe80::1%eth;0",
    "[fe80::1%eth(0)]:80 | fe80::1%eth(0)",
    "[fe80::1%eth'0]:80 | fe80::1%eth'0",
    "http://[fe80::1%eth?0]:80/path | fe80::1%eth?0",
    "http://[fe80::1%eth#0]:80/path | fe80::1%eth#0",
    "http://[fe80::1%eth@0]:80/path | fe80::1%eth@0",
    "http://[fe80::1%eth,0]:80/path | fe80::1%eth,0",
    "http://[fe80::1%eth;0]:80/path | fe80::1%eth;0",
    "[fe80::1%eth0]:8080 | fe80::1%eth0",
    "10.0.0.1:0 | 10.0.0.1",
    "10.0.0.1:65535 | 10.0.0.1",
    "10.0.0.1:00080 | 10.0.0.1",
    "[2001:db8::1]:0 | 2001:db8::1",
    "[2001:db8::1]:65535 | 2001:db8::1",
    "2001:db8::1:8080 | 2001:db8::1:8080",
    "10.0.0.1/0 | 10.0.0.1/0",
    "10.0.0.1/32 | 10.0.0.1/32",
    "address=10.0.0.1/032 | 10.0.0.1/032",
    "2001:db8::1/128 | 2001:db8::1/128",
    "fe80::1%eth0/64 | fe80::1%eth0/64",
    "[2001:db8::1]/64 | 2001:db8::1/64",
    "::ffff:192.0.2.1/128 | ::ffff:192.0.2.1/128",
    "/10.0.0.1/33 | 10.0.0.1",
    "registry/10.0.0.1/33 | 10.0.0.1",
    "10.0.0.1/path/33 | 10.0.0.1",
    "10.0.0.1/path | 10.0.0.1",
    "[::1]/index.html | ::1",
    "10.0.0.1:80/33 | 10.0.0.1",
    "[2001:db8::1]:80/129 | 2001:db8::1",
    "http://10.0.0.1/33 | 10.0.0.1",
    "//[2001:db8::1]/129 | 2001:db8::1",
    "git+ssh://user:pass@[2001:DB8::1]:22/path | 2001:DB8::1",
    "http://192.0.2.1@10.0.0.1/ | 10.0.0.1",
    "http://192.0.2.1,192.0.2.2@10.0.0.1/ | 10.0.0.1",
    "http://192.0.2.1;192.0.2.2@10.0.0.1/ | 10.0.0.1",
    "http://user=192.0.2.1@10.0.0.1/ | 10.0.0.1",
    "http://user(10.0.0.1)@example.org/10.0.0.2 | 10.0.0.2",
    "http://user(::1)@example.org/10.0.0.2 | 10.0.0.2",
    "http://user'10.0.0.1'@example.org/10.0.0.2 | 10.0.0.2",
    "http://user!$&'()*+,;=10.0.0.1@example.org/10.0.0.2 | 10.0.0.2",
    "http://example.org/(10.0.0.1/33) | 10.0.0.1",
    "http://example.org/'10.0.0.1/33' | 10.0.0.1",
    "http://example.org/?a=one(thing);ip=10.0.0.1/24 | 10.0.0.1",
    "http://example.org/?a=one(thing);ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/?a=one'thing';ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/#a=(thing);ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/?a=one;ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/?a=one;ip=10.0.0.1/24 | 10.0.0.1",
    "'http://example.org/?a=one,ip=10.0.0.1/33' | 10.0.0.1",
    "'http://example.org/?a=one,ip=10.0.0.1/24' | 10.0.0.1",
    "http://example.org/path;ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/#a;ip=10.0.0.1/33 | 10.0.0.1",
    "custom+v1.2.3.4://example.org/path/10.0.0.1 | 10.0.0.1",
    "http://example.org/path/10.0.0.1 | 10.0.0.1",
    "http://example.org/?ip=10.0.0.1 | 10.0.0.1",
    "http://example.org/#10.0.0.1 | 10.0.0.1",
    "http://example.org/?ip=10.0.0.1/33 | 10.0.0.1",
    "http://example.org/?next=http://192.0.2.1@10.0.0.1/ | 10.0.0.1"
  })
  void extractsAddressWithoutChangingItsSpelling(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @ParameterizedTest
  @MethodSource("multipleAddresses")
  void returnsAllOccurrencesInSourceOrder(String content, List<String> expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> multipleAddresses() {
    return Stream.of(
      arguments("10.0.0.1, 10.0.0.2; 10.0.0.1", List.of("10.0.0.1", "10.0.0.2", "10.0.0.1")),
      arguments("10.0.0.1\t::1\n10.0.0.2", List.of("10.0.0.1", "::1", "10.0.0.2")),
      arguments("(10.0.0.1) [::1]:80 \"10.0.0.2\"", List.of("10.0.0.1", "::1", "10.0.0.2")),
      arguments("'http://10.0.0.1';contact=user@example.org", List.of("10.0.0.1")),
      arguments("(http://10.0.0.1);contact=user@example.org", List.of("10.0.0.1")),
      arguments("' http://10.0.0.1'", List.of("10.0.0.1")),
      arguments("( http://10.0.0.1)", List.of("10.0.0.1")),
      arguments("(url=http://10.0.0.1);contact=user@example.org", List.of("10.0.0.1")),
      arguments("'url= http://10.0.0.1';contact=user@example.org", List.of("10.0.0.1")),
      arguments("Don't use (http://10.0.0.1)", List.of("10.0.0.1")),
      arguments("'Don't use http://10.0.0.1'", List.of("10.0.0.1")),
      arguments("\"Use (http://10.0.0.1)\"", List.of("10.0.0.1")),
      arguments("'Use (http://10.0.0.1)'", List.of("10.0.0.1")),
      arguments("Don't use http://user'10.0.0.1'@example.org/10.0.0.2", List.of("10.0.0.2")),
      arguments("Cafe\u0301's http://user'10.0.0.1'@example.org/10.0.0.2", List.of("10.0.0.2")),
      arguments("a\u093e's http://user'10.0.0.1'@example.org/10.0.0.2", List.of("10.0.0.2")),
      arguments("a\u20dd's http://user'10.0.0.1'@example.org/10.0.0.2", List.of("10.0.0.2")),
      arguments("a\ud834\udd65's http://user'10.0.0.1'@example.org/10.0.0.2", List.of("10.0.0.2")),
      arguments("(http://[fe80::1%eth(0]:80/path/10.0.0.2)", List.of("fe80::1%eth(0", "10.0.0.2")),
      arguments("( url= http://user(10.0.0.1)@example.org/10.0.0.2);contact=user@example.org", List.of("10.0.0.2")),
      arguments("(http://user(10.0.0.1)@example.org/10.0.0.2);contact=user@example.org", List.of("10.0.0.2")),
      arguments("\"http://user'10.0.0.1'@example.org/10.0.0.2\";contact=user@example.org", List.of("10.0.0.2")),
      arguments("http://" + String.join(":", "192.0.2.1", "192.0.2.2") + "@10.0.0.1/", List.of("10.0.0.1")),
      arguments("(http://example.org/(other);ip=10.0.0.1/33),10.0.0.2/24", List.of("10.0.0.1", "10.0.0.2/24")),
      arguments("'http://example.org/10.0.0.1',10.0.0.2/24", List.of("10.0.0.1", "10.0.0.2/24")),
      arguments("http://10.0.0.1,10.0.0.2/24", List.of("10.0.0.1", "10.0.0.2/24")),
      arguments("10.0.0.1/24, fe80::1%eth0/64; 10.0.0.1/24", List.of("10.0.0.1/24", "fe80::1%eth0/64", "10.0.0.1/24")),
      arguments("http://10.0.0.1/10.0.0.2?peer=10.0.0.3#2001:db8::1", List.of("10.0.0.1", "10.0.0.2", "10.0.0.3", "2001:db8::1")),
      arguments("http://example.org/10.0.0.1?q=10.0.0.2&peer=10.0.0.1#::1", List.of("10.0.0.1", "10.0.0.2", "10.0.0.1", "::1")),
      arguments("http://10.0.0.1;https://[::1]:443,10.0.0.2", List.of("10.0.0.1", "::1", "10.0.0.2")),
      arguments("http://10.0.0.1/path@10.0.0.2", List.of("10.0.0.1", "10.0.0.2")),
      arguments("::ffff:192.0.2.1 192.0.2.1", List.of("::ffff:192.0.2.1", "192.0.2.1")),
      arguments("[bad;10.0.0.1 http://[::1]:bad/path/10.0.0.2", List.of("10.0.0.1", "10.0.0.2")));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "ordinary text", "example.org", "version 1.2.3", "1.2.3.4.5", ".10.0.0.1", "10.0.0.1.",
    "prefix10.0.0.1", "10.0.0.1suffix", "v1.2.3.4", "_10.0.0.1", "10.0.0.1_", "-10.0.0.1", "10.0.0.1-test",
    "é10.0.0.1", "10.0.0.1é", "𐐀10.0.0.1", "10.0.0.1𐐀", "10.0.0.1９",
    "999.10.0.1", "256.10.0.1", "01.2.3.4", "2001:db8:::1", "::ffff:999.2.3.4", "::ffff:01.2.3.4", "::ffff:192.0.2.1.",
    "[10.0.0.1]", "[10.0.0.1]:80", "[::1", "::1]", "[[::1]]", "[::1]extra", "[::1]80", "[10.0.0.1:80]",
    "10.0.0.1:", "10.0.0.1:bad", "10.0.0.1:+80", "10.0.0.1:-1", "10.0.0.1:８０", "10.0.0.1:65536", "10.0.0.1:000080",
    "10.0.0.1:80+1", "10.0.0.1:80!1", "10.0.0.1:80~1", "[::1]:80+1", "[::1]:80!1", "[::1]:80~1",
    "[::1]:", "[::1]:bad", "[::1]:+80", "[::1]:-1", "[::1]:８０", "[::1]:65536", "[::1]:000080",
    "10.0.0.1/33", "10.0.0.1/9999999999999999999999", "10.0.0.1/-1", "10.0.0.1/+24", "10.0.0.1/３２", "10.0.0.1/24x", "10.0.0.1/",
    "2001:db8::1/129", "2001:db8::1/-1", "[2001:db8::1]/129", "::ffff:192.0.2.1/129", "fe80::1%eth[0", "fe80::1%eth0]",
    "http://name+10.0.0.1/path", "http://name!10.0.0.1/path", "http://user'10.0.0.1'@example.org/path",
    "http://10.0.0.1@example.org/path", "//[::1]@example.org/path", "http://example10.0.0.1/path", "http://[10.0.0.1]/path"
  })
  void rejectsMalformedEntriesAndStillFindsALaterAddress(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll(content + " 203.0.113.1")).containsExactly("203.0.113.1");
  }

  @Test
  void scansLongSchemeLikeTextWithoutRepeatedSuffixSearches() {
    String content = "a+".repeat(32_000);
    assertTimeout(Duration.ofSeconds(2), () -> assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty());
  }

  @Test
  void requiresNonNullContent() {
    assertThatNullPointerException().isThrownBy(() -> IpAddressCandidateExtractor.extractAll(null));
  }
}
