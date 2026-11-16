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

/** Extracts IP address literals or a single heuristic candidate from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  // Consume URL user information before splitting entries: it can itself contain list/value delimiters.
  private static final Pattern ENTRIES = Pattern.compile(
    "(?<url>(?:(?<scheme>[a-zA-Z][a-zA-Z0-9+.-]*):)?//(?:[^/?#\\s\"]*@)?)|(?<entry>[^\\s,;\"'()=?#&]+)",
    Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern URL_END = Pattern.compile("[\\s,;\"'()]", Pattern.UNICODE_CHARACTER_CLASS);
  // Keep whole segments, including invalid endpoint text, rather than recovering valid-looking substrings.
  private static final Pattern PARTS = Pattern.compile("[^/@]+");

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
   * Extracts all syntactically valid IP literals from the complete string, in source order, including duplicates.
   * Unlike {@link #extract(String)}, this operation validates addresses using {@link IpAddressClassifier#parse(String)};
   * it does not apply any address classification or security-rule exemptions.
   *
   * <p>Supports whitespace-, comma- and semicolon-separated entries, quotes, parentheses, equals signs,
   * paths, and URL hosts, paths, queries and fragments. URL schemes and authority user information are skipped.
   * Whole segments are validated so identifiers, trailing dots and malformed endpoints are not shortened into matches.
   * IPv6 brackets and valid endpoint ports are removed; spelling and zones are preserved.
   * A single slash suffix on a standalone address is validated and preserved as CIDR. Slashes in URLs,
   * paths (entries with a preceding slash or further path segments), and after endpoint ports are separators.
   * Bracketed IPv4 endpoints and bracketed lists are not supported.
   *
   * @return a list of addresses, or an empty list when none are found
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher entries = ENTRIES.matcher(content);
    int urlEnd = 0;
    while (entries.find()) {
      if (entries.group("url") != null && (entries.start() >= urlEnd || entries.group("scheme") != null)) {
        Matcher delimiter = URL_END.matcher(content);
        urlEnd = delimiter.find(entries.end()) ? delimiter.start() : content.length();
      } else if (entries.group("url") != null) {
        // Repeated slashes are paths, not a new authority. Rescan any swallowed entry delimiters.
        entries.region(entries.start() + 2, content.length());
      } else {
        extractEntry(entries.group(), entries.start() < urlEnd, addresses);
      }
    }
    return addresses;
  }

  private static void extractEntry(String entry, boolean path, List<String> addresses) {
    Matcher parts = PARTS.matcher(entry);
    int previousEnd = 0;
    while (parts.find()) {
      path |= entry.substring(previousEnd, parts.start()).indexOf('/') >= 0;
      Optional<StrippedCandidate> stripped = stripBracketsAndPort(parts.group());
      int end = parts.end();
      if (stripped.isPresent()) {
        StrippedCandidate candidate = stripped.get();
        String address = candidate.candidate();
        if (!path && !candidate.portRemoved() && end < entry.length() && entry.charAt(end) == '/'
          && entry.indexOf('/', end + 1) < 0 && IpAddressClassifier.parse(address).isPresent()) {
          // A standalone slash suffix must validate in full, including its family-specific prefix length.
          address += entry.substring(end);
          end = entry.length();
        }
        if (IpAddressClassifier.parse(address).isPresent()) {
          addresses.add(address);
        }
      }
      previousEnd = parts.end();
      parts.region(end, entry.length());
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
