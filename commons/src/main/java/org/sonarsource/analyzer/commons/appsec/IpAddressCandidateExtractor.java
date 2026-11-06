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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts IP address literals or a legacy unvalidated candidate from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final Pattern URL_AUTHORITY_START = Pattern.compile("//");
  private static final Pattern CONTENT_ENTRY = Pattern.compile(
    "[\\p{L}\\p{N}\\p{M}._:+\\-\\[\\]/]+(?:%[^\\s,;=\"'()<>?&#]*)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ENTRY_END = Pattern.compile("[\\s,;\"'()<>]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern URL_USER_INFO = Pattern.compile("[^/?#,@\\s;\"'()<>]*@", Pattern.UNICODE_CHARACTER_CLASS);

  private IpAddressCandidateExtractor() {
  }

  /**
   * Heuristically extracts an IP address candidate from the given string.
   * Supports bare IP addresses, socket endpoints, URLs, and delimited lists of these.
   * The result is not guaranteed to be a valid IP address.
   *
   * <p><strong>Note:</strong> Extraction starts at the beginning of the string.
   * For delimited lists, the candidate is extracted from the first entry only.
   * If extraction fails, no attempt is made to find another candidate later in the string.
   *
   * More specifically:
   *
   * <ul>
   *   <li>Skips URL user information before the host, such as {@code user@} or {@code user:pass@}.</li>
   *   <li>Removes a single enclosing pair of brackets from IPv6 addresses, and ASCII decimal ports from 0 through 65535.
   *     Brackets delimit IPv6 hosts in URLs and endpoints so address colons are distinct from the port separator,
   *     for example {@code [2003::1]:8080}.</li>
   *   <li>Preserves IPv6 zones and recognized CIDR suffixes on bare addresses.
   *     Slash suffixes in URLs or after ports are always paths.
   *     Other slash suffixes on bare addresses are also treated as paths.</li>
   *   <li>Stops at a query, fragment, comma, or whitespace; leading whitespace is not accepted.</li>
   * </ul>
   *
   * <p>Returns empty for missing candidates or malformed endpoints.
   * The returned text is not validated as an IP address or CIDR and may be a host name or malformed IP address.
   * Use {@link IpAddressClassifier#parse(String)} to validate and parse the candidate.
   */
  public static Optional<String> extract(String content) {
    // Isolate the URL host: http://127.0.0.1/7 has a path, not a CIDR prefix.
    Matcher protocol = URL_PREFIX.matcher(content);
    String host = content;
    if (protocol.find()) {
      host = content.substring(protocol.end());
      int path = host.indexOf('/');
      if (path >= 0) {
        host = host.substring(0, path);
      }
    }

    // Stop only at recognized delimiters; retain other characters so parsing rejects malformed candidates like 1.2.3.4-foo.
    Matcher delimiter = ADDRESS_DELIMITER.matcher(host);
    String candidate = delimiter.find() ? host.substring(0, delimiter.start()) : host;

    // Preserve bare CIDRs (1.2.3.4/24); strip paths (1.2.3.4/path).
    int slash = candidate.indexOf('/');
    String suffix = "";
    if (slash >= 0) {
      suffix = candidate.substring(slash + 1);
      candidate = candidate.substring(0, slash);
    }
    // Three digits cover both IPv4 and IPv6 prefixes.
    int maxPrefixDigits = 3;
    String cidr = (suffix.length() <= maxPrefixDigits && ASCII_DIGITS.matcher(suffix).matches()) ? ("/" + suffix) : "";

    return stripBracketsAndPort(candidate)
      .filter(stripped -> !stripped.candidate().isEmpty())
      .map(stripped -> stripped.candidate() +
        // An IP address with a port identifies an endpoint, where a CIDR prefix doesn't make sense.
        // Treat the suffix as a URL path.
        (stripped.portRemoved() ? "" : cidr));
  }

  /**
   * Finds all syntactically valid IP address literals throughout the content, in source order, including duplicates.
   * Unlike {@link #extract(String)}, this operation validates candidates with {@link IpAddressClassifier#parse(String)}.
   * It does not apply any address classification or hardcoding exceptions.
   *
   * <p>Supports whitespace-, comma-, and semicolon-separated entries, quoted or parenthesized values, assignments,
   * paths, and URLs (excluding their scheme and authority user information). IPv6 endpoint brackets and valid
   * IPv4 or bracketed IPv6 ports are removed. Spelling and zones are preserved; embedded IPv4 tails are not separate matches.
   * Malformed entries are skipped without preventing extraction from later entries.
   *
   * <p>A standalone address with a numeric-looking slash suffix is treated as CIDR and must have a valid prefix length.
   * Other slash suffixes, slashes in URLs, and entries with multiple path segments are treated as paths.
   * Adjacent letters, digits, dots, hyphens, and underscores remain part of a candidate, preventing partial matches.
   * Bracketed IPv4 endpoints and bracketed lists are not supported.
   * Zones are opaque, as in the address validator; percent-encoded URL content is not decoded.
   *
   * @return an empty list if no valid address is found
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher urls = URL_AUTHORITY_START.matcher(content);
    Matcher entries = CONTENT_ENTRY.matcher(content);
    int position = 0;
    int urlEnd = 0;
    // Locate authority markers before tokenizing entries, independently of the scheme spelling.
    // Compound schemes (jdbc:mysql://), labels, and stray brackets must not expose user information.
    // Scheme text preceding the marker cannot validate as an IP address.
    while (urls.find(position)) {
      extractEntries(entries, position, urls.start(), urlEnd, addresses);
      position = urls.end();
      if (urls.start() >= urlEnd) {
        Matcher end = ENTRY_END.matcher(content);
        urlEnd = end.find(position) ? end.start() : content.length();
      }
      Matcher userInfo = URL_USER_INFO.matcher(content).region(position, urlEnd);
      if (userInfo.lookingAt()) {
        position = userInfo.end();
      }
    }
    extractEntries(entries, position, content.length(), urlEnd, addresses);
    return addresses;
  }

  private static void extractEntries(Matcher entries, int start, int end, int urlEnd, List<String> addresses) {
    entries.region(start, end);
    while (entries.find()) {
      extractEntry(entries.group(), entries.start() < urlEnd, addresses);
    }
  }

  private static void extractEntry(String entry, boolean inUrl, List<String> addresses) {
    String[] segments = entry.split("/", -1);
    // Only a standalone address/prefix pair can be CIDR. Leading slashes, multiple segments, URLs,
    // and endpoints with ports establish path context instead.
    if (!inUrl && segments.length == 2 && isPrefixLike(segments[1])) {
      Optional<StrippedCandidate> stripped = stripBracketsAndPort(segments[0]);
      if (stripped.isPresent() && !stripped.get().portRemoved()) {
        addValidAddress(stripped.get().candidate() + "/" + segments[1], addresses);
        return;
      }
    }
    for (String segment : segments) {
      stripBracketsAndPort(segment).ifPresent(stripped -> addValidAddress(stripped.candidate(), addresses));
    }
  }

  private static boolean isPrefixLike(String suffix) {
    if (suffix.isEmpty() || suffix.indexOf(':') >= 0 || IpAddressClassifier.parse(suffix).isPresent()) {
      return false;
    }
    int first = suffix.codePointAt(0);
    return Character.isDigit(first) || first == '+' || first == '-';
  }

  private static void addValidAddress(String candidate, List<String> addresses) {
    if (IpAddressClassifier.parse(candidate).isPresent()) {
      addresses.add(candidate);
    }
  }

  private static Optional<StrippedCandidate> stripBracketsAndPort(String candidate) {
    // Brackets distinguish IPv6 address colons from the port separator.
    String address = candidate;
    int portSeparator = candidate.length();
    if (candidate.startsWith("[")) {
      int closing = candidate.indexOf(']');
      if (closing < 0) {
        return Optional.empty();
      }
      address = candidate.substring(1, closing);
      if (address.indexOf(':') < 0) {
        // No `:` found => it is not an IPv6 address.
        // IPv4 addresses must not be enclosed in brackets.
        return Optional.empty();
      }
      portSeparator = closing + 1;
    } else {
      int colon = candidate.indexOf(':');
      if (colon > 0 && candidate.substring(0, colon).indexOf('.') >= 0) {
        address = candidate.substring(0, colon);
        portSeparator = colon;
      }
    }

    boolean hasPort = portSeparator < candidate.length();
    if (hasPort && (candidate.charAt(portSeparator) != ':' || !isPort(candidate.substring(portSeparator + 1)))) {
      return Optional.empty();
    }
    // After unwrapping, any remaining bracket makes the address invalid.
    if (address.indexOf('[') >= 0 || address.indexOf(']') >= 0) {
      return Optional.empty();
    }

    return Optional.of(new StrippedCandidate(address, hasPort));
  }

  private static boolean isPort(String port) {
    if (port.length() > 5 || !ASCII_DIGITS.matcher(port).matches()) {
      return false;
    }
    return Integer.parseInt(port) <= 65535;
  }

  private record StrippedCandidate(String candidate, boolean portRemoved) {
  }

}
