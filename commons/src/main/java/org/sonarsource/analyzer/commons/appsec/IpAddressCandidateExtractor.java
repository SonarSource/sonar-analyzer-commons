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
  private static final Pattern URL_START = Pattern.compile("(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");

  private IpAddressCandidateExtractor() {
  }

  /**
   * Finds all syntactically valid IP address literals in the complete string, in source order.
   * Repeated occurrences are retained. Returns an empty list when no address is found.
   * This operation validates addresses with {@link IpAddressClassifier#parse(String)}; it does not apply security policy.
   *
   * <p>Addresses can appear in text, delimited lists, paths, URL hosts, query values, and fragments.
   * List and value separators, quotes, and parentheses can delimit values.
   * Complete entries are validated so malformed addresses and identifiers do not yield shorter matches.
   * URL schemes and authority user information are excluded. IPv6 brackets and valid endpoint ports are removed.
   * A numeric CIDR suffix is retained and validated on a standalone address; slashes in URLs and paths remain separators.
   * Address spelling and IPv6 zones are preserved. Bracketed IPv4 endpoints and bracketed lists are not supported.
   *
   * @see #extract(String)
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    int start = 0;
    while (start < content.length()) {
      if (isEntryDelimiter(content.charAt(start))) {
        start++;
        continue;
      }
      int end = entryEnd(content, start);
      collectAddresses(content.substring(start, end), addresses);
      start = end;
    }
    return addresses;
  }

  private static int entryEnd(String content, int start) {
    int end = start;
    Matcher url = URL_START.matcher(content);
    while (end < content.length()) {
      char c = content.charAt(end);
      if (isEntryDelimiter(c)) {
        break;
      }
      boolean componentStart = end == start || isComponentDelimiter(content.charAt(end - 1));
      // User information can contain list separators. Only an @ in this authority makes them part of the URL.
      if (componentStart && url.region(end, content.length()).lookingAt()) {
        end = authorityHostStart(content, url.end());
      } else {
        int zoneEnd = componentStart ? bracketedZoneEnd(content, end) : end;
        end = zoneEnd > end ? zoneEnd : (end + 1);
      }
    }
    return end;
  }

  private static int authorityHostStart(String content, int start) {
    int host = start;
    boolean bracket = false;
    for (int i = start; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '/' || c == '?' || c == '#' || c == '"' || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
        break;
      }
      if (c == '[' || c == ']') {
        bracket = c == '[';
      } else if (c == '@' && !bracket) {
        host = i + 1;
      }
    }
    return host;
  }

  private static void collectAddresses(String entry, List<String> addresses) {
    boolean path = false;
    boolean urlContext = false;
    int start = 0;
    Matcher url = URL_START.matcher(entry);
    while (start < entry.length()) {
      if (url.region(start, entry.length()).lookingAt()) {
        start = authorityHostStart(entry, url.end());
        path = true;
        urlContext = true;
      }
      int end = componentEnd(entry, start, urlContext);
      String candidate = entry.substring(start, end);
      String cidr = standalonePrefix(entry, end, path);
      stripBracketsAndPort(candidate)
        .map(stripped -> stripped.candidate() + (stripped.portRemoved() ? "" : cidr))
        .filter(address -> IpAddressClassifier.parse(address).isPresent())
        .ifPresent(addresses::add);
      if (!cidr.isEmpty()) {
        break;
      }
      if (end < entry.length()) {
        char separator = entry.charAt(end);
        urlContext |= separator == '?' || separator == '#';
        path |= separator == '/' || urlContext;
      }
      start = end + 1;
    }
  }

  private static int componentEnd(String entry, int start, boolean urlContext) {
    boolean bracket = start < entry.length() && entry.charAt(start) == '[';
    boolean ipv6 = false;
    boolean zone = false;
    int end = bracketedZoneEnd(entry, start);
    while (end < entry.length()) {
      char c = entry.charAt(end);
      // Complete bracketed zones were consumed above. In URLs, & separates unbracketed query values.
      boolean zoneCharacter = zone && !bracket && (c == '=' || c == '@' || (c == '&' && !urlContext));
      if (isComponentDelimiter(c) && !zoneCharacter) {
        break;
      }
      if (c == ']') {
        zone = false;
      } else if (c == ':') {
        ipv6 = true;
      } else if (c == '%' && ipv6) {
        zone = true;
      }
      end++;
    }
    return end;
  }

  private static int bracketedZoneEnd(String content, int start) {
    if (start >= content.length() || content.charAt(start) != '[') {
      return start;
    }
    boolean zone = false;
    for (int end = start + 1; end < content.length(); end++) {
      char c = content.charAt(end);
      if (c == '[' || c == '/') {
        return start;
      }
      if (c == ']') {
        // Only complete, valid bracketed zones can contain entry and component delimiters.
        return zone && IpAddressClassifier.parse(content.substring(start + 1, end)).isPresent() ? (end + 1) : start;
      }
      zone |= c == '%';
    }
    return start;
  }

  private static String standalonePrefix(String entry, int end, boolean path) {
    if (path || end >= entry.length() || entry.charAt(end) != '/' || end + 1 == entry.length()) {
      return "";
    }
    String suffix = entry.substring(end + 1);
    char first = suffix.charAt(0);
    // A path with another component is not a standalone CIDR entry.
    if ((Character.isDigit(first) || first == '+' || first == '-')
      && suffix.chars().noneMatch(c -> isComponentDelimiter((char) c))
      && stripBracketsAndPort(suffix).flatMap(stripped -> IpAddressClassifier.parse(stripped.candidate())).isEmpty()) {
      return "/" + suffix;
    }
    return "";
  }

  private static boolean isComponentDelimiter(char c) {
    return c == '/' || c == '?' || c == '#' || c == '&' || c == '=' || c == '@';
  }

  private static boolean isEntryDelimiter(char c) {
    return c == ',' || c == ';' || isTextDelimiter(c);
  }

  private static boolean isTextDelimiter(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c) || isEnclosure(c);
  }

  private static boolean isEnclosure(char c) {
    return c == '\'' || c == '"' || c == '(' || c == ')';
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
