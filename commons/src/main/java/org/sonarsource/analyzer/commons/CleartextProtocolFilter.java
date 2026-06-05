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
 * Two categories of safe URLs are recognised:
 * <ul>
 *   <li><b>Internal hosts</b> — loopback addresses, cloud instance metadata
 *       endpoints, Docker-internal hostnames, Kubernetes cluster-internal service
 *       DNS, and inbound listen-wildcard pseudo-hosts. None of these are reachable
 *       from the public internet.</li>
 *   <li><b>Namespace URI authorities</b> — well-known authorities (W3C, Android,
 *       OASIS, HL7, …) whose {@code http://} URIs are opaque namespace identifiers
 *       used in XML, JSON-LD, RDF, and similar formats. They carry a protocol prefix
 *       by convention and do not imply an actual HTTP connection.</li>
 * </ul>
 * <p>
 * Usage: extract the raw URL string from the AST node and call {@link #isSafe(String)}
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

  private static final String LOOPBACK_IPV4 = "^127(?:\\.\\d+){3}";
  private static final String LOOPBACK_IPV6 = "^(?:0*:){7}:?0*1|^\\[?::1\\]?";
  // AWS instance metadata service — RFC 3927 link-local (IPv4) and AWS-defined ULA (IPv6)
  private static final String CLOUD_METADATA_IPV4 = "^169\\.254\\.169\\.254";
  private static final String CLOUD_METADATA_IPV6 = "^\\[?fd00:ec2::254\\]?";
  // GCP and generic cloud IMDS hostnames — resolvable only inside the cloud runtime
  private static final String CLOUD_METADATA_HOSTNAMES = "^metadata\\.google\\.internal|^metadata\\.internal";
  // Docker host-side gateway — not routable outside the Docker network
  private static final String DOCKER_INTERNAL = "^host\\.docker\\.internal|^gateway\\.docker\\.internal";
  // Kubernetes cluster-internal service DNS (*.svc.cluster.local)
  private static final String K8S_INTERNAL = "^\\S+\\.svc\\.cluster\\.local";
  // Bind-all (0.0.0.0) and ASP.NET listen-wildcard (+) are inbound pseudo-hosts, not destinations
  private static final String LISTEN_WILDCARDS = "^0\\.0\\.0\\.0|^\\+";

  private static final Pattern SAFE_HOSTS = Pattern.compile(
    "^localhost"
      + "|" + LOOPBACK_IPV4
      + "|" + LOOPBACK_IPV6
      + "|" + CLOUD_METADATA_IPV4
      + "|" + CLOUD_METADATA_IPV6
      + "|" + CLOUD_METADATA_HOSTNAMES
      + "|" + DOCKER_INTERNAL
      + "|" + K8S_INTERNAL
      + "|" + LISTEN_WILDCARDS,
    Pattern.CASE_INSENSITIVE);

  // --- Well-known namespace URI authorities ----------------------------------------------
  // These authority (host) values appear in http:// URIs that are opaque identifiers in
  // XML namespaces, JSON-LD contexts, RDF ontologies, and similar formats. The protocol
  // prefix is mandated by the respective standard; no actual HTTP connection is implied.

  private static final Pattern NAMESPACE_URI_AUTHORITIES = Pattern.compile(
    "^www\\.w3\\.org"                    // W3C: XML Schema, XHTML, RDF, OWL, SPARQL, …
      + "|^schemas\\.android\\.com"      // Android SDK XML namespaces
      + "|^schemas\\.microsoft\\.com"    // Microsoft XML schemas
      + "|^schemas\\.xmlsoap\\.org"      // SOAP / WS-* schemas
      + "|^www\\.sap\\.com"              // SAP namespaces
      + "|^www\\.opengis\\.net"          // OGC / OpenGIS
      + "|^hl7\\.org"                    // HL7 FHIR
      + "|^unitsofmeasure\\.org"         // UCUM units
      + "|^purl\\.org"                   // Dublin Core, BIBO, …
      + "|^docs\\.oasis-open\\.org"      // OASIS: SAML, WS-Security, OData, …
      + "|^xmlns\\.com"                  // FOAF, vCard, …
      + "|^json-ld\\.org"                // JSON-LD
      + "|^schema\\.org"                 // Schema.org structured data
      + "|^www\\.springframework\\.org"  // Spring Framework XML schemas
      + "|^maven\\.apache\\.org"         // Maven POM / XSD
      + "|^dublincore\\.org"             // Dublin Core legacy URIs
      + "|^ogp\\.me",                    // Open Graph Protocol
    Pattern.CASE_INSENSITIVE);

  private CleartextProtocolFilter() {
  }

  /**
   * Returns {@code true} if {@code url} is safe and should NOT trigger a
   * cleartext-protocol security warning. Call this before raising an issue.
   *
   * @param url the raw URL string as it appears in source code
   */
  public static boolean isSafe(String url) {
    var matcher = CLEARTEXT_URL.matcher(url.strip());
    if (!matcher.find()) {
      return true;
    }
    var rest = matcher.group("rest");
    return SAFE_HOSTS.matcher(rest).find()
      || NAMESPACE_URI_AUTHORITIES.matcher(rest).find();
  }
}
