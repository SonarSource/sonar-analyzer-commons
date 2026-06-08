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

import java.util.List;
import java.util.regex.Pattern;

/**
 * Determines whether a cleartext-protocol URL should be considered safe and should not
 * trigger a security warning.
 * <p>
 * Three categories of safe URLs are recognised:
 * <ul>
 *   <li><b>Internal hosts</b> — loopback addresses, cloud instance metadata endpoints
 *       (AWS, Azure, GCP, Alibaba, and others), Docker-internal hostnames, and
 *       Kubernetes cluster-internal service DNS. None of these are reachable from the
 *       public internet.</li>
 *   <li><b>Namespace URI authorities</b> — well-known authorities (W3C, Android,
 *       OASIS, HL7, …) whose {@code http://} URIs are opaque namespace identifiers
 *       used in XML, JSON-LD, RDF, and similar formats. They carry a protocol prefix
 *       by convention and do not imply an actual HTTP connection.</li>
 *   <li><b>IANA-reserved documentation domains</b> — {@code example.com},
 *       {@code example.net}, and {@code example.org} and their subdomains, plus the
 *       reserved TLDs {@code .example}, {@code .test}, and {@code .localhost} (RFC 6761).
 *       Their use in source code is almost always a placeholder, not a real connection.</li>
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
  private static final List<String> SAFE_HOST_PATTERNS = List.of(
    "^localhost",                                              // localhost
    "^127(?:\\.\\d+){3}",                                     // IPv4 loopback (127.0.0.0/8)
    "^(?:0*:){7}:?0*1|^\\[?::1\\]?",                         // IPv6 loopback (::1)
    "^169\\.254\\.\\d+\\.\\d+",                               // IPv4 link-local (169.254.0.0/16, RFC 3927) — AWS/Azure/GCP/OCI IMDS
    "^\\[?fd00:ec2::254\\]?",                                 // AWS IPv6 IMDS (fd00:ec2::254)
    "^168\\.63\\.129\\.16",                                   // Azure wireserver
    "^100\\.100\\.100\\.200",                                  // Alibaba Cloud ECS IMDS
    "^metadata\\.google\\.internal|^metadata\\.internal",     // GCP/generic cloud IMDS hostnames
    "^host\\.docker\\.internal|^gateway\\.docker\\.internal", // Docker internal hostnames
    "\\.svc\\.cluster\\.local"                                // Kubernetes cluster-internal DNS (*.svc.cluster.local)
  );

  private static final Pattern SAFE_HOSTS = buildPattern(SAFE_HOST_PATTERNS);

  // --- Well-known namespace URI authorities ----------------------------------------------
  // These authority (host) values appear in http:// URIs that are opaque identifiers in
  // XML namespaces, JSON-LD contexts, RDF ontologies, and similar formats. The protocol
  // prefix is mandated by the respective standard; no actual HTTP connection is implied.
  private static final List<String> NAMESPACE_URI_PATTERNS = List.of(
    "^www\\.w3\\.org",              // W3C XML Schema, XHTML, RDF, OWL, SPARQL
    "^schemas\\.android\\.com",     // Android SDK XML namespaces
    "^schemas\\.microsoft\\.com",   // Microsoft XML schemas
    "^schemas\\.xmlsoap\\.org",     // SOAP / WS-* schemas
    "^www\\.sap\\.com",             // SAP namespaces
    "^www\\.opengis\\.net",         // OGC / OpenGIS
    "^hl7\\.org",                   // HL7 FHIR
    "^unitsofmeasure\\.org",        // UCUM units
    "^purl\\.org",                  // Dublin Core, BIBO
    "^docs\\.oasis-open\\.org",     // OASIS: SAML, WS-Security, OData
    "^xmlns\\.com",                 // FOAF, vCard
    "^json-ld\\.org",               // JSON-LD
    "^schema\\.org",                // Schema.org structured data
    "^www\\.springframework\\.org", // Spring Framework XML schemas
    "^maven\\.apache\\.org",        // Maven POM / XSD
    "^dublincore\\.org",            // Dublin Core legacy URIs
    "^ogp\\.me"                     // Open Graph Protocol
  );

  private static final Pattern NAMESPACE_URI_AUTHORITIES = buildPattern(NAMESPACE_URI_PATTERNS);

  // --- IANA-reserved documentation / placeholder domains --------------------------------
  // example.com/net/org are well-known IANA-delegated documentation domains.
  // RFC 6761 reserves the .example, .test, and .localhost TLDs for documentation and testing.
  private static final List<String> DOCUMENTATION_HOST_PATTERNS = List.of(
    "(?:^|\\.)example\\.(?:com|net|org)", // example.com/net/org and subdomains
    "\\.(?:example|test|localhost)" // .example, .test, .localhost reserved TLDs (RFC 6761)
  );

  private static final Pattern DOCUMENTATION_HOSTS = buildPattern(DOCUMENTATION_HOST_PATTERNS);

  private CleartextProtocolFilter() {
  }

  private static Pattern buildPattern(List<String> patterns) {
    return Pattern.compile(
      "(?:" + String.join("|", patterns) + ")(?=[:/?#]|$)",
      Pattern.CASE_INSENSITIVE);
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
