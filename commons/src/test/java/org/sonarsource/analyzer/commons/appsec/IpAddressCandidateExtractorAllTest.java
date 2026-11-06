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
    // Text, paths, and value separators
    "/registry/ipaddresses/192.168.2.3, 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.1",
    "'  10.0.0.1', 10.0.0.1",
    "'\u00a010.0.0.1\u2003', 10.0.0.1",
    "address=10.0.0.1, 10.0.0.1",
    "(10.0.0.1), 10.0.0.1",
    "\"10.0.0.1\", 10.0.0.1",
    "'''10.0.0.1''', 10.0.0.1",
    "10.0.0.1!, 10.0.0.1",
    "path/10.0.0.1/24, 10.0.0.1",
    "/10.0.0.1/33, 10.0.0.1",
    "10.0.0.1/path, 10.0.0.1",
    "10.0.0.1/path/33, 10.0.0.1",
    // Entire IPv6 literals, including mixed notation and opaque zones
    "2001:DB8:0000::1, 2001:DB8:0000::1",
    "::00000:2, ::00000:2",
    "::ffff:192.0.2.1, ::ffff:192.0.2.1",
    "2001:db8::192.0.2.1, 2001:db8::192.0.2.1",
    "fe80::1%, fe80::1%",
    "fe80::1%eth0, fe80::1%eth0",
    "fe80::1%接口, fe80::1%接口",
    "fe80::1%eth-0_1, fe80::1%eth-0_1",
    "fe80::1%eth~0, fe80::1%eth~0",
    "[fe80::1%25eth0]:8080, fe80::1%25eth0",
    "[fe80::1%接口]/64, fe80::1%接口/64",
    // Port handling
    "10.0.0.1:0, 10.0.0.1",
    "10.0.0.1:65535, 10.0.0.1",
    "10.0.0.1:00080, 10.0.0.1",
    "[2001:db8::1], 2001:db8::1",
    "[2001:db8::1]:65535, 2001:db8::1",
    "[::ffff:192.0.2.1]:0, ::ffff:192.0.2.1",
    "2001:db8::1:8080, 2001:db8::1:8080",
    "10.0.0.1:8080/33, 10.0.0.1",
    "[2001:db8::1]:8080/129, 2001:db8::1",
    // CIDR spelling and address-family bounds
    "10.0.0.1/0, 10.0.0.1/0",
    "10.0.0.1/32, 10.0.0.1/32",
    "10.0.0.1/032, 10.0.0.1/032",
    "ip=10.0.0.1/24, 10.0.0.1/24",
    "2001:db8::1/0, 2001:db8::1/0",
    "2001:db8::1/128, 2001:db8::1/128",
    "2001:db8::1/064, 2001:db8::1/064",
    "::ffff:192.0.2.1/120, ::ffff:192.0.2.1/120",
    "[2001:db8::1]/64, 2001:db8::1/64",
    // URL schemes, credentials, and numeric paths
    "http://10.0.0.1:8080/33, 10.0.0.1",
    "//[2001:db8::1]:8080/129, 2001:db8::1",
    "custom-scheme.v1://user:pass@10.0.0.1/path, 10.0.0.1",
    "http://10.0.0.2:pass@10.0.0.1/path, 10.0.0.1",
    "http://[2001:db8::2]@10.0.0.1/path, 10.0.0.1",
    "'http://10.0.0.2,user@10.0.0.1/path', 10.0.0.1",
    "http://10.0.0.2;user@10.0.0.1/path, 10.0.0.1",
    "http://example.org/10.0.0.1/33, 10.0.0.1",
    "http://example.org?ip=10.0.0.1, 10.0.0.1",
    "http://example.org#10.0.0.1, 10.0.0.1",
    "url=https://user@example.org/10.0.0.1, 10.0.0.1",
    "http://example.org?ip=10.0.0.1/33, 10.0.0.1"
  })
  void findsSingleAddress(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "no addresses here", "example.org", "http://example.org/path",
    "999.2.3.4", "192.168.01.1", "1.2.3", "1.2.3.4.5", "v1.2.3.4", "1.2.3.4.",
    "prefix10.0.0.1", "10.0.0.1suffix", "10.0.0.1-foo", "10.0.0.1_foo", "-10.0.0.1", "_10.0.0.1",
    "接口10.0.0.1", "10.0.0.1接口", "10.0.0.1９", "９10.0.0.1",
    "2001:db8:::1", "2001:db8::1::2", "gggg::1", "::ffff:999.2.3.4", "::ffff:192.0.02.1",
    "2001:db8::1.", "2001:db8::1-foo", "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]",
    "[192.0.2.1]", "[192.0.2.1]:8080", "[192.0.2.1:8080]", "[2001:db8::1]extra", "[2001:db8::1]8080",
    "[2001:db8::1].", "[2001:db8::1]suffix", "fe80::1%eth[0", "fe80::1%eth0]",
    "10.0.0.1:", "10.0.0.1:bad", "10.0.0.1:-1", "10.0.0.1:+80", "10.0.0.1:８０",
    "10.0.0.1:65536", "10.0.0.1:000080", "10.0.0.1:99999999999999999999", "10.0.0.1:80:90",
    "[2001:db8::1]:", "[2001:db8::1]:bad", "[2001:db8::1]:-1", "[2001:db8::1]:+80",
    "[2001:db8::1]:８０", "[2001:db8::1]:65536", "[2001:db8::1]:000080",
    "10.0.0.1/33", "10.0.0.1/9999999999999999999999", "10.0.0.1/-1", "10.0.0.1/+24",
    "10.0.0.1/２４", "10.0.0.1/24bad", "10.0.0.1/24.", "10.0.0.1/33.5", "2001:db8::1/129", "[2001:db8::1]/129",
    "http://10.0.0.1@host.example/path", "http://10.0.0.1:65536/path", "http://[2001:db8::1]:bad/path",
    "http://host10.0.0.1/path", "http://host:2001:db8::1/path", "http+10.0.0.1://example.org"
  })
  void rejectsMalformedAddressesAndEndpoints(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @Test
  void retainsOrderAndRepeatedOccurrencesAcrossLists() {
    assertThat(IpAddressCandidateExtractor.extractAll("connect to 10.0.0.1, 10.0.0.2;[2001:DB8::1]:443 10.0.0.1"))
      .containsExactly("10.0.0.1", "10.0.0.2", "2001:DB8::1", "10.0.0.1");
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.1/24,2001:db8::1/64;10.0.0.1/24"))
      .containsExactly("10.0.0.1/24", "2001:db8::1/64", "10.0.0.1/24");
  }

  @Test
  void searchesEveryPartOfUrlsAndContinuesAfterNamedHosts() {
    assertThat(IpAddressCandidateExtractor.extractAll(
      "http://10.0.0.9:secret@10.0.0.1:80/10.0.0.2?peer=10.0.0.3#10.0.0.4 " +
      "https://example.org/10.0.0.5?other=[2001:DB8::1]:443"))
      .containsExactly("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "2001:DB8::1");
    assertThat(IpAddressCandidateExtractor.extractAll("http://10.0.0.1,https://user@10.0.0.2;10.0.0.3/24"))
      .containsExactly("10.0.0.1", "10.0.0.2", "10.0.0.3/24");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?next=https://10.0.0.9@10.0.0.1/10.0.0.2"))
      .containsExactly("10.0.0.1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?one=10.0.0.1/33;two=10.0.0.2/33"))
      .containsExactly("10.0.0.1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org?peers=10.0.0.1,10.0.0.2/24"))
      .containsExactly("10.0.0.1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org#10.0.0.1;10.0.0.2/33"))
      .containsExactly("10.0.0.1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("http://example.org/path;peer=10.0.0.1/33"))
      .containsExactly("10.0.0.1");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]", "10.0.0.1:65536", "10.0.0.1/33",
    "http://[2001:db8::1]:bad/path", "http://[2001:db8::1/path", "[2001:db8::1]:", "[192.0.2.1]"
  })
  void malformedEntriesDoNotHideLaterAddresses(String malformed) {
    for (String separator : new String[] {" ", ",", ";", ") (", "\" \""}) {
      assertThat(IpAddressCandidateExtractor.extractAll(malformed + separator + "10.0.0.2"))
        .as("after %s separated by %s", malformed, separator)
        .containsExactly("10.0.0.2");
    }
  }

  @Test
  void treatsSlashesBetweenAddressesAsPathSeparators() {
    assertThat(IpAddressCandidateExtractor.extractAll("/10.0.0.1/2001:db8::1/10.0.0.2"))
      .containsExactly("10.0.0.1", "2001:db8::1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.1/10.0.0.2"))
      .containsExactly("10.0.0.1", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.1/10.0.0.2:80"))
      .containsExactly("10.0.0.1", "10.0.0.2");
  }

  @Test
  void leavesSecurityRuleClassificationToTheCaller() {
    assertThat(IpAddressCandidateExtractor.extractAll("0.0.0.0 127.0.0.1 224.0.0.1 255.255.255.255 8.8.8.8 :: ff02::1"))
      .containsExactly("0.0.0.0", "127.0.0.1", "224.0.0.1", "255.255.255.255", "8.8.8.8", "::", "ff02::1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"|", "&", "!", "<", ">", "@"})
  void cidrSuffixDoesNotSwallowLaterAddresses(String separator) {
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.0/8" + separator + "10.0.0.2"))
      .containsExactly("10.0.0.0/8", "10.0.0.2");
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.0/33" + separator + "10.0.0.2"))
      .containsExactly("10.0.0.2");
  }

  @Test
  void boundsEachCidrValueAndKeepsMalformedSuffixesAttached() {
    assertThat(IpAddressCandidateExtractor.extractAll("allow=10.0.0.0/8&gw=10.0.0.2/24"))
      .containsExactly("10.0.0.0/8", "10.0.0.2/24");
    assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.1/24!"))
      .containsExactly("10.0.0.1/24");
    assertThat(IpAddressCandidateExtractor.extractAll("2001:db8::1/64|2001:db8::2/129|10.0.0.2"))
      .containsExactly("2001:db8::1/64", "10.0.0.2");
    for (String suffix : new String[] {"24bad", "24.", "24.0", "24:80", "+24", "-1", "２４", "24%zone", "24["}) {
      assertThat(IpAddressCandidateExtractor.extractAll("10.0.0.1/" + suffix + "!10.0.0.2"))
        .as("malformed prefix %s", suffix)
        .containsExactly("10.0.0.2");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "<http://10.0.0.1>, 10.0.0.1",
    "http://10.0.0.1!, 10.0.0.1",
    "[http://10.0.0.1], 10.0.0.1",
    "<http://10.0.0.1:80>, 10.0.0.1",
    "http://[2001:db8::1]:80!, 2001:db8::1",
    "[http://[2001:db8::1]], 2001:db8::1",
    "[http://[2001:db8::1]:80], 2001:db8::1",
    "[http://[fe80::1%eth0]], fe80::1%eth0"
  })
  void findsUrlHostsEnclosedByPunctuation(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "http://10.0.0.1:65536!", "http://10.0.0.1:bad!", "http://10.0.0.1.",
    "http://host10.0.0.1!", "http://host:10.0.0.1!", "http://[192.0.2.1]!",
    "http://[2001:db8::1]:bad!", "http://2001:db8::1]!", "[http://[2001:db8::1]",
    "[http://10.0.0.1]suffix", "http:///path", "//"
  })
  void punctuationDoesNotSalvageMalformedUrlEndpoints(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "[http://[::1]/], ::1",
    "[http://[2001:db8::1]/path], 2001:db8::1",
    "[http://[::1]?q=1], ::1",
    "[http://[::1]#fragment], ::1",
    "[http://[::1]:80/x], ::1",
    "[http://[fe80::1%eth0]/path], fe80::1%eth0",
    "[http://[fe80::1%eth0]?q=1], fe80::1%eth0",
    "[http://[fe80::1%eth0]#fragment], fe80::1%eth0"
  })
  void keepsIpv6HostBracketsInsideWrappedUrls(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @Test
  void searchesWrappedUrlValuesAndLaterEntries() {
    assertThat(IpAddressCandidateExtractor.extractAll(
      "[http://[::1]/10.0.0.1?peer=10.0.0.2#10.0.0.3],http://10.0.0.4;[http://[::2]]"))
      .containsExactly("::1", "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "::2");
  }

  @ParameterizedTest
  @CsvSource({
    "http://[fe80::1%eth0]!, fe80::1%eth0",
    "<http://[fe80::1%eth0]>, fe80::1%eth0",
    "<http://[fe80::1%eth0]:80>, fe80::1%eth0",
    "http://[fe80::1%eth0]:65535!, fe80::1%eth0",
    "http://[fe80::1%eth~0]!, fe80::1%eth~0",
    "http://[fe80::1%eth!0]:80!, fe80::1%eth!0",
    "http://[fe80::1%]:0!, fe80::1%",
    "<http://[fe80::1%接口]:80>, fe80::1%接口",
    "fe80::1%eth!0, fe80::1%eth!0",
    "fe80::1%eth>0, fe80::1%eth>0"
  })
  void boundsBracketedUrlZonesWithoutChangingOpaqueZones(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[http://[::1]", "[http://[::1]/path", "[http://[::1]?q=1", "[http://[::1]#fragment",
    "[http://[::1]]suffix", "http://[fe80::1%eth0]:bad!", "http://[fe80::1%eth0]:80bad!",
    "http://[fe80::1%eth0]:65536!", "http://[fe80::1%eth0]:000080!", "http://[fe80::1%eth0]:!",
    "http://[fe80::1%eth0]:+80!", "http://[fe80::1%eth0]:-1!", "http://[fe80::1%eth0]:８０!",
    "http://[fe80::1%eth0].", "http://[fe80::1%eth0]suffix!", "http://[fe80::1%eth0]]!",
    "http://[fe80::1%eth0!", "[http://[fe80::1%eth0]"
  })
  void malformedWrappedAndZonedUrlEndpointsDoNotHideLaterAddresses(String malformed) {
    assertThat(IpAddressCandidateExtractor.extractAll(malformed)).isEmpty();
    for (String separator : new String[] {" ", ",", ";"}) {
      assertThat(IpAddressCandidateExtractor.extractAll(malformed + separator + "10.0.0.2"))
        .as("after %s separated by %s", malformed, separator)
        .containsExactly("10.0.0.2");
    }
  }


  @ParameterizedTest
  @CsvSource({
    "<[fe80::1%eth0]>, fe80::1%eth0",
    "[fe80::1%eth0]:80!, fe80::1%eth0",
    "<[fe80::1%eth0]:80>, fe80::1%eth0",
    "[fe80::1%eth!0]:65535!, fe80::1%eth!0",
    "<[fe80::1%eth>0]>, fe80::1%eth>0",
    "[fe80::1%接口]!, fe80::1%接口",
    "[fe80::1%]:0!, fe80::1%",
    "[::ffff:192.0.2.1%eth0]!, ::ffff:192.0.2.1%eth0",
    "[fe80::1%eth0]/64!, fe80::1%eth0/64"
  })
  void boundsBracketedZonesInText(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected);
  }

  @Test
  void continuesAfterBracketedZonesAndKeepsOpaqueUnbracketedZones() {
    assertThat(IpAddressCandidateExtractor.extractAll(
      "<[fe80::1%eth0]>!10.0.0.1&[fe80::2%eth0]:80!10.0.0.2 fe80::3%eth>0"))
      .containsExactly("fe80::1%eth0", "10.0.0.1", "fe80::2%eth0", "10.0.0.2", "fe80::3%eth>0");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[fe80::1%eth0]:bad", "[fe80::1%eth0]:80bad", "[fe80::1%eth0]:65536", "[fe80::1%eth0]:000080",
    "[fe80::1%eth0]:", "[fe80::1%eth0]:+80", "[fe80::1%eth0]:-1", "[fe80::1%eth0]:８０",
    "[fe80::1%eth0]:80:90", "[fe80::1%eth0]:80%zone", "[fe80::1%eth0]suffix", "[fe80::1%eth0].",
    "[fe80::1%eth0]]", "[[fe80::1%eth0]]", "[fe80::1%eth0", "[fe80::1%eth0]/129"
  })
  void rejectsMalformedBracketedZonesAndContinuesAfterPunctuation(String malformed) {
    assertThat(IpAddressCandidateExtractor.extractAll(malformed + "!10.0.0.2"))
      .containsExactly("10.0.0.2");
  }

}
