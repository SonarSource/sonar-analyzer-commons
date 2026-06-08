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
package org.sonarsource.analyzer.commons;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CleartextProtocolFilterTest {

  // --- Not a cleartext URL → always safe -------------------------------------------------

  @Test
  public void https_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("https://example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("sftp://example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("not-a-url")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe(null)).isTrue();
  }

  // --- Ordinary public HTTP → not safe ---------------------------------------------------

  @Test
  public void regular_http_url_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://example.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://api.example.com/v1/users")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("ftp://files.example.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("HTTP://EXAMPLE.COM")).isFalse();
  }

  // --- Loopback --------------------------------------------------------------------------

  @Test
  public void localhost_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://localhost")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://localhost:8080/api")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://LOCALHOST:3000")).isTrue();
  }

  @Test
  public void ipv4_loopback_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://127.0.0.1:8080")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://127.255.255.254/path")).isTrue();
  }

  @Test
  public void ipv6_loopback_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://::1/path")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://0:0:0:0:0:0:0:1")).isTrue();
  }

  // --- Cloud IMDS ------------------------------------------------------------------------

  @Test
  public void aws_imds_ipv4_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://169.254.169.254/latest/meta-data/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://169.254.169.254/latest/api/token")).isTrue();
  }

  @Test
  public void aws_imds_ipv6_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://[fd00:ec2::254]/latest/meta-data/")).isTrue();
  }

  @Test
  public void gcp_imds_hostname_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://metadata.google.internal/computeMetadata/v1")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://metadata.internal/")).isTrue();
  }

  // --- Docker ----------------------------------------------------------------------------

  @Test
  public void docker_internal_hostnames_are_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://host.docker.internal:8085/metrics")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://gateway.docker.internal")).isTrue();
  }

  // --- Kubernetes ------------------------------------------------------------------------

  @Test
  public void kubernetes_cluster_local_dns_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://vault.vault.svc.cluster.local:8200")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://auth-service.prod.svc.cluster.local:3001/auth")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://otel-collector.observability.svc.cluster.local:4317")).isTrue();
  }

  // --- XML / JSON-LD namespace URI authorities -------------------------------------------

  @Test
  public void w3c_namespace_uris_are_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org/2001/XMLSchema")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org/2005/sparql-results#")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org/2005/Atom")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org/1999/xhtml")).isTrue();
  }

  @Test
  public void android_namespace_uri_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://schemas.android.com/apk/res/android")).isTrue();
  }

  @Test
  public void soap_namespace_uri_is_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://schemas.xmlsoap.org/soap/envelope/")).isTrue();
  }

  @Test
  public void other_well_known_namespace_uris_are_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://schemas.microsoft.com/winfx/2006/xaml")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.sap.com/adt/core")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.opengis.net/gml")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://hl7.org/fhir")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://unitsofmeasure.org")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://purl.org/dc/elements/1.1/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://xmlns.com/foaf/0.1/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://json-ld.org/contexts/person.jsonld")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://schema.org/Person")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://www.springframework.org/schema/beans")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://maven.apache.org/POM/4.0.0")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://dublincore.org/documents/dces/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://ogp.me/ns#")).isTrue();
  }

  // --- Host must be an exact match, not a prefix -----------------------------------------

  @Test
  public void safe_host_prefix_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://localhost.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://127.0.0.1.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://169.254.169.254.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://metadata.google.internal.evil.com")).isFalse();
  }

  @Test
  public void safe_namespace_authority_prefix_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org.evil.com/x")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://schema.org.evil.com/Person")).isFalse();
  }

  @Test
  public void userinfo_spoofing_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafe("http://www.w3.org@evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafe("http://localhost@evil.com")).isFalse();
  }

  // --- Case insensitivity and whitespace -------------------------------------------------

  @Test
  public void matching_is_case_insensitive() {
    assertThat(CleartextProtocolFilter.isSafe("HTTP://LOCALHOST:8080")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("FTP://127.0.0.1")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("HTTP://metadata.google.internal")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("http://WWW.W3.ORG/2001/XMLSchema")).isTrue();
  }

  @Test
  public void surrounding_whitespace_is_stripped() {
    assertThat(CleartextProtocolFilter.isSafe("  http://localhost  ")).isTrue();
    assertThat(CleartextProtocolFilter.isSafe("  http://example.com  ")).isFalse();
  }
}
