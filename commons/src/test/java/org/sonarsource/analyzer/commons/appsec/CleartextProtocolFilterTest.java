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

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CleartextProtocolFilterTest {

  @Test
  void getCleartextProtocolsReturnsExpectedSet() {
    assertThat(CleartextProtocolFilter.getCleartextProtocols())
      .containsExactlyInAnyOrder(
        "http://", "ftp://", "ws://", "telnet://", "gopher://", "tftp://",
        "smtp://", "ldap://", "imap://", "pop3://", "amqp://", "mqtt://",
        "sip://", "rtmp://", "irc://", "nntp://", "stomp://");
  }

  @Test
  void getCleartextProtocolsIsUnmodifiable() {
    var protocols = CleartextProtocolFilter.getCleartextProtocols();
    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(() -> protocols.add("custom://"));
  }

  @Test
  void getAlternativeProtocolsReturnsExpectedMappings() {
    var alternatives = CleartextProtocolFilter.getAlternativeProtocols();
    assertThat(alternatives)
      .containsEntry("http",   "https")
      .containsEntry("ftp",    "sftp, scp or ftps")
      .containsEntry("ws",     "wss")
      .containsEntry("telnet", "ssh")
      .containsEntry("gopher", "https")
      .containsEntry("tftp",   "sftp")
      .containsEntry("smtp",   "smtps")
      .containsEntry("ldap",   "ldaps")
      .containsEntry("imap",   "imaps")
      .containsEntry("pop3",   "pop3s")
      .containsEntry("amqp",   "amqps")
      .containsEntry("mqtt",   "mqtts")
      .containsEntry("sip",    "sips")
      .containsEntry("rtmp",   "rtmps")
      .containsEntry("irc",    "ircs")
      .containsEntry("nntp",   "nntps")
      .containsEntry("stomp",  "stomps");
  }

  @Test
  void getAlternativeProtocolsIsUnmodifiable() {
    var alternatives = CleartextProtocolFilter.getAlternativeProtocols();
    assertThatExceptionOfType(UnsupportedOperationException.class)
      .isThrownBy(() -> alternatives.put("custom", "customs"));
  }

  @Test
  void cleartextProtocolKeysMatchAlternativeProtocolKeys() {
    var protocols = CleartextProtocolFilter.getCleartextProtocols();
    var alternativeKeys = CleartextProtocolFilter.getAlternativeProtocols().keySet();
    assertThat(protocols)
      .as("getCleartextProtocols() must be exactly the keySet of getAlternativeProtocols() with :// appended")
      .hasSameSizeAs(alternativeKeys)
      .allSatisfy(p -> assertThat(alternativeKeys).contains(p.replace("://", "")));
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

  static Stream<String> safeUrls() {
    return Stream.of(
      // Not a cleartext URL — always safe regardless of host
      "https://example.com",
      "sftp://example.com",
      "wss://acme.com",
      "ircs://acme.com",
      "not-a-url",
      "",

      // Loopback
      "http://localhost",
      "http://localhost:8080/api",
      "http://LOCALHOST:3000",
      "http://127.0.0.1:8080",
      "http://127.255.255.254/path",
      "http://[::1]/path",
      "http://[0:0:0:0:0:0:0:1]",

      // Cloud IMDS — link-local range (169.254.0.0/16) and named endpoints
      "http://169.254.169.254/latest/meta-data/",
      "http://169.254.169.254/latest/api/token",
      "http://169.254.0.1/",
      "http://169.254.255.254/",
      "http://[fd00:ec2::254]/latest/meta-data/",
      "http://168.63.129.16/",
      "http://168.63.129.16:32526/vmAgentLog",
      "http://100.100.100.200/latest/meta-data/",
      "http://metadata.google.internal/computeMetadata/v1",
      "http://metadata.internal/",

      // Docker
      "http://host.docker.internal:8085/metrics",
      "http://gateway.docker.internal",

      // Kubernetes
      "http://vault.vault.svc.cluster.local:8200",
      "http://auth-service.prod.svc.cluster.local:3001/auth",
      "http://otel-collector.observability.svc.cluster.local:4317",

      // Namespace URI authorities
      "http://www.w3.org/2001/XMLSchema",
      "http://www.w3.org/2005/sparql-results#",
      "http://www.w3.org/2005/Atom",
      "http://www.w3.org/1999/xhtml",
      "http://schemas.android.com/apk/res/android",
      "http://schemas.xmlsoap.org/soap/envelope/",
      "http://schemas.microsoft.com/winfx/2006/xaml",
      "http://www.sap.com/adt/core",
      "http://www.opengis.net/gml",
      "http://hl7.org/fhir",
      "http://unitsofmeasure.org",
      "http://purl.org/dc/elements/1.1/",
      "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
      "http://xmlns.com/foaf/0.1/",
      "http://json-ld.org/contexts/person.jsonld",
      "http://schema.org/Person",
      "http://www.springframework.org/schema/beans",
      "http://maven.apache.org/POM/4.0.0",
      "http://dublincore.org/documents/dces/",
      "http://ogp.me/ns#",

      // IANA-reserved documentation domains
      "http://example.com",
      "http://example.net",
      "http://example.org",
      "http://example.com/path/to/resource",
      "ftp://example.com/file",
      "http://api.example.com/v1/users",
      "http://something.example.com",
      "http://sub.example.org/path",
      "http://myservice.example",
      "http://api.myservice.example/v1",
      "http://myapi.test",
      "http://db.myapi.test:5432",
      "http://myapp.localhost",
      "http://service.myapp.localhost:8080",

      // New cleartext schemes — safe because of internal/documentation host
      "ws://localhost",
      "ws://example.com",
      "telnet://127.0.0.1",
      "rtmp://example.com/live",
      "tftp://example.com/file",
      "gopher://example.com",
      "irc://localhost/channel",
      "smtp://localhost:25",
      "smtp://mail.example.com",
      "ldap://localhost/dc=example,dc=com",
      "ldap://127.0.0.1:389",
      "amqp://localhost/vhost",
      "amqp://example.com/queue",
      "mqtt://localhost:1883",
      "mqtt://broker.example.com",
      "imap://localhost:143",
      "imap://mail.example.com",
      "pop3://localhost:110",
      "pop3://mail.example.com",
      "nntp://news.example.com",
      "sip://localhost",
      "sip://example.com",
      "stomp://localhost:61613",
      "stomp://example.com",

      // Credentials (userinfo) with safe host — real host after @ is what matters
      "http://user:password@localhost",
      "http://user:pass@example.com",
      // Template placeholder in password — URI parsing fails, lenient fallback used
      "http://user:${password}@localhost",

      // Template placeholders in port — URI parsing fails, lenient fallback recovers safe host
      "http://localhost:${port}/api",
      "ftp://files.example.com:${port}/data",
      "ws://localhost:${port}/socket",

      // Underscores in hostnames — URI.getHost() returns null, lenient fallback used
      "http://my_service.svc.cluster.local:8200",
      "http://my_app.example.com/path",

      // Case insensitivity and surrounding whitespace
      "HTTP://LOCALHOST:8080",
      "FTP://127.0.0.1",
      "HTTP://metadata.google.internal",
      "http://WWW.W3.ORG/2001/XMLSchema",
      "  http://localhost  "
    );
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
      URI.create("http://myapi.test")
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
      URI.create("http:not-hierarchical")
    );
  }

  static Stream<String> notSafeUrls() {
    return Stream.of(
      // Ordinary public HTTP/FTP
      "http://acme.com",
      "http://api.acme.com/v1/users",
      "ftp://files.acme.com",
      "HTTP://ACME.COM",

      // Additional cleartext schemes
      "ws://acme.com",
      "telnet://acme.com",
      "rtmp://acme.com/live",
      "tftp://acme.com/file",
      "gopher://acme.com",
      "irc://acme.com/channel",
      "smtp://mail.acme.com",
      "ldap://acme.com/dc=acme,dc=com",
      "amqp://acme.com/vhost",
      "mqtt://broker.acme.com",
      "imap://mail.acme.com",
      "pop3://mail.acme.com",
      "nntp://news.acme.com",
      "sip://acme.com",
      "stomp://acme.com",

      // Documentation domain lookalikes
      "http://notexample.com",
      "http://example.com.evil.com",

      // Safe host used as prefix — must not match
      "http://localhost.evil.com",
      "http://127.0.0.1.evil.com",
      "http://169.254.169.254.evil.com",
      "http://metadata.google.internal.evil.com",
      "http://www.w3.org.evil.com/x",
      "http://schema.org.evil.com/Person",

      // Userinfo — safe-looking host before @ must not grant safety
      "http://www.w3.org@evil.com",
      "http://localhost@evil.com",
      // Template placeholder in password — URI parsing fails, lenient fallback used; real host is acme.com
      "http://localhost:${password}@acme.com",
      // Underscore in real host — URI.getHost() returns null, lenient fallback used; real host is acme_corp.com
      "http://localhost:password@acme_corp.com",

      // Surrounding whitespace with a non-safe URL
      "  http://acme.com  ",

      // Malformed — cannot be parsed as a URI (URISyntaxException → false)
      "http://foo bar.com",
      "http://[unclosed"
    );
  }
}
