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

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CleartextProtocolFilterTest {

  @Test
  void exportedBaselineExclusionsAreUnmodifiable() {
    var hostPatterns = CleartextProtocolFilter.exportHostPatterns();
    var safeUrlPrefixes = CleartextProtocolFilter.exportSafeUrlPrefixes();

    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(hostPatterns::clear);
    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(safeUrlPrefixes::clear);
  }

  @Test
  void getCleartextProtocolsIsUnmodifiable() {
    var protocols = CleartextProtocolFilter.getCleartextProtocols();
    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(() -> protocols.add("custom://"));
  }

  @Test
  void getIssueMessageReturnsFormattedMessage() {
    assertThat(CleartextProtocolFilter.getIssueMessage("http"))
      .hasValue("Using HTTP protocol is insecure. Use HTTPS instead.");
    assertThat(CleartextProtocolFilter.getIssueMessage("ftp"))
      .hasValue("Using FTP protocol is insecure. Use SFTP, SCP or FTPS instead.");
    assertThat(CleartextProtocolFilter.getIssueMessage("telnet"))
      .hasValue("Using Telnet protocol is insecure. Use SSH instead.");
    assertThat(CleartextProtocolFilter.getIssueMessage("gopher"))
      .hasValue("Using Gopher protocol is insecure. Use HTTPS instead.");
  }

  @Test
  void getIssueMessageReturnsEmptyForUnknownScheme() {
    assertThat(CleartextProtocolFilter.getIssueMessage("https")).isEmpty();
    assertThat(CleartextProtocolFilter.getIssueMessage("unknown")).isEmpty();
  }

  @Test
  void getIssueMessageIsPresentForAllCleartextProtocols() {
    CleartextProtocolFilter.getCleartextProtocols()
      .forEach(prefix -> assertThat(CleartextProtocolFilter.getIssueMessage(prefix.replace("://", "")))
        .as("missing issue message for scheme: %s", prefix)
        .isPresent());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("safeUrls")
  void safeUrl(String url) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("notSafeUrls")
  void notSafeUrl(String url) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(url)).isFalse();
  }

  static Stream<String> safeUrls() throws IOException, ParseException {
    return samples("safeUrls");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("safeUris")
  void safeUri(URI uri) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(uri)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("notSafeUris")
  void notSafeUri(URI uri) {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(uri)).isFalse();
  }

  static Stream<URI> safeUris() {
    return Stream.of(
      // Non-cleartext scheme
      URI.create("https://acme.com"),
      URI.create("sftp://acme.com"),
      URI.create("wss://acme.com"),
      URI.create("ircs://acme.com"),
      // New cleartext schemes — safe internal/documentation host
      URI.create("ws://localhost"),
      URI.create("ws://example.com"),
      URI.create("telnet://127.0.0.1"),
      // Loopback
      URI.create("http://localhost:8080/api"),
      URI.create("http://127.0.0.1/path"),
      // Cloud IMDS
      URI.create("http://169.254.169.254/latest/meta-data/"),
      URI.create("http://metadata.google.internal/computeMetadata/v1"),
      // Kubernetes
      URI.create("http://vault.vault.svc.cluster.local:8200"),
      // Namespace URI authority
      URI.create("http://www.w3.org/2001/XMLSchema"),
      URI.create("http://schema.org/Person"),
      // Documentation domain
      URI.create("http://example.com/path"),
      URI.create("http://api.example.com/v1"),
      URI.create("http://myapi.test"),
      // Single-label hostname
      URI.create("http://my-service/path")
    );
  }

  static Stream<URI> notSafeUris() {
    return Stream.of(
      // Ordinary public HTTP/FTP
      URI.create("http://acme.com"),
      URI.create("ftp://files.acme.com/data"),
      // Additional cleartext schemes
      URI.create("ws://acme.com"),
      URI.create("telnet://acme.com"),
      URI.create("rtmp://acme.com/live"),
      URI.create("gopher://acme.com"),
      URI.create("irc://acme.com/channel"),
      // Userinfo spoofing — getHost() returns the real host
      URI.create("http://localhost@evil.com"),
      URI.create("http://www.w3.org@evil.com"),
      // Opaque URI — http scheme but no host component (getHost() == null)
      URI.create("http:not-hierarchical"),
      // Single-label host exclusions — IP addresses, not DNS names
      URI.create("http://2130706433"),
      // 8.8.8.8 as hexadecimal literal
      URI.create("http://0x08080808"),
      URI.create("http://[2001:db8::1]"),
      // Credentials before a known-identifier host so the prefix check does not match
      URI.create("http://user:pass@jabber.org/protocol/muc")
    );
  }

  static Stream<String> notSafeUrls() throws IOException, ParseException {
    return samples("unsafeUrls");
  }

  private static Stream<String> samples(String group) throws IOException, ParseException {
    try (var reader = new InputStreamReader(Objects.requireNonNull(CleartextProtocolFilterTest.class
      .getResourceAsStream("/org/sonarsource/analyzer/commons/appsec/cleartext-protocol-corpus.json")), StandardCharsets.UTF_8)) {
      var corpus = (JSONObject) new JSONParser().parse(reader);
      var samples = (List<?>) corpus.get(group);
      return samples.stream().map(String.class::cast);
    }
  }

}
