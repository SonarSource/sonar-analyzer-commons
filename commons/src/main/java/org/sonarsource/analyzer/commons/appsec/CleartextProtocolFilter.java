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
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Determines whether a cleartext-protocol URL should be considered safe and should not
 * trigger a security warning.
 * <p>
 * Recognised cleartext schemes: {@code http}, {@code ftp}, {@code ws}, {@code telnet},
 * {@code rtmp}, {@code tftp}, {@code gopher}, {@code irc}. Any other scheme is considered
 * safe (e.g. {@code https}, {@code wss}, {@code sftp}).
 * <p>
 * Three categories of safe cleartext URLs are recognised:
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
  private static final Pattern SAFE_HOSTS = Pattern.compile("(?:" +
    // localhost
    "^localhost|" +
    // IPv4 loopback (127.0.0.0/8)
    "^127(?:\\.\\d+){3}|" +
    // IPv6 loopback (::1)
    "^\\[(?:0*:){7}:?0*1\\]|^\\[::1\\]|" +
    // IPv4 link-local (169.254.0.0/16, RFC 3927) — AWS/Azure/GCP/OCI IMDS
    "^169\\.254\\.\\d+\\.\\d+|" +
    // AWS IPv6 IMDS (fd00:ec2::254)
    "^\\[fd00:ec2::254\\]|" +
    // Azure wireserver
    "^168\\.63\\.129\\.16|" +
    // Alibaba Cloud ECS IMDS
    "^100\\.100\\.100\\.200|" +
    // GCP/generic cloud IMDS hostnames
    "^metadata\\.google\\.internal|^metadata\\.internal|" +
    // Docker internal hostnames
    "^host\\.docker\\.internal|^gateway\\.docker\\.internal|" +
    // Kubernetes cluster-internal DNS (*.svc.cluster.local)
    "\\.svc\\.cluster\\.local" +
    ")(?=:|$)", Pattern.CASE_INSENSITIVE);

  // --- Well-known namespace URI authorities ----------------------------------------------
  // These authority (host) values appear in http:// URIs that are opaque identifiers in
  // XML namespaces, JSON-LD contexts, RDF ontologies, and similar formats. The protocol
  // prefix is mandated by the respective standard; no actual HTTP connection is implied.
  private static final Pattern NAMESPACE_URI_AUTHORITIES = Pattern.compile("(?:" +
    // W3C XML Schema, XHTML, RDF, OWL, SPARQL
    "^www\\.w3\\.org|" +
    // Android SDK XML namespaces
    "^schemas\\.android\\.com|" +
    // Microsoft XML schemas
    "^schemas\\.microsoft\\.com|" +
    // SOAP / WS-* schemas
    "^schemas\\.xmlsoap\\.org|" +
    // SAP namespaces
    "^www\\.sap\\.com|" +
    // OGC / OpenGIS
    "^www\\.opengis\\.net|" +
    // HL7 FHIR
    "^hl7\\.org|" +
    // UCUM units
    "^unitsofmeasure\\.org|" +
    // Dublin Core, BIBO
    "^purl\\.org|" +
    // OASIS: SAML, WS-Security, OData
    "^docs\\.oasis-open\\.org|" +
    // FOAF, vCard
    "^xmlns\\.com|" +
    // JSON-LD
    "^json-ld\\.org|" +
    // Schema.org structured data
    "^schema\\.org|" +
    // Spring Framework XML schemas
    "^www\\.springframework\\.org|" +
    // Maven POM / XSD
    "^maven\\.apache\\.org|" +
    // Dublin Core legacy URIs
    "^dublincore\\.org|" +
    // Open Graph Protocol
    "^ogp\\.me" +
    ")(?=:|$)", Pattern.CASE_INSENSITIVE);

  // --- IANA-reserved documentation / placeholder domains --------------------------------
  // example.com/net/org are well-known IANA-delegated documentation domains.
  // RFC 6761 reserves the .example, .test, and .localhost TLDs for documentation and testing.
  private static final Pattern DOCUMENTATION_HOSTS = Pattern.compile("(?:" +
    // example.com/net/org and subdomains
    "(?:^|\\.)example\\.(?:com|net|org)|" +
    // .example, .test, .localhost reserved TLDs (RFC 6761)
    "\\.(?:example|test|localhost)" +
    ")(?=:|$)", Pattern.CASE_INSENSITIVE);

  /**
   * Maps each cleartext scheme name (correctly capitalised) to its recommended secure alternative.
   * Keys use display-correct capitalisation (acronyms in ALL-CAPS, proper names in Title Case).
   * The map uses case-insensitive ordering so lookups work regardless of the input case.
   * This is the single source of truth for both detection and messaging.
   */
  private static final Map<String, String> CLEARTEXT_PROTOCOL_ALTERNATIVES;
  static {
    TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    m.put("HTTP",    "HTTPS");
    m.put("FTP",     "SFTP, SCP or FTPS");
    m.put("WS",      "WSS");
    m.put("Telnet",  "SSH");
    m.put("Gopher",  "HTTPS");
    m.put("TFTP",    "SFTP");
    m.put("SMTP",    "SMTPS");
    m.put("LDAP",    "LDAPS");
    m.put("IMAP",    "IMAPS");
    m.put("POP3",    "POP3S");
    m.put("AMQP",    "AMQPS");
    m.put("MQTT",    "MQTTS");
    m.put("SIP",     "SIPS");
    m.put("RTMP",    "RTMPS");
    m.put("IRC",     "IRCS");
    m.put("NNTP",    "NNTPS");
    m.put("STOMP",   "STOMPS");
    CLEARTEXT_PROTOCOL_ALTERNATIVES = Collections.unmodifiableMap(m);
  }

  private static final Set<String> CLEARTEXT_SCHEMES = CLEARTEXT_PROTOCOL_ALTERNATIVES.keySet();

  private static final Set<String> CLEARTEXT_SCHEME_PREFIXES = CLEARTEXT_SCHEMES.stream()
    .map(s -> s.toLowerCase(Locale.ROOT) + "://")
    .collect(Collectors.toUnmodifiableSet());

  // Lenient fallback: extracts the authority from a cleartext URL without strict URI validation.
  // Used when java.net.URI rejects the string (e.g. template placeholders) or returns a null
  // host (e.g. underscores in hostnames).
  private static final Pattern CLEARTEXT_AUTHORITY = Pattern.compile(
    "^(?:" + String.join("|", CLEARTEXT_SCHEMES) + ")://(?:[^@\\s/?#]++@)?(?<rest>[^\\s/?#]++)", Pattern.CASE_INSENSITIVE);

  private CleartextProtocolFilter() {
  }

  /**
   * Returns the set of cleartext scheme strings (including the {@code ://} suffix) for which
   * a well-known secure alternative exists. These are the schemes that rule implementations
   * should flag, e.g. {@code {"http://", "ftp://", "ws://", ...}}.
   *
   * <p>Use in conjunction with {@link #getIssueMessage(String)} to build issue messages.
   */
  public static Set<String> getCleartextProtocols() {
    return CLEARTEXT_SCHEME_PREFIXES;
  }

  /**
   * Returns the standard issue message for the given cleartext scheme name (without {@code ://}),
   * e.g. {@code getIssueMessage("http")} returns
   * {@code Optional.of("Using http protocol is insecure. Use https instead.")}.
   * Returns {@link Optional#empty()} if the scheme is not a known cleartext protocol.
   *
   * @param scheme the scheme name as it appears in the URL, without {@code ://}
   */
  public static Optional<String> getIssueMessage(String scheme) {
    return CLEARTEXT_PROTOCOL_ALTERNATIVES.entrySet().stream()
      .filter(e -> e.getKey().equalsIgnoreCase(scheme))
      .findFirst()
      .map(e -> "Using " + e.getKey() + " protocol is insecure. Use " + e.getValue() + " instead.");
  }

  /**
   * Returns {@code true} if {@code url} is safe to use without TLS and should NOT
   * trigger a cleartext-protocol security warning. Call this before raising an issue.
   *
   * <p>Well-formed URLs are checked via {@link #isSafeWithoutTls(URI)}. When strict URI
   * parsing fails (e.g. template placeholders like {@code ${port}}) or yields no host
   * (e.g. underscores in hostnames), a lenient authority extraction is used as a fallback.
   *
   * @param url the URL string as it appears in source code; must not be null
   */
  public static boolean isSafeWithoutTls(String url) {
    var stripped = url.strip();
    try {
      var uri = new URI(stripped);
      if (uri.getScheme() != null && uri.getHost() != null) {
        return isSafeWithoutTls(uri);
      }
    } catch (URISyntaxException e) {
      // fall through to lenient parsing
    }
    var matcher = CLEARTEXT_AUTHORITY.matcher(stripped);
    return !matcher.find() || isSafeHost(matcher.group("rest"));
  }

  /**
   * Returns {@code true} if {@code url} is safe to use without TLS and should NOT
   * trigger a cleartext-protocol security warning. Call this before raising an issue.
   *
   * @param url the parsed URI; must not be null
   */
  public static boolean isSafeWithoutTls(URI url) {
    var scheme = url.getScheme();
    if (scheme == null || !CLEARTEXT_SCHEMES.contains(scheme)) {
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
