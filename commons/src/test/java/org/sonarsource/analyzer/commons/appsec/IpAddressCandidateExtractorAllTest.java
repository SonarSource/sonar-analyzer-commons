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

  @Test
  void findsQuotedValuesAfterLeadingWhitespace() {
    assertThat(IpAddressCandidateExtractor.extractAll(" \t\"10.0.0.1\" ('10.0.0.2')"))
      .containsExactly("10.0.0.1", "10.0.0.2");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "/registry/ipaddresses/192.168.2.3 | 192.168.2.3",
    "connect to 10.0.0.1, 10.0.0.2 | 10.0.0.1,10.0.0.2",
    "  10.0.0.1 10.0.0.2;10.0.0.1 | 10.0.0.1,10.0.0.2,10.0.0.1",
    "10.0.0.1\u00a010.0.0.2 | 10.0.0.1,10.0.0.2",
    "address=10.0.0.1 | 10.0.0.1",
    "(10.0.0.1);\"10.0.0.2\" | 10.0.0.1,10.0.0.2",
    "'10.0.0.1' | 10.0.0.1",
    "10.0.0.1! | 10.0.0.1",
    "2001:DB8:0:0::AbCd ::1 :: ::00000:2 | 2001:DB8:0:0::AbCd,::1,::,::00000:2",
    "::ffff:192.0.2.1,2001:db8::192.0.2.2 | ::ffff:192.0.2.1,2001:db8::192.0.2.2",
    "[::ffff:192.0.2.1]:8080 | ::ffff:192.0.2.1",
    "fe80::1%eth0 fe80::1% fe80::1%接口 | fe80::1%eth0,fe80::1%,fe80::1%接口",
    "fe80::1%eth!0 [fe80::1%eth~0]:80 | fe80::1%eth!0,fe80::1%eth~0",
    "[fe80::1%interface-that-does-not-exist]:80 | fe80::1%interface-that-does-not-exist",
    "2001:db8::1:8080 | 2001:db8::1:8080",
    "192.0.2.1:0;192.0.2.2:65535;192.0.2.1:00080 | 192.0.2.1,192.0.2.2,192.0.2.1",
    "[2001:db8::1]:0 [2001:db8::2]:65535 | 2001:db8::1,2001:db8::2",
    "http://192.0.2.1:80/10.0.0.1?next=10.0.0.2#10.0.0.3 | 192.0.2.1,10.0.0.1,10.0.0.2,10.0.0.3",
    "see https://example.org/path/10.0.0.1?q=10.0.0.2&peer=10.0.0.3#10.0.0.4 | 10.0.0.1,10.0.0.2,10.0.0.3,10.0.0.4",
    "git+ssh://[2001:db8::1]:22/192.0.2.1 | 2001:db8::1,192.0.2.1",
    "custom-scheme.v1://192.0.2.1/7 | 192.0.2.1",
    "//user:pass@[2001:db8::1]/192.0.2.1 | 2001:db8::1,192.0.2.1",
    "http://192.0.2.1:192.0.2.2@example.org/10.0.0.1 | 10.0.0.1",
    "redirect=https://192.0.2.1@example.org/10.0.0.1 | 10.0.0.1",
    "http://192.0.2.1/path@192.0.2.2 | 192.0.2.1,192.0.2.2",
    "https://example.org/path//192.0.2.1@label/10.0.0.1 | 192.0.2.1,10.0.0.1",
    "/registry//192.0.2.1@label | 192.0.2.1",
    "https://example.org?q=http://192.0.2.1@host/10.0.0.1 | 10.0.0.1",
    "http://192.0.2.1,https://192.0.2.2;http://[2001:db8::1]/ | 192.0.2.1,192.0.2.2,2001:db8::1",
    "192.0.2.1/0;192.0.2.1/032;2001:db8::1/128 | 192.0.2.1/0,192.0.2.1/032,2001:db8::1/128",
    "value=fe80::1%eth0/064 | fe80::1%eth0/064",
    "[2001:db8::1]/64 | 2001:db8::1/64",
    "allow=10.0.0.0/8&x=1 | 10.0.0.0/8",
    "10.0.0.0/8! | 10.0.0.0/8",
    "10.0.0.0/8?note | 10.0.0.0/8",
    "10.0.0.0/8#note | 10.0.0.0/8",
    "10.0.0.0/8=value | 10.0.0.0/8",
    "allow=10.0.0.0/8&peer=2001:db8::/032#10.0.0.0/8 | 10.0.0.0/8,2001:db8::/032,10.0.0.0/8",
    "[fe80::1%eth0]/64! | fe80::1%eth0/64",
    "10.0.0.0/8&path=/other/192.0.2.1/24 | 10.0.0.0/8,192.0.2.1",
    "10.0.0.0/33&peer=192.0.2.1/24 | 192.0.2.1/24",
    "10.0.0.0/8/ | 10.0.0.0",
    "192.0.2.1/path/10.0.0.1/33 | 192.0.2.1,10.0.0.1",
    "192.0.2.1/24/10.0.0.1 | 192.0.2.1,10.0.0.1",
    "192.0.2.1/10.0.0.1 | 192.0.2.1,10.0.0.1",
    "192.0.2.1:80/33 [2001:db8::1]:80/129 | 192.0.2.1,2001:db8::1",
    "https://host/192.0.2.1/33?q=2001:db8::1/129 | 192.0.2.1,2001:db8::1",
    "/192.0.2.1/33 | 192.0.2.1",
    "192.0.2.1/path | 192.0.2.1",
    "invalid 999.1.2.3 10.0.0.1 1.2.3.4.5 ::ffff:10.0.0.2 | 10.0.0.1,::ffff:10.0.0.2",
    "[2001:db8::1 10.0.0.1;2001:db8::2]:bad,10.0.0.2 | 10.0.0.1,10.0.0.2",
    "http://[2001:db8::1/10.0.0.1?peer=10.0.0.2 | 10.0.0.1,10.0.0.2",
    "192.0.2.1:bad/10.0.0.1 | 10.0.0.1",
    "0.0.0.0 127.0.0.1 192.168.1.1 8.8.8.8 | 0.0.0.0,127.0.0.1,192.168.1.1,8.8.8.8"
  })
  void findsAllAddresses(String content, String expected) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).containsExactly(expected.split(","));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "", "no addresses", "example.org", "https://example.org/path",
    "http://192.0.2.1:pass@example.org", "//192.0.2.1@example.org",
    "prefix192.0.2.1", "192.0.2.1suffix", "a-192.0.2.1", "192.0.2.1-a",
    "a_192.0.2.1", "192.0.2.1_a", "é192.0.2.1", "192.0.2.1接口",
    "v1.2.3.4", "1.2.3.4.5", ".192.0.2.1", "192.0.2.1.",
    "999.1.2.3", "192.168.001.1", "1234.1.2.3", "1.2.3", "１.2.3.4",
    "1:2:3:4:5:6:7:8:9", "2001:db8:::1", "gggg::1", "2001:db8::1.",
    "::ffff:999.1.2.3", "::ffff:192.168.001.1", "2001:db8:::192.0.2.1",
    "192.0.2.1%eth0", "[192.0.2.1]", "[192.0.2.1]:80",
    "[2001:db8::1", "2001:db8::1]", "[[2001:db8::1]]",
    "[2001:db8::1]extra", "[2001:db8::1]80", "fe80::1%eth[0", "fe80::1%eth0]",
    "192.0.2.1:", "192.0.2.1:bad", "192.0.2.1:-1", "192.0.2.1:+80",
    "192.0.2.1:８０", "192.0.2.1:65536", "192.0.2.1:000080", "192.0.2.1:80:90",
    "[2001:db8::1]:", "[2001:db8::1]:bad", "[2001:db8::1]:-1", "[2001:db8::1]:+80",
    "[2001:db8::1]:８０", "[2001:db8::1]:65536", "[2001:db8::1]:000080",
    "192.0.2.1/33", "192.0.2.1/1000", "192.0.2.1/-1", "192.0.2.1/+24",
    "192.0.2.1/３２", "192.0.2.1/24x", "2001:db8::1/129", "[2001:db8::1]/129",
    "Allowed range is 10.0.0.0/8.", "2001:db8::/32.", "[fe80::1%eth0]/64.",
    "10.0.0.0/33!", "10.0.0.0/8x&x=1", "2001:db8::/129#note",
    "http://192.0.2.1:bad/path", "http://[2001:db8::1]:65536/path"
  })
  void rejectsMalformedAddressesAndEndpoints(String content) {
    assertThat(IpAddressCandidateExtractor.extractAll(content)).isEmpty();
    assertThat(IpAddressCandidateExtractor.extractAll(content + " 10.0.0.1")).containsExactly("10.0.0.1");
  }
}
