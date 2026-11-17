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

/** Extracts IP address candidates and validated literals from string content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");

  private static final Pattern URL_IN_TEXT = Pattern.compile(
    "(?<![\\p{L}\\p{N}_.:\\[\\]-])(?:[a-zA-Z][a-zA-Z0-9+.-]*+:)?//(?:[^\\s/?#\"'`()<>]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern AUTHORITY_DELIMITER = Pattern.compile("[/?#,;\\s\"'`()<>]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern URL_DELIMITER = Pattern.compile("[,;\\s\"'`()<>]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ENTRIES = Pattern.compile("[\\p{L}\\p{N}_.:%\\[\\]+/\\-]+", Pattern.UNICODE_CHARACTER_CLASS);

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
   * Extracts all syntactically valid IP address literals throughout the given string, in source order,
   * retaining repeated occurrences and the original address spelling (including IPv6 zones).
   * Returns an empty list when no address is found. This operation does not apply security-rule exceptions.
   *
   * <p>Supports paths, text, delimited lists, quoted or parenthesized values, and URL hosts, paths,
   * queries and fragments. URL schemes and authority user information are excluded. Addresses attached
   * to letters, digits, dots, hyphens or underscores are rejected as a whole, as are malformed endpoints.
   * IPv6 endpoint brackets and valid IPv4 or bracketed IPv6 ports are removed.
   *
   * <p>A valid standalone CIDR suffix is preserved. Numeric-looking invalid prefixes reject the entry;
   * slashes in URLs, after ports, or in paths separate path segments instead.
   * Address validation follows {@link IpAddressClassifier#parse(String)}.
   * Unlike {@link #extract(String)}, this operation validates its results and searches the complete string.
   *
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher urls = URL_IN_TEXT.matcher(content);
    int previous = 0;
    int urlEnd = 0;
    while (urls.find(previous)) {
      extractBetweenUrls(content, previous, urls.start(), urlEnd, addresses);
      int host = urls.end();
      Matcher authorityDelimiter = AUTHORITY_DELIMITER.matcher(content);
      int authorityEnd = authorityDelimiter.find(host) ? authorityDelimiter.start() : content.length();
      stripBracketsAndPort(content.substring(host, authorityEnd))
        .ifPresent(candidate -> addValidAddress(candidate.candidate(), addresses));
      // Nested URLs share the surrounding URL boundary; do not rescan the remaining text for each one.
      if (host >= urlEnd) {
        Matcher urlDelimiter = URL_DELIMITER.matcher(content);
        urlEnd = urlDelimiter.find(host) ? urlDelimiter.start() : content.length();
      }
      previous = authorityEnd;
    }
    extractBetweenUrls(content, previous, content.length(), urlEnd, addresses);
    return addresses;
  }

  private static void extractBetweenUrls(String content, int start, int end, int urlEnd, List<String> addresses) {
    if (start < urlEnd) {
      int pathEnd = Math.min(end, urlEnd);
      extractEntries(content.substring(start, pathEnd), true, addresses);
      start = pathEnd;
    }
    extractEntries(content.substring(start, end), false, addresses);
  }

  private static void extractEntries(String content, boolean url, List<String> addresses) {
    Matcher entries = ENTRIES.matcher(content);
    while (entries.find()) {
      extractOrdinaryEntry(entries.group(), url, addresses);
    }
  }

  private static void extractOrdinaryEntry(String entry, boolean url, List<String> addresses) {
    int start = 0;
    int colons = 0;
    boolean zone = false;
    for (int i = 0; i < entry.length(); i++) {
      char current = entry.charAt(i);
      if (isOrdinaryPunctuation(entry, i, start, colons, zone)) {
        extractEntry(entry.substring(start, i), url, addresses);
        start = i + 1;
        colons = 0;
        zone = false;
      } else if (current == ':') {
        colons++;
      } else if (current == '%') {
        zone = true;
      } else if (current == '/' || current == ']') {
        colons = 0;
        zone = false;
      }
    }
    extractEntry(entry.substring(start), url, addresses);
  }

  private static boolean isOrdinaryPunctuation(String entry, int index, int start, int colons, boolean zone) {
    char current = entry.charAt(index);
    return (current == ':' && colons == 0 && isLabelSeparator(entry, index, start))
      || (current == '%' && colons < 2)
      || (current == '+' && !zone && (index == start || ":/".indexOf(entry.charAt(index - 1)) < 0));
  }

  private static boolean isLabelSeparator(String entry, int colon, int start) {
    int segment = entry.lastIndexOf('/', colon) + 1;
    String label = entry.substring(Math.max(start, segment), colon);
    // Hexadecimal groups, dotted addresses and brackets belong to an address or endpoint.
    if (!label.isEmpty() && (label.chars().allMatch(c -> Character.digit(c, 16) >= 0)
      || label.indexOf('.') >= 0 || label.indexOf('[') >= 0 || label.indexOf(']') >= 0)) {
      return false;
    }
    int end = colon + 1;
    while (end < entry.length() && "/+%".indexOf(entry.charAt(end)) < 0) {
      end++;
    }
    String value = entry.substring(colon + 1, end);
    // A label before IPv4 (possibly with a port) or a bracketed endpoint is unambiguous.
    // Keep unbracketed IPv6 together, including malformed groups and embedded IPv4 tails.
    return value.startsWith("[") || (!value.startsWith(":") && value.indexOf('.') >= 0 && value.indexOf(':') == value.lastIndexOf(':'));
  }

  private static void extractEntry(String entry, boolean url, List<String> addresses) {
    int slash = entry.indexOf('/');
    // A single numeric-looking suffix on a bare address is a CIDR, even if its prefix is invalid.
    // Leading slashes, additional segments, and endpoint ports establish path context instead.
    if (!url && slash > 0 && slash == entry.lastIndexOf('/')) {
      Optional<StrippedCandidate> bare = stripBracketsAndPort(entry.substring(0, slash));
      String suffix = entry.substring(slash + 1);
      if (bare.isPresent() && !bare.get().portRemoved()
        && IpAddressClassifier.parse(bare.get().candidate()).isPresent() && looksLikePrefix(suffix)) {
        addValidAddress(bare.get().candidate() + "/" + suffix, addresses);
        return;
      }
    }
    int start = 0;
    while (start <= entry.length()) {
      int end = entry.indexOf('/', start);
      if (end < 0) {
        end = entry.length();
      }
      stripBracketsAndPort(entry.substring(start, end))
        .ifPresent(candidate -> addValidAddress(candidate.candidate(), addresses));
      start = end + 1;
    }
  }

  private static boolean looksLikePrefix(String suffix) {
    // A second address is another path segment, never a CIDR prefix.
    return !suffix.isEmpty() && (Character.isDigit(suffix.codePointAt(0)) || suffix.charAt(0) == '+' || suffix.charAt(0) == '-')
      && stripBracketsAndPort(suffix).filter(candidate -> IpAddressClassifier.parse(candidate.candidate()).isPresent()).isEmpty();
  }

  private static void addValidAddress(String address, List<String> addresses) {
    if (!address.endsWith(".") && IpAddressClassifier.parse(address).isPresent()) {
      addresses.add(address);
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
