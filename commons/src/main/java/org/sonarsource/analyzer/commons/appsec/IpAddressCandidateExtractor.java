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

/** Heuristically extracts an IP address candidate string from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final Pattern ENTRIES = Pattern.compile("[^\\s\"]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern URL_START = Pattern.compile("(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");

  private static final char NO_URL = '\0';
  private static final char PATH = '/';
  private static final char QUERY = '?';
  private static final char FRAGMENT = '#';

  // Per-entry state: these private instances never escape the static extraction API.
  private final String entry;
  private final List<String> addresses;
  private final Matcher url;
  private char component = NO_URL;
  private boolean inPath;
  private boolean enclosingQuery;
  private boolean urlAllowed = true;
  private int parentheses;
  private boolean quoted;
  private int start;

  private IpAddressCandidateExtractor(String entry, List<String> addresses) {
    this.entry = entry;
    this.addresses = addresses;
    this.url = URL_START.matcher(entry);
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
   * Finds all syntactically valid IP address literals throughout {@code content}, in source order, including duplicates.
   * Unlike {@link #extract(String)}, this operation validates each result with {@link IpAddressClassifier#parse(String)}.
   * It does not classify addresses or decide whether they may be hardcoded.
   *
   * <p>Supports whitespace-, comma-, and semicolon-separated entries, quoted or parenthesized values, values after
   * {@code =}, path segments, and URL hosts, paths, queries, and fragments. URL schemes and authority user information
   * are excluded. Addresses attached to identifiers or extra dots are not extracted from those larger tokens.
   * IPv6 spelling and zones are preserved; endpoint brackets and valid ports are removed. Ports are supported only
   * on IPv4 or bracketed IPv6 endpoints, and malformed endpoints are rejected as a whole.
   *
   * <p>A standalone address with a single slash suffix is treated as a CIDR and must have a valid prefix length.
   * In URLs, after ports, and in paths (including entries starting with a slash or a non-address segment), slashes
   * instead separate path segments. Bracketed IPv4 endpoints and bracketed lists are not supported.
   *
   * @return the addresses, or an empty list when none are found
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher entries = ENTRIES.matcher(content);
    while (entries.find()) {
      new IpAddressCandidateExtractor(entries.group(), addresses).scan();
    }
    return addresses;
  }

  private void scan() {
    while (start < entry.length()) {
      if (urlAllowed && url.region(start, entry.length()).lookingAt()) {
        extractHost();
      } else {
        extractText();
      }
      if (start < entry.length() && entry.charAt(start) != '/') {
        char separator = entry.charAt(start);
        start++;
        advance(separator);
      }
    }
  }

  private void extractHost() {
    // Parentheses, apostrophes, and list separators may occur in user information. Skip it before splitting.
    enclosingQuery |= component == QUERY;
    int hostStart = urlHostStart(entry, url.end(), enclosingQuery);
    int hostEnd = urlHostEnd(entry, hostStart, parentheses, quoted, enclosingQuery);
    addAddress(entry.substring(hostStart, hostEnd), "", addresses);
    start = hostEnd;
    component = PATH;
    inPath = true;
    urlAllowed = false;
  }

  private void extractText() {
    int end = nextSeparator(entry, start, ",;=?#&@'()");
    inPath |= extractSection(entry.substring(start, end), inPath, addresses);
    urlAllowed &= !inPath || end == start;
    start = end;
  }

  private void advance(char separator) {
    switch (separator) {
      case ',', ';' -> {
        component = NO_URL;
        inPath = false;
        enclosingQuery = false;
        urlAllowed = true;
      }
      case '\'' -> quoted = !quoted;
      case '(' -> parentheses += 1;
      case ')' -> parentheses = Math.max(0, parentheses - 1);
      case '?' -> {
        component = component == PATH ? QUERY : component;
        urlAllowed = component != FRAGMENT;
      }
      case '#' -> {
        component = component == NO_URL ? NO_URL : FRAGMENT;
        enclosingQuery = false;
        urlAllowed = !inPath;
      }
      case '&' -> {
        // An unescaped ampersand ends a URL-valued parameter in the enclosing query.
        component = enclosingQuery ? QUERY : component;
        urlAllowed = !inPath || component == QUERY;
      }
      // Equals signs and other value separators inside a path or fragment do not start URL authorities.
      default -> urlAllowed = !inPath || component == QUERY;
    }
  }

  private static int urlHostStart(String entry, int start, boolean queryValue) {
    int authorityEnd = nextSeparator(entry, start, queryValue ? "/?#&" : "/?#");
    String authority = entry.substring(start, authorityEnd);
    return start + authority.lastIndexOf('@') + 1;
  }

  private static int urlHostEnd(String entry, int start, int parentheses, boolean quoted, boolean queryValue) {
    int end = nextSeparator(entry, start, queryValue ? "/?#,;&" : "/?#,;");
    // Only remove punctuation enclosing the URL, never punctuation attached inside its host or port.
    while (end > start) {
      char last = entry.charAt(end - 1);
      if (last == ')' && parentheses > 0) {
        parentheses--;
      } else if (last == '\'' && quoted) {
        quoted = false;
      } else {
        break;
      }
      end--;
    }
    return end;
  }

  private static boolean extractSection(String section, boolean inPath, List<String> addresses) {
    int slash = section.indexOf('/');
    if (!inPath && slash > 0 && section.indexOf('/', slash + 1) < 0) {
      Optional<StrippedCandidate> first = stripBracketsAndPort(section.substring(0, slash));
      if (first.isPresent() && !first.get().portRemoved() && IpAddressClassifier.parse(first.get().candidate()).isPresent()) {
        // Do not recover the bare address when its standalone CIDR suffix is invalid.
        addAddress(section.substring(0, slash), section.substring(slash), addresses);
        return false;
      }
    }
    int start = 0;
    while (start < section.length()) {
      int end = nextSeparator(section, start, "/");
      addAddress(section.substring(start, end), "", addresses);
      start = end + 1;
    }
    return slash >= 0;
  }

  private static void addAddress(String endpoint, String suffix, List<String> addresses) {
    // Zone validation is intentionally opaque, but a sentence-ending dot must not become part of a match.
    if (suffix.isEmpty() && endpoint.endsWith(".")) {
      return;
    }
    Optional<StrippedCandidate> stripped = stripBracketsAndPort(endpoint);
    if (stripped.isPresent()) {
      String literal = stripped.get().candidate() + suffix;
      if (IpAddressClassifier.parse(literal).isPresent()) {
        addresses.add(literal);
      }
    }
  }

  private static int nextSeparator(String text, int start, String separators) {
    int end = start;
    while (end < text.length() && separators.indexOf(text.charAt(end)) < 0) {
      end++;
    }
    return end;
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
