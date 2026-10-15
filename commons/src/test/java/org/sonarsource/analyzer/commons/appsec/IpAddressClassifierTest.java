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

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.analyzer.commons.appsec.IpAddressClassifier.ParsedIp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpAddressClassifierTest {

  @ParameterizedTest
  @ValueSource(strings = {
    // "This network" (0.0.0.0/8)
    "0.0.0.0", // singleton sentinel
    "0.99.42.7", // middle of the /8
    // RFC 1918 Private (10/8, 172.16/12, 192.168/16) — middle of each block, then boundaries
    "10.99.42.7",
    "10.0.0.1",
    "10.255.255.255",
    "172.24.50.25",
    "172.16.0.0",
    "172.31.255.255",
    "192.168.99.42",
    "192.168.0.0",
    "192.168.255.255",
    // CGNAT (100.64.0.0/10)
    "100.96.50.25", // middle (100.64 to 100.127)
    "100.64.0.0",
    "100.100.100.200", // Alibaba ECS IMDS
    // Loopback (127.0.0.0/8)
    "127.99.42.7", // middle
    // Link-local (169.254.0.0/16)
    "169.254.169.254", // middle — also Azure IMDS
    // IETF Protocol Assignments (192.0.0.0/24)
    "192.0.0.128", // middle of /24
    // Documentation: TEST-NET-1/2/3 — middle of each /24
    "192.0.2.128",
    "198.51.100.128",
    "203.0.113.128",
    // Benchmarking (198.18.0.0/15) — middle (second /16 of the /15)
    "198.19.50.25",
    // Reserved for future use / Class E (240.0.0.0/4) — middle and the limited-broadcast singleton
    "247.99.42.7",
    "255.255.255.255"
  })
  void shouldClassifyReservedIpv4SingleAsReserved(String literal) {
    assertThat(IpAddressClassifier.isReserved(literal)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // Ordinary public
    "1.2.3.4",
    "8.8.8.8",
    "199.0.0.0",
    // Just outside an RFC 1918 boundary
    "172.32.0.0",
    "192.1.0.0",
    // IANA single-use specials marked globally reachable (not in our safe list)
    "192.31.196.0", // AS112-v4 (RFC 7535)
    "192.52.193.0", // AMT (RFC 7450)
    "192.175.48.0" // AS112 Direct Delegation (RFC 7534)
  })
  void shouldClassifyPublicIpv4SingleAsNotReserved(String literal) {
    assertThat(IpAddressClassifier.isReserved(literal)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // "This network" (0.0.0.0/8)
    "0.0.0.0/8",
    // RFC 1918 Private (10/8, 172.16/12, 192.168/16)
    "10.0.0.0/8",
    "10.5.0.0/16",
    "172.16.0.0/12",
    "172.20.0.0/16",
    "192.168.0.0/16",
    "192.168.27.0/24",
    // Loopback (127.0.0.0/8)
    "127.0.0.0/8",
    // Reserved for future use / Class E (240.0.0.0/4)
    "240.0.0.0/4"
  })
  void shouldClassifyReservedIpv4CidrAsReserved(String literal) {
    assertThat(IpAddressClassifier.parse(literal).orElseThrow().isReserved()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // Default route, straddles everything
    "0.0.0.0/0",
    // Straddles RFC 1918 10/8 boundary
    "10.0.0.0/1",
    "10.0.0.0/7",
    // Straddles RFC 1918 172.16/12 boundary
    "172.0.0.0/8",
    // Straddles 192.0.0.0/24 boundary
    "192.0.0.0/16"
  })
  void shouldClassifyStraddlingCidrAsNotReserved(String literal) {
    assertThat(IpAddressClassifier.parse(literal).orElseThrow().isReserved()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "224.0.0.0, true",
    "239.255.255.255, true",
    "223.255.255.255, false",
    "240.0.0.0, false",
    "224.0.0.1/4, true",
    "224.0.0.1/3, false",
    "ff02::1/8, true",
    "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff, true",
    "feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff, false",
    "ff02::1/7, false",
    "::ffff:224.0.0.1, true"
  })
  void detectsMulticastWithoutChangingReservedClassification(String literal, boolean expected) {
    ParsedIp parsed = IpAddressClassifier.parse(literal).orElseThrow();
    assertThat(parsed.isMulticast()).isEqualTo(expected);
    if (expected) {
      assertThat(parsed.isReserved()).isFalse();
    }
  }

  @ParameterizedTest
  @CsvSource({
    // Loopback (::1/128) — the block is a singleton
    "::1,                     true",
    "::1/128,                 true",
    // Unique Local Addresses (fc00::/7) — middle of each half, plus block
    "fc99:1234::42,           true",
    "fd12:3456::1,            true",
    "fd00:ec2::254,           true", // AWS IMDSv6
    "fd20:ce::254,            true", // GCP IMDSv6
    "fc00::/7,                true",
    // Link-local (fe80::/10) — middle and boundary
    "fea0::42,                true",
    "fe80::1,                 true",
    // Documentation 2001:db8::/32 — middle and block
    "2001:db8:1234:5678::42,  true",
    "2001:db8::1,             true",
    "2001:db8::/32,           true",
    // Documentation 3fff::/20 — middle, upper boundary, and block
    "3fff:0800::42,           true",
    "3fff::1,                 true",
    "3fff:0fff::1,            true",
    "3fff::/20,               true",
    // Default route, straddles everything
    "::/0,                    false",
    // Ordinary public
    "2001::1,                 false",
    "2606:4700::1,            false",
    "4000::1,                 false"
  })
  void shouldClassifyIpv6Correctly(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isReserved(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // Not IP-shaped
    "",
    "unknown",
    "not.an.ip",
    // Bad IPv4 octet count
    "1.2.3",
    "1.2.3.4.5",
    // Bad IPv4 octet value
    "256.0.0.0",
    // Bad IPv4 CIDR mask
    "10.0.0.1/33",
    "10.0.0.1/-1",
    "10.0.0.1/abc",
    // Bad IPv6 CIDR mask
    "2001:db8::/129",
    // Bad IPv6 hex
    "gggg::1",
    // Malformed numeric IPv6
    "1:",
    ":::",
    "::fffff",
    "1:2:3:4:5:6:7",
    "1:2:3:4:5:6:7:8:9",
    "1:2:3:4::5:6:7:8",
    ":1:2:3:4:5:6:7",
    "1:2:3:4:5:6:7:",
    "1::2::3",
    "10.0.0.1/+8",
    "10.0.0.1/-0",
    "10.0.0.1/８",
    "10.0.0.1/9999999999999999999999999",
    "::1/+128",
    // Mixed notation must still be a complete numeric address
    "::ffff:256.1.2.3",
    "::ffff:1.2.3",
    "1:2:3:4:5:6:7:1.2.3.4",
    "1.2.3.4::",
    "1.2.3.+4",
    "1.2.3.٤",
    ".1:2",
    "1.2.3.4.",
    // Misplaced or IPv4 zones are rejected; the base address must be valid
    "fe80::1/64%eth0",
    "1.2.3.4%eth0",
    "gggg::1%eth0",
    "[fe80::1%eth0]"
  })
  void shouldClassifyMalformedAsUnparseable(String literal) {
    assertThat(IpAddressClassifier.parse(literal)).isEmpty();
    assertThat(IpAddressClassifier.isReserved(literal)).isFalse();
  }

  @Test
  void parseIpv4SingleAddressShouldReturnLongForValidAddress() {
    assertThat(IpAddressClassifier.parseIpv4SingleAddress("0.0.0.0")).hasValue(0L);
    assertThat(IpAddressClassifier.parseIpv4SingleAddress("10.0.0.1")).hasValue(0x0A000001L);
    assertThat(IpAddressClassifier.parseIpv4SingleAddress("255.255.255.255")).hasValue(0xFFFFFFFFL);
    assertThat(IpAddressClassifier.parseIpv4SingleAddress("0177.0.0.1")).hasValue(0xB1000001L);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
    // CIDR (not a single address)
    "10.0.0.0/8",
    // Bad IPv4 octet count
    "10.0.0",
    "1.2.3.4.5",
    // Bad IPv4 octet value
    "256.0.0.0",
    "10.0.0.-1",
    // Non-decimal octet
    "0x7f.0.0.1",
    "10.0.0.a",
    // Surrounding whitespace not accepted
    " 10.0.0.1",
    "10.0.0.1 ",
    // Not IP-shaped
    "not.an.ip",
    "",
    // Wrong family
    "::1"
  })
  void parseIpv4SingleAddressShouldReturnEmptyForInvalidInput(String literal) {
    assertThat(IpAddressClassifier.parseIpv4SingleAddress(literal)).isEqualTo(OptionalLong.empty());
  }

  @Test
  void isAddressRangeReservedShouldRecognizeRfc1918Ranges() {
    long start = IpAddressClassifier.parseIpv4SingleAddress("10.0.0.0").orElseThrow();
    long end = IpAddressClassifier.parseIpv4SingleAddress("10.255.255.255").orElseThrow();
    assertThat(IpAddressClassifier.isAddressRangeReserved(start, end)).isTrue();
  }

  @Test
  void isAddressRangeReservedShouldRejectStraddlingRange() {
    long start = IpAddressClassifier.parseIpv4SingleAddress("10.0.0.0").orElseThrow();
    long end = IpAddressClassifier.parseIpv4SingleAddress("11.0.0.0").orElseThrow();
    assertThat(IpAddressClassifier.isAddressRangeReserved(start, end)).isFalse();
  }

  @Test
  void isAddressRangeReservedShouldCoverFullClassEReservedBlock() {
    long start = IpAddressClassifier.parseIpv4SingleAddress("240.0.0.0").orElseThrow();
    long end = IpAddressClassifier.parseIpv4SingleAddress("255.255.255.255").orElseThrow();
    assertThat(IpAddressClassifier.isAddressRangeReserved(start, end)).isTrue();
  }

  @Test
  void isAddressRangeReservedShouldThrowOnInvertedRange() {
    long start = IpAddressClassifier.parseIpv4SingleAddress("10.0.0.100").orElseThrow();
    long end = IpAddressClassifier.parseIpv4SingleAddress("10.0.0.0").orElseThrow();
    assertThatThrownBy(() -> IpAddressClassifier.isAddressRangeReserved(start, end))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Inverted IPv4 range");
  }

  @ParameterizedTest
  @CsvSource({
    // IPv4 loopback (127.0.0.0/8) — middle, edges, and CIDR forms
    "127.99.42.7,      true",
    "127.0.0.1,        true",
    "127.255.255.255,  true",
    "127.0.0.0/8,      true",
    "127.0.0.0/16,     true",
    // IPv6 loopback (::1/128) — the block is a singleton
    "::1,              true",
    "::1/128,          true",
    "::1/127,          false",
    "::0.0.0.1,        true",
    "0:0:0:0:0:0:0.0.0.1, true",
    // Mapped and translated payloads, including native IPv6 prefix lengths
    "::ffff:127.0.0.1, true",
    "::FFFF:7f00:1, true",
    "0:0:0:0:ffff:0:7f00:1, true",
    "::ffff:127.12.34.56/104, true",
    "::ffff:0:127.0.0.1/104, true",
    "::ffff:127.0.0.1/103, false",
    "::ffff:127.0.0.1/95, false",
    "::ffff:0:127.0.0.1/95, false",
    "::127.0.0.1, false",
    // JDK accepts extra leading zeros
    "::00000:1, true",
    "::ffff:127.00.0.1, true",
    // Zones are opaque and preserve the CIDR suffix
    "::ffff:127.0.0.1%eth0/104, true",
    // Abbreviated inet_aton forms — not supported (classifier requires 4 octets)
    "127.1,            false", // FN: expands to 127.0.0.1
    "127.0.1,          false", // FN: expands to 127.0.0.1
    // Not loopback
    "10.0.0.1,         false",
    "8.8.8.8,          false",
    "fe80::1,          false",
    "192.0.2.10,       false",
    "bogus,            false",
    // Malformed but IP-shaped — exercise the parsed-null short-circuit
    "256.0.0.0,        false",
    "1:,               false"
  })
  void shouldDetectLoopback(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isLoopback(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // IPv4 link-local (169.254.0.0/16) — middle, edges, CIDR
    "169.254.99.42,    true",
    "169.254.169.254,  true",
    "169.254.0.0,      true",
    "169.254.255.255,  true",
    "169.254.0.0/16,   true",
    "169.254.10.0/24,  true",
    // IPv6 link-local (fe80::/10) — middle, edges
    "fea0::42,         true",
    "fe80::1,          true",
    "febf::1,          true",
    "fe80::/10,        true",
    "fe80::1%1, true",
    "fe80::1%, true",
    "fe80::1%/64, true",
    "fe80::1%interface-that-does-not-exist/64, true",
    "fe80::1%接口, true",
    "::ffff:169.254.1.2, true",
    // Not link-local
    "10.0.0.1,         false",
    "127.0.0.1,        false",
    "169.255.0.0,      false",
    "fec0::1,          false",
    "bogus,            false"
  })
  void shouldDetectLinkLocal(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isLinkLocal(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // RFC 1918 IPv4 — middle of each block
    "10.99.42.7,       true",
    "172.24.50.25,     true",
    "192.168.99.42,    true",
    // RFC 1918 IPv4 — edges (sanity)
    "10.0.0.1,         true",
    "10.255.255.255,   true",
    "172.16.0.0,       true",
    "172.31.255.255,   true",
    // RFC 1918 CIDR forms
    "10.0.0.0/8,       true",
    "172.16.0.0/12,    true",
    "192.168.0.0/16,   true",
    // IPv6 Unique Local (fc00::/7) — middle and edges
    "fd12:3456::1,     true",
    "fc99:1234::42,    true",
    "fc00::1,          true",
    "fc00::/7,         true",
    "::ffff:10.0.0.1, true",
    // Not private — adjacent or other reserved
    "127.0.0.1,        false",
    "169.254.169.254,  false",
    "192.0.2.10,       false",
    "100.64.0.0,       false",
    "8.8.8.8,          false",
    "::1,              false",
    "fe80::1,          false",
    "bogus,            false"
  })
  void shouldDetectPrivate(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isPrivate(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // TEST-NET-1/2/3 — middle of each /24
    "192.0.2.128,             true",
    "198.51.100.128,          true",
    "203.0.113.128,           true",
    "192.0.2.0/24,            true",
    // IPv6 2001:db8::/32 — middle and the block itself
    "2001:db8:1234:5678::42,  true",
    "2001:db8::1,             true",
    "2001:db8::/32,           true",
    // IPv6 3fff::/20 — middle, upper-boundary, CIDR
    "3fff:0800::42,           true",
    "3fff::1,                 true",
    "3fff:0fff::1,            true",
    "3fff::/20,               true",
    "2001:db8::127.0.0.1, true",
    "::ffff:203.0.113.42/120, true",
    "64:ff9b::192.0.2.1, false",
    "3fff:1000::1, false",
    "3fff::1/19, false",
    // Not documentation
    "10.0.0.1,                false",
    "127.0.0.1,                false",
    "192.0.0.0,                false",
    "192.0.3.0,                false",
    "4000::1,                  false",
    "bogus,                    false"
  })
  void shouldDetectDocumentation(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isDocumentation(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // Singletons
    "0.0.0.0,          true",
    "0.0.0.0/32,       true",
    "::,               true",
    "::/128,           true",
    "::0.0.0.0,       true",
    "::ffff:0:0.0.0.0, true",
    // Not any-address — these belong to other categories
    "0.0.0.0/0,        false",
    "::/0,             false",
    "0.0.0.1,          false",
    "0.0.0.0/8,        false",
    "0.0.0.0/31,       false",
    "::1,              false",
    "10.0.0.1,         false",
    "bogus,            false",
    // Malformed but IP-shaped — exercise the parsed-null short-circuit
    "256.0.0.0,        false",
    "1:,               false"
  })
  void shouldDetectAnyAddress(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isAnyAddress(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "255.255.255.255,     true",
    "255.255.255.255/32,  true",
    "::ffff:0:255.255.255.255, true",
    // Not broadcast
    "255.255.255.254,     false",
    "255.255.255.255/31,  false",
    "240.0.0.0/4,         false",
    "0.0.0.0,             false",
    "::,                  false",
    "::ffff:ffff:ffff,    true",
    "bogus,               false",
    // Malformed IPv4 — exercise the parsed-null short-circuit
    "256.0.0.0,           false"
  })
  void shouldDetectBroadcast(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isBroadcast(literal)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // /0-mask CIDR (any starting address normalizes to the same range)
    "0.0.0.0/0,        true",
    "::/0,             true",
    "0::0/0,           true",
    "10.0.0.0/0,       true",
    "::ffff:127.0.0.1/0, true",
    "::ffff:0:127.0.0.1/0, true",
    "::%eth0/0, true",
    "::ffff:8.8.8.8/96, false",
    "::ffff:0:8.8.8.8/96, false",
    // Not unrestricted
    "0.0.0.0,          false",
    "::,               false",
    "0.0.0.0/32,       false",
    "::/128,           false",
    "0.0.0.0/8,        false",
    "10.0.0.0/8,       false",
    "bogus,            false",
    // Malformed but IP-shaped — exercise the parsed-null short-circuit
    "256.0.0.0/0,      false",
    "1:/0,             false"
  })
  void shouldDetectUnrestrictedCidr(String literal, boolean expected) {
    assertThat(IpAddressClassifier.isUnrestrictedCidr(literal)).isEqualTo(expected);
  }
}
