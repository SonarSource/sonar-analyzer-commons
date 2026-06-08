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

  // Make this a parameterized test for better maintainability

  @Test
  public void https_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("https://example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("sftp://example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("not-a-url")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls(null)).isTrue();
  }

  // --- Ordinary public HTTP → not safe ---------------------------------------------------

  @Test
  public void regular_http_url_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://acme.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://api.acme.com/v1/users")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("ftp://files.acme.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("HTTP://ACME.COM")).isFalse();
  }

  // --- Loopback --------------------------------------------------------------------------

  @Test
  public void localhost_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://localhost")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://localhost:8080/api")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://LOCALHOST:3000")).isTrue();
  }

  @Test
  public void ipv4_loopback_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://127.0.0.1:8080")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://127.255.255.254/path")).isTrue();
  }

  @Test
  public void ipv6_loopback_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://::1/path")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://0:0:0:0:0:0:0:1")).isTrue();
  }

  // --- Cloud IMDS ------------------------------------------------------------------------

  @Test
  public void link_local_ipv4_range_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://169.254.169.254/latest/meta-data/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://169.254.169.254/latest/api/token")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://169.254.0.1/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://169.254.255.254/")).isTrue();
  }

  @Test
  public void aws_imds_ipv6_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://[fd00:ec2::254]/latest/meta-data/")).isTrue();
  }

  @Test
  public void azure_wireserver_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://168.63.129.16/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://168.63.129.16:32526/vmAgentLog")).isTrue();
  }

  @Test
  public void alibaba_imds_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://100.100.100.200/latest/meta-data/")).isTrue();
  }

  @Test
  public void gcp_imds_hostname_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://metadata.google.internal/computeMetadata/v1")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://metadata.internal/")).isTrue();
  }

  // --- Docker ----------------------------------------------------------------------------

  @Test
  public void docker_internal_hostnames_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://host.docker.internal:8085/metrics")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://gateway.docker.internal")).isTrue();
  }

  // --- Kubernetes ------------------------------------------------------------------------

  @Test
  public void kubernetes_cluster_local_dns_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://vault.vault.svc.cluster.local:8200")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://auth-service.prod.svc.cluster.local:3001/auth")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://otel-collector.observability.svc.cluster.local:4317")).isTrue();
  }

  // --- XML / JSON-LD namespace URI authorities -------------------------------------------

  @Test
  public void w3c_namespace_uris_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org/2001/XMLSchema")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org/2005/sparql-results#")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org/2005/Atom")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org/1999/xhtml")).isTrue();
  }

  @Test
  public void android_namespace_uri_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://schemas.android.com/apk/res/android")).isTrue();
  }

  @Test
  public void soap_namespace_uri_is_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://schemas.xmlsoap.org/soap/envelope/")).isTrue();
  }

  @Test
  public void other_well_known_namespace_uris_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://schemas.microsoft.com/winfx/2006/xaml")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.sap.com/adt/core")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.opengis.net/gml")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://hl7.org/fhir")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://unitsofmeasure.org")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://purl.org/dc/elements/1.1/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://xmlns.com/foaf/0.1/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://json-ld.org/contexts/person.jsonld")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://schema.org/Person")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.springframework.org/schema/beans")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://maven.apache.org/POM/4.0.0")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://dublincore.org/documents/dces/")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://ogp.me/ns#")).isTrue();
  }

  // --- IANA-reserved documentation domains (RFC 2606) -----------------------------------

  @Test
  public void example_domains_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://example.net")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://example.org")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://example.com/path/to/resource")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("ftp://example.com/file")).isTrue();
  }

  @Test
  public void example_domain_subdomains_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://api.example.com/v1/users")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://something.example.com")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://sub.example.org/path")).isTrue();
  }

  @Test
  public void reserved_tlds_are_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://myservice.example")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://api.myservice.example/v1")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://myapi.test")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://db.myapi.test:5432")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://myapp.localhost")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://service.myapp.localhost:8080")).isTrue();
  }

  @Test
  public void example_domain_lookalike_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://notexample.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://example.com.evil.com")).isFalse();
  }

  // --- Host must be an exact match, not a prefix -----------------------------------------

  @Test
  public void safe_host_prefix_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://localhost.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://127.0.0.1.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://169.254.169.254.evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://metadata.google.internal.evil.com")).isFalse();
  }

  @Test
  public void safe_namespace_authority_prefix_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org.evil.com/x")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://schema.org.evil.com/Person")).isFalse();
  }

  @Test
  public void userinfo_spoofing_is_not_safe() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://www.w3.org@evil.com")).isFalse();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://localhost@evil.com")).isFalse();
  }

  // --- Case insensitivity and whitespace -------------------------------------------------

  @Test
  public void matching_is_case_insensitive() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("HTTP://LOCALHOST:8080")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("FTP://127.0.0.1")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("HTTP://metadata.google.internal")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("http://WWW.W3.ORG/2001/XMLSchema")).isTrue();
  }

  @Test
  public void surrounding_whitespace_is_stripped() {
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("  http://localhost  ")).isTrue();
    assertThat(CleartextProtocolFilter.isSafeWithoutTls("  http://example.com  ")).isFalse();
  }
}
