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

import java.net.URI;
import java.net.URISyntaxException;
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

  // --- Internal / non-public hosts -------------------------------------------------------
  private static final List<String> SAFE_HOST_PATTERNS = List.of(
    // localhost
    "^localhost",
    // IPv4 loopback (127.0.0.0/8)
    "^127(?:\\.\\d+){3}",
    // IPv6 loopback (::1)
    "^\\[(?:0*:){7}:?0*1\\]|^\\[::1\\]",
    // IPv4 link-local (169.254.0.0/16, RFC 3927) — AWS/Azure/GCP/OCI IMDS
    "^169\\.254\\.\\d+\\.\\d+",
    // AWS IPv6 IMDS (fd00:ec2::254)
    "^\\[fd00:ec2::254\\]",
    // Azure wireserver
    "^168\\.63\\.129\\.16",
    // Alibaba Cloud ECS IMDS
    "^100\\.100\\.100\\.200",
    // GCP/generic cloud IMDS hostnames
    "^metadata\\.google\\.internal|^metadata\\.internal",
    // Docker internal hostnames
    "^host\\.docker\\.internal|^gateway\\.docker\\.internal",
    // Kubernetes cluster-internal DNS (*.svc.cluster.local)
    "\\.svc\\.cluster\\.local"
  );

  private static final Pattern SAFE_HOSTS = buildPattern(SAFE_HOST_PATTERNS);

  // --- Well-known namespace URI authorities ----------------------------------------------
  // These authority (host) values appear in http:// URIs that are opaque identifiers in
  // XML namespaces, JSON-LD contexts, RDF ontologies, and similar formats. The protocol
  // prefix is mandated by the respective standard; no actual HTTP connection is implied.
  private static final List<String> NAMESPACE_URI_PATTERNS = List.of(
    // W3C XML Schema, XHTML, RDF, OWL, SPARQL
    "^www\\.w3\\.org",
    // Android SDK XML namespaces
    "^schemas\\.android\\.com",
    // Microsoft XML schemas
    "^schemas\\.microsoft\\.com",
    // SOAP / WS-* schemas
    "^schemas\\.xmlsoap\\.org",
    // SAP namespaces
    "^www\\.sap\\.com",
    // OGC / OpenGIS
    "^www\\.opengis\\.net",
    // HL7 FHIR
    "^hl7\\.org",
    // UCUM units
    "^unitsofmeasure\\.org",
    // Dublin Core, BIBO
    "^purl\\.org",
    // OASIS: SAML, WS-Security, OData
    "^docs\\.oasis-open\\.org",
    // FOAF, vCard
    "^xmlns\\.com",
    // JSON-LD
    "^json-ld\\.org",
    // Schema.org structured data
    "^schema\\.org",
    // Spring Framework XML schemas
    "^www\\.springframework\\.org",
    // Maven POM / XSD
    "^maven\\.apache\\.org",
    // Dublin Core legacy URIs
    "^dublincore\\.org",
    // Open Graph Protocol
    "^ogp\\.me"
  );

  private static final Pattern NAMESPACE_URI_AUTHORITIES = buildPattern(NAMESPACE_URI_PATTERNS);

  // --- IANA-reserved documentation / placeholder domains --------------------------------
  // example.com/net/org are well-known IANA-delegated documentation domains.
  // RFC 6761 reserves the .example, .test, and .localhost TLDs for documentation and testing.
  private static final List<String> DOCUMENTATION_HOST_PATTERNS = List.of(
    // example.com/net/org and subdomains
    "(?:^|\\.)example\\.(?:com|net|org)",
    // .example, .test, .localhost reserved TLDs (RFC 6761)
    "\\.(?:example|test|localhost)"
  );

  private static final Pattern DOCUMENTATION_HOSTS = buildPattern(DOCUMENTATION_HOST_PATTERNS);

  private CleartextProtocolFilter() {
  }

  private static Pattern buildPattern(List<String> patterns) {
    return Pattern.compile(
      "(?:" + String.join("|", patterns) + ")(?=:|$)",
      Pattern.CASE_INSENSITIVE);
  }

  /**
   * Returns {@code true} if {@code url} is safe to use without TLS and should NOT
   * trigger a cleartext-protocol security warning. Call this before raising an issue.
   * Returns {@code false} if the URL string cannot be parsed as a URI.
   *
   * @param url the URL string as it appears in source code; must not be null
   */
  public static boolean isSafeWithoutTls(String url) {
    try {
      return isSafeWithoutTls(new URI(url.strip()));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} if {@code url} is safe to use without TLS and should NOT
   * trigger a cleartext-protocol security warning. Call this before raising an issue.
   *
   * @param url the parsed URI; must not be null
   */
  public static boolean isSafeWithoutTls(URI url) {
    var scheme = url.getScheme();
    if (scheme == null || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("ftp")) {
      return true;
    }
    var host = url.getHost();
    if (host == null) {
      return false;
    }
    return isSafeHost(host);
  }

  private static boolean isSafeHost(String host) {
    return isInternalHost(host) || isNamespaceUriAuthority(host) || isDocumentationHost(host);
  }

  private static boolean isInternalHost(String host) {
    return SAFE_HOSTS.matcher(host).find();
  }

  private static boolean isNamespaceUriAuthority(String host) {
    return NAMESPACE_URI_AUTHORITIES.matcher(host).find();
  }

  private static boolean isDocumentationHost(String host) {
    return DOCUMENTATION_HOSTS.matcher(host).find();
  }
}
