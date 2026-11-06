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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts IP address candidates from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final Pattern URL_START = Pattern.compile(
    "(?<![a-zA-Z0-9+.-])(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#\\s\"]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern URL_AUTHORITY_END = Pattern.compile("[/?#\\s,;]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ENTRY = Pattern.compile("[^\\s,;]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern VALUE_DELIMITER = Pattern.compile("[=&#?@\"'()]");
  private static final Pattern PORT_DELIMITER = Pattern.compile("[/?#]");
  private static final Pattern QUERY_PORT_DELIMITER = Pattern.compile("[/?#&]");
  private static final Pattern ADDRESS_SUFFIX = Pattern.compile("[/%]");
  private static final Pattern OVERLONG_IPV6_GROUP = Pattern.compile("[0-9a-fA-F]{5,}");
  // Consume identifiers and malformed endpoints whole, including any opaque IPv6 zone.
  private static final Pattern ADDRESS = Pattern.compile("[\\p{L}\\p{N}\\p{M}_.:+\\[\\]-]+(?:%[^/=?#&@\"'()]*)?");

  private IpAddressCandidateExtractor() {
  }

  /**
   * Returns all syntactically valid IP address literals in the complete string, in source order.
   * Repeated occurrences are retained. Returns an empty list when no address is found.
   * The result preserves address spelling and IPv6 zones without applying security classifications.
   *
   * <p>Supports paths, ordinary text, whitespace/comma/semicolon lists, equals-separated values,
   * quotes, parentheses, and URL hosts, paths, queries and fragments. URL schemes and authority user
   * information are excluded. Matches cannot touch letters, digits, dots, hyphens or underscores.
   *
   * <p>Removes IPv6 endpoint brackets and valid IPv4 or bracketed IPv6 ports (0 through 65535,
   * with one to five ASCII digits). Malformed endpoints and bracketed IPv4 addresses are rejected.
   * An unbracketed IPv6 address is never split into an address and port.
   * Standalone CIDRs retain their valid prefix. URL slashes and slashes in paths remain separators.
   * A numeric suffix on a standalone address is treated as a CIDR prefix and must be valid.
   * Bracketed lists are not supported.
   *
   * @see IpAddressClassifier#parse(String)
   */
  public static List<String> extractAll(String content) {
    List<String> result = new ArrayList<>();
    Matcher url = URL_START.matcher(content);
    int offset = 0;
    boolean startsInUrl = false;
    Deque<Character> enclosures = new ArrayDeque<>();
    while (url.find()) {
      extractEntries(content.substring(offset, url.start()), startsInUrl, enclosures, result);
      char enclosure = currentEnclosure(enclosures);
      int hostStart = urlHostStart(content, url, enclosure);
      offset = endpointEnd(URL_AUTHORITY_END, content, hostStart, enclosure);
      // Validate the complete host before interpreting query or value separators.
      stripBracketsAndPort(content.substring(hostStart, offset))
        .map(StrippedCandidate::candidate)
        .filter(IpAddressCandidateExtractor::isValidAddressLiteral)
        .ifPresent(result::add);
      startsInUrl = true;
      url.region(offset, content.length());
    }
    extractEntries(content.substring(offset), startsInUrl, enclosures, result);
    return result;
  }

  private static int urlHostStart(String content, Matcher url, char enclosure) {
    int authorityStart = content.indexOf("//", url.start()) + 2;
    char closing = enclosure == '(' ? ')' : enclosure;
    // User information cannot extend beyond the value that encloses the URL.
    if (closing != '\0' && content.substring(authorityStart, url.end()).indexOf(closing) >= 0) {
      return authorityStart;
    }
    return url.end();
  }

  private static void extractEntries(String content, boolean startsInUrl, Deque<Character> enclosures, List<String> result) {
    Matcher entry = ENTRY.matcher(content);
    while (entry.find()) {
      boolean url = startsInUrl && entry.start() == 0;
      extractEntry(entry.group(), url, enclosures, result);
    }
  }

  private static char currentEnclosure(Deque<Character> enclosures) {
    return enclosures.isEmpty() ? '\0' : enclosures.peek();
  }

  private static void trackEnclosures(String entry, int start, int end, Deque<Character> enclosures) {
    for (int i = start; i < end; i++) {
      char character = entry.charAt(i);
      char enclosure = currentEnclosure(enclosures);
      if (character == '(') {
        enclosures.push(character);
      } else if (character == ')' && enclosure == '(') {
        enclosures.pop();
      } else if (character == '\'' || character == '"') {
        trackQuote(entry, i, enclosures);
      }
    }
  }

  private static void trackQuote(String entry, int offset, Deque<Character> enclosures) {
    char quote = entry.charAt(offset);
    boolean followsWord = offset > 0 && Character.isLetterOrDigit(entry.charAt(offset - 1));
    boolean apostropheInWord = quote == '\'' && followsWord
      && offset + 1 < entry.length() && Character.isLetterOrDigit(entry.charAt(offset + 1));
    if (enclosures.contains(quote) && !apostropheInWord) {
      // Parentheses in quoted text cannot outlive the closing quote.
      while (currentEnclosure(enclosures) != quote) {
        enclosures.pop();
      }
      enclosures.pop();
    } else if (quote == '"' || !followsWord) {
      enclosures.push(quote);
    }
  }

  private static void extractEntry(String entry, boolean url, Deque<Character> enclosures, List<String> result) {
    int firstSlash = entry.indexOf('/');
    Matcher address = ADDRESS.matcher(entry);
    int enclosureOffset = 0;
    while (address.find()) {
      int start = address.start();
      trackEnclosures(entry, enclosureOffset, start, enclosures);
      String endpoint = address.group();
      int end = address.end();
      if (hasEndpointPort(endpoint)) {
        end = endpointPortEnd(entry, start, currentEnclosure(enclosures));
        endpoint = entry.substring(start, end);
        address.region(end, entry.length());
      }
      // Punctuation consumed as part of an endpoint must not affect later values.
      enclosureOffset = end;
      Optional<StrippedCandidate> stripped = stripBracketsAndPort(endpoint);
      if (stripped.isEmpty()) {
        continue;
      }
      String candidate = stripped.get().candidate();
      boolean path = url || (firstSlash >= 0 && firstSlash < start);
      if (!path && !stripped.get().portRemoved() && end < entry.length() && entry.charAt(end) == '/') {
        int valueEnd = nextDelimiter(VALUE_DELIMITER, entry, end);
        if (hasStandalonePrefix(entry.substring(start, valueEnd), end - start)) {
          candidate += entry.substring(end, valueEnd);
          // Consume the complete suffix even when validation rejects it.
          address.region(valueEnd, entry.length());
        }
      }
      if (isValidAddressLiteral(candidate)) {
        result.add(candidate);
      }
    }
    trackEnclosures(entry, enclosureOffset, entry.length(), enclosures);
  }

  private static int endpointPortEnd(String entry, int start, char enclosure) {
    int query = entry.indexOf('?');
    int fragment = entry.indexOf('#');
    boolean parameter = (query >= 0 && query < start) || (fragment >= 0 && fragment < start);
    return endpointEnd(parameter ? QUERY_PORT_DELIMITER : PORT_DELIMITER, entry, start, enclosure);
  }

  private static boolean isValidAddressLiteral(String candidate) {
    if (candidate.indexOf(':') >= 0) {
      int addressEnd = nextDelimiter(ADDRESS_SUFFIX, candidate, 0);
      // The JDK parser also accepts groups with extra leading zeros. IPv6 syntax limits them to four digits.
      if (OVERLONG_IPV6_GROUP.matcher(candidate).region(0, addressEnd).find()) {
        return false;
      }
    }
    return IpAddressClassifier.parse(candidate).isPresent();
  }

  private static int nextDelimiter(Pattern pattern, String content, int start) {
    Matcher delimiter = pattern.matcher(content);
    return delimiter.find(start) ? delimiter.start() : content.length();
  }

  private static int endpointEnd(Pattern delimiter, String content, int start, char enclosure) {
    int end = nextDelimiter(delimiter, content, start);
    int parentheses = 0;
    for (int i = start; i < end; i++) {
      char current = content.charAt(i);
      if ((enclosure == '\'' || enclosure == '"') && current == enclosure) {
        return i;
      }
      if (current == ')' && parentheses == 0 && enclosure == '(') {
        return i;
      }
      if (current == '(') {
        parentheses++;
      } else if (current == ')' && parentheses > 0) {
        parentheses--;
      }
    }
    return end;
  }

  private static boolean hasEndpointPort(String candidate) {
    if (candidate.startsWith("[")) {
      return candidate.contains("]:");
    }
    int colon = candidate.indexOf(':');
    return colon > 0 && candidate.substring(0, colon).indexOf('.') >= 0;
  }

  private static boolean hasStandalonePrefix(String value, int addressEnd) {
    int slash = value.indexOf('/');
    if (slash != addressEnd || slash + 1 >= value.length() || value.indexOf('/', slash + 1) >= 0) {
      return false;
    }
    String suffix = value.substring(slash + 1);
    // Named segments and address-valued segments are paths. Numeric prefix attempts must validate whole.
    char first = suffix.charAt(0);
    return (Character.isDigit(first) || first == '+' || first == '-')
      && stripBracketsAndPort(suffix).filter(segment -> IpAddressClassifier.parse(segment.candidate()).isPresent()).isEmpty();
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
