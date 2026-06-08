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

import java.util.regex.Pattern;

/**
 * Determines whether a cleartext-protocol URL should be considered safe and
 * suppressed by S5332 ("Using clear-text protocols is security-sensitive").
 * <p>
 * Three categories of safe URLs are recognised:
 * <ul>
 *   <li><b>Internal hosts</b> — loopback addresses, cloud instance metadata
 *       endpoints, Docker-internal hostnames, and Kubernetes cluster-internal service
 *       DNS. None of these are reachable from the public internet.</li>
 *   <li><b>Namespace URI authorities</b> — well-known authorities (W3C, Android,
 *       OASIS, HL7, …) whose {@code http://} URIs are opaque namespace identifiers
 *       used in XML, JSON-LD, RDF, and similar formats. They carry a protocol prefix
 *       by convention and do not imply an actual HTTP connection.</li>
 *   <li><b>IANA-reserved documentation domains</b> — {@code example.com},
 *       {@code example.net}, and {@code example.org} (RFC 2606) and their subdomains,
 *       plus the reserved TLDs {@code .example}, {@code .test} (RFC 2606), and
 *       {@code .localhost} (RFC 6761). Their use in source code is almost always a
 *       placeholder, not a real connection.</li>
 * </ul>
 * <p>
 * Usage: extract the raw URL string from the AST node and call {@link #isSafeWithoutTls(String)}
 * before raising an issue. Patterns that require AST context (e.g. the URL appears
 * inside {@code startsWith()}, or the file is a test) cannot be handled here and must
 * be implemented per analyzer on top of this filter.
 */
public final class CleartextProtocolFilter {

  // Captures everything after the scheme (http:// / ftp://) as the "rest" group.
  // "rest" starts with the host and may include an optional port and path.
  private static final Pattern CLEARTEXT_URL = Pattern.compile(
    "^(?:http|ftp)://(?<rest>\\S+)", Pattern.CASE_INSENSITIVE);

  // --- Internal / non-public hosts -------------------------------------------------------
  // TODO Convert these constants into a map for better maintenance
  private static final String LOOPBACK_IPV4 = "^127(?:\\.\\d+){3}";
  private static final String LOOPBACK_IPV6 = "^(?:0*:){7}:?0*1|^\\[?::1\\]?";
  // All 169.254.0.0/16 link-local addresses are non-routable (RFC 3927); covers AWS/Azure/GCP/OCI IMDS
  private static final String CLOUD_METADATA_IPV4 = "^169\\.254\\.\\d+\\.\\d+";
  // AWS IPv6 IMDS
  private static final String CLOUD_METADATA_IPV6 = "^\\[?fd00:ec2::254\\]?";
  // Azure wireserver — internal Azure platform IP, non-routable outside Azure fabric
  private static final String AZURE_WIRESERVER = "^168\\.63\\.129\\.16";
  // Alibaba Cloud ECS instance metadata service
  private static final String ALIBABA_METADATA = "^100\\.100\\.100\\.200";
  // GCP and generic cloud IMDS hostnames — resolvable only inside the cloud runtime
  private static final String CLOUD_METADATA_HOSTNAMES = "^metadata\\.google\\.internal|^metadata\\.internal";
  // Docker host-side gateway — not routable outside the Docker network
  private static final String DOCKER_INTERNAL = "^host\\.docker\\.internal|^gateway\\.docker\\.internal";
  // Kubernetes cluster-internal service DNS (*.svc.cluster.local)
  private static final String K8S_INTERNAL = "\\.svc\\.cluster\\.local";
  private static final Pattern SAFE_HOSTS = Pattern.compile(
    "(?:^localhost"
      + "|" + LOOPBACK_IPV4
      + "|" + LOOPBACK_IPV6
      + "|" + CLOUD_METADATA_IPV4
      + "|" + CLOUD_METADATA_IPV6
      + "|" + AZURE_WIRESERVER
      + "|" + ALIBABA_METADATA
      + "|" + CLOUD_METADATA_HOSTNAMES
      + "|" + DOCKER_INTERNAL
      + "|" + K8S_INTERNAL
      + ")(?=[:/?#]|$)",
    Pattern.CASE_INSENSITIVE);

  // --- Well-known namespace URI authorities ----------------------------------------------
  // These authority (host) values appear in http:// URIs that are opaque identifiers in
  // XML namespaces, JSON-LD contexts, RDF ontologies, and similar formats. The protocol
  // prefix is mandated by the respective standard; no actual HTTP connection is implied.

  private static final Pattern NAMESPACE_URI_AUTHORITIES = Pattern.compile(
    // W3C: XML Schema, XHTML, RDF, OWL, SPARQL, …
    "(?:^www\\.w3\\.org"
      // Android SDK XML namespaces
      + "|^schemas\\.android\\.com"
      // Microsoft XML schemas
      + "|^schemas\\.microsoft\\.com"
      // SOAP / WS-* schemas
      + "|^schemas\\.xmlsoap\\.org"
      // SAP namespaces
      + "|^www\\.sap\\.com"
      // OGC / OpenGIS
      + "|^www\\.opengis\\.net"
      // HL7 FHIR
      + "|^hl7\\.org"
      // UCUM units
      + "|^unitsofmeasure\\.org"
      // Dublin Core, BIBO, …
      + "|^purl\\.org"
      // OASIS: SAML, WS-Security, OData, …
      + "|^docs\\.oasis-open\\.org"
      // FOAF, vCard, …
      + "|^xmlns\\.com"
      // JSON-LD
      + "|^json-ld\\.org"
      // Schema.org structured data
      + "|^schema\\.org"
      // Spring Framework XML schemas
      + "|^www\\.springframework\\.org"
      // Maven POM / XSD
      + "|^maven\\.apache\\.org"
      // Dublin Core legacy URIs
      + "|^dublincore\\.org"
      // Open Graph Protocol
      + "|^ogp\\.me"
      + ")(?=[:/?#]|$)",
    Pattern.CASE_INSENSITIVE);

  // --- IANA-reserved documentation / placeholder domains --------------------------------
  // RFC 6761 reserves example.com/net/org and the .example/.test TLDs for documentation.
  // RFC 6761 reserves .localhost for loopback addresses.
  private static final Pattern DOCUMENTATION_HOSTS = Pattern.compile(
    "(?:example\\.(?:com|net|org)|\\.(?:example|test|localhost))(?=[:/?#]|$)",
    Pattern.CASE_INSENSITIVE);

  private CleartextProtocolFilter() {
  }

  /**
   * Returns {@code true} if {@code url} is safe to use without TLS and should NOT
   * trigger a cleartext-protocol security warning. Call this before raising an issue.
   *
   * @param url the raw URL string as it appears in source code; must not be null
   */
  public static boolean isSafeWithoutTls(String url) {
    var matcher = CLEARTEXT_URL.matcher(url.strip());
    if (!matcher.find()) {
      return true;
    }
    var rest = matcher.group("rest");
    return SAFE_HOSTS.matcher(rest).find()
      || NAMESPACE_URI_AUTHORITIES.matcher(rest).find()
      || DOCUMENTATION_HOSTS.matcher(rest).find();
  }
}
