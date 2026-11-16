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

class IpAddressCandidateExtractorAllTest {

  @ParameterizedTest
  @CsvSource({
    "/registry/ipaddresses/192.168.2.3, 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.1",
    "' \t10.0.0.1', 10.0.0.1",
    "'\u00a010.0.0.1\u2003', 10.0.0.1",
    "address=10.0.0.1, 10.0.0.1",
    "(10.0.0.1), 10.0.0.1",
    "'\"10.0.0.1\"', 10.0.0.1",
    "2001:DB8:0:0:0:0:0:1, 2001:DB8:0:0:0:0:0:1",
    "::, ::",
    "::1, ::1",
    "::ffff:192.0.2.1, ::ffff:192.0.2.1",
    "2001:db8::192.0.2.1, 2001:db8::192.0.2.1",
    "fe80::1%eth0, fe80::1%eth0",
    "fe80::1%接口, fe80::1%接口",
    "fe80::1%eth=0, fe80::1%eth=0",
    "fe80::1%eth@0, fe80::1%eth@0",
    "fe80::1%eth&0, fe80::1%eth&0",
    "[fe80::1%eth=0]:80, fe80::1%eth=0",
    "[fe80::1%eth@0]:80, fe80::1%eth@0",
    "[fe80::1%eth&0]:80, fe80::1%eth&0",
    "[fe80::1%eth(0)]:80, fe80::1%eth(0)",
    "http://[fe80::1%eth@0]:80/path, fe80::1%eth@0",
    "2001:db8::1%, 2001:db8::1%",
    "[fe80::1%eth-0]:80, fe80::1%eth-0",
    "[2001:db8::1], 2001:db8::1",
    "[::ffff:192.0.2.1]:65535, ::ffff:192.0.2.1",
    "192.0.2.1:0, 192.0.2.1",
    "192.0.2.1:65535, 192.0.2.1",
    "192.0.2.1:00080, 192.0.2.1",
    "[2001:db8::1]:0, 2001:db8::1",
    "2001:db8::1:80, 2001:db8::1:80",
    "http://192.0.2.1:80/path, 192.0.2.1",
    "git+ssh://192.0.2.1/path, 192.0.2.1",
    "custom-scheme.v1://[2001:db8::1]:443/path, 2001:db8::1",
    "//[2001:db8::1]/128, 2001:db8::1",
    "http://user:pass@192.0.2.1/path, 192.0.2.1",
    "http://user(foo)@192.0.2.1/path, 192.0.2.1",
    "http://user'foo'@192.0.2.1/path, 192.0.2.1",
    "http://192.0.2.2@192.0.2.1/path, 192.0.2.1",
    "'http://192.0.2.2,user;pass@192.0.2.1/path', 192.0.2.1",
    "url=http://192.0.2.2@example.org/path/192.0.2.1, 192.0.2.1",
    "http://example.org/path/192.0.2.1, 192.0.2.1",
    "http://example.org?ip=192.0.2.1, 192.0.2.1",
    "http://example.org#192.0.2.1, 192.0.2.1",
    "http://example.org?next=http://user@192.0.2.1/path, 192.0.2.1",
    "http://[fe80::1%25eth0]:80/path, fe80::1%25eth0",
    "192.0.2.1/0, 192.0.2.1/0",
    "192.0.2.1/32, 192.0.2.1/32",
    "192.0.2.1/032, 192.0.2.1/032",
    "ip=192.0.2.1/24, 192.0.2.1/24",
    "2001:db8::1/0, 2001:db8::1/0",
    "[2001:db8::1]/128, 2001:db8::1/128",
    "[fe80::1%接口]/064, fe80::1%接口/064",
    "::ffff:192.0.2.1/120, ::ffff:192.0.2.1/120",
    "http://192.0.2.1/33, 192.0.2.1",
    "http://example.org/192.0.2.1/33, 192.0.2.1",
    "/registry/192.0.2.1/33, 192.0.2.1",
    "192.0.2.1/path/33, 192.0.2.1",
    "192.0.2.1/path, 192.0.2.1",
    "192.0.2.1/, 192.0.2.1",
    "192.0.2.1:80/33, 192.0.2.1",
    "[2001:db8::1]:80/129, 2001:db8::1"
  })
  void findsCompleteAddress(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @Test
  void retainsOrderAndRepeatedOccurrences() {
    assertThat(IpAddressCandidateExtractor.extractAll("connect to 10.0.0.1, 10.0.0.2;10.0.0.1\t[2001:db8::1]:80\n::ffff:192.0.2.1"))
      .containsExactly("10.0.0.1", "10.0.0.2", "10.0.0.1", "2001:db8::1", "::ffff:192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("'192.0.2.1' (192.0.2.2) \"192.0.2.1\""))
      .containsExactly("192.0.2.1", "192.0.2.2", "192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("192.0.2.1/192.0.2.2"))
      .containsExactly("192.0.2.1", "192.0.2.2");
    assertThat(IpAddressCandidateExtractor.extractAll("192.0.2.1/2001:db8::1"))
      .containsExactly("192.0.2.1", "2001:db8::1");
  }

  @Test
  void searchesAllUrlComponentsAndLaterUrls() {
    assertThat(IpAddressCandidateExtractor.extractAll(
      "http://192.0.2.9@192.0.2.1:80/192.0.2.2?ip=192.0.2.3&peer=[2001:db8::1]#192.0.2.4;https://example.org/192.0.2.5"))
        .containsExactly("192.0.2.1", "192.0.2.2", "192.0.2.3", "2001:db8::1", "192.0.2.4", "192.0.2.5");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org/path@192.0.2.1 http://192.0.2.2,https://192.0.2.3"))
      .containsExactly("192.0.2.1", "192.0.2.2", "192.0.2.3");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?ip=fe80::1%eth=0&peer=192.0.2.1"))
      .containsExactly("fe80::1%eth=0", "192.0.2.1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"eth;0", "eth,0", "eth?0", "eth#0", "eth 0", "eth(0)", "eth\"0", "eth'0", "eth=0", "eth@0", "eth&0"})
  void preservesCompleteBracketedZones(String zone) {
    String address = "fe80::1%" + zone;
    assertThat(IpAddressCandidateExtractor.extractAll("[" + address + "]:80")).containsExactly(address);
    assertThat(IpAddressCandidateExtractor.extractAll("http://[" + address + "]:80/path")).containsExactly(address);
    assertThat(IpAddressCandidateExtractor.extractAll("[" + address + "]:65536; 192.0.2.1")).containsExactly("192.0.2.1");
  }

  @Test
  void recoversAfterIncompleteBracketedZones() {
    assertThat(IpAddressCandidateExtractor.extractAll("[fe80::1%eth; 192.0.2.1, [fe80::1%eth, 192.0.2.2"))
      .containsExactly("192.0.2.1", "192.0.2.2");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?ip=[fe80::1%eth&peer=192.0.2.1"))
      .containsExactly("192.0.2.1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"192.0.2.2:80", "192.0.2.2:0", "192.0.2.2:65535"})
  void findsEndpointAfterAddressPathSegment(String endpoint) {
    assertThat(IpAddressCandidateExtractor.extractAll("192.0.2.1/" + endpoint)).containsExactly("192.0.2.1", "192.0.2.2");
  }

  @Test
  void separatesRelativeUrlQueryValuesAfterZones() {
    assertThat(IpAddressCandidateExtractor.extractAll("relative/path?ip=fe80::1%eth=0&peer=192.0.2.1#192.0.2.2"))
      .containsExactly("fe80::1%eth=0", "192.0.2.1", "192.0.2.2");
    assertThat(IpAddressCandidateExtractor.extractAll("?ip=fe80::1%eth=0&peer=192.0.2.1"))
      .containsExactly("fe80::1%eth=0", "192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("relative/path#ip=fe80::1%eth=0&peer=192.0.2.1"))
      .containsExactly("fe80::1%eth=0", "192.0.2.1");
    assertThat(IpAddressCandidateExtractor.extractAll("relative?ip=192.0.2.1/33"))
      .containsExactly("192.0.2.1");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "example.org", "http://example.org/path", "http://192.0.2.1@example.org/path", "v192.0.2.1://example.org",
    "http://192.0.2.2(foo)@example.org/path", "http://192.0.2.2'foo'@example.org/path",
    "999.2.3.4", "01.2.3.4", "192.0.2.1.5", "1.192.0.2.1", "192.0.2.1.", ".192.0.2.1", "1.2.3",
    "x192.0.2.1", "192.0.2.1x", "_192.0.2.1", "192.0.2.1_", "-192.0.2.1", "192.0.2.1-", "é192.0.2.1", "192.0.2.1é",
    "2001:db8::1::2", "2001:db8::gggg", "2001:db8::192.0.2.999", "2001:db8::192.0.2.1.5", "2001:db8::1.",
    "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]", "[2001:db8::1]extra", "[2001:db8::1]8080",
    "[192.0.2.1]", "[192.0.2.1]:80", "[192.0.2.1:80]", "fe80::1%eth[0", "fe80::1%eth0]",
    "192.0.2.1:", "192.0.2.1:bad", "192.0.2.1:-1", "192.0.2.1:+80", "192.0.2.1:８０", "192.0.2.1:65536", "192.0.2.1:000080",
    "[2001:db8::1]:", "[2001:db8::1]:bad", "[2001:db8::1]:-1", "[2001:db8::1]:+80", "[2001:db8::1]:８０",
    "[2001:db8::1]:65536", "[2001:db8::1]:000080", "http://192.0.2.1:65536/path", "http://[2001:db8::1]:bad/path",
    "192.0.2.1/33", "192.0.2.1/1000", "192.0.2.1/-1", "192.0.2.1/+24", "192.0.2.1/24x", "192.0.2.1/３２",
    "192.0.2.1/24:80", "192.0.2.1/24.0",
    "2001:db8::1/129", "[2001:db8::1]/129", "::ffff:192.0.2.1/129"
  })
  void rejectsInvalidEntriesAndFindsLaterAddress(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll(content + "; 10.0.0.2")).containsExactly("10.0.0.2");
  }
}
