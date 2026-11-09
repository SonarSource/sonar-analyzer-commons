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
import javax.annotation.Nullable;

/** Heuristically extracts an IP address candidate string from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final Pattern ENTRY_DELIMITER = Pattern.compile("[\\s,;'\"()]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASSIGNMENT_DELIMITER = Pattern.compile("[=&]");
  private static final Pattern URL_START = Pattern.compile("(?<![a-zA-Z0-9+.-])(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");
  private static final Pattern STANDALONE_CIDR = Pattern.compile("([^/]+)/([^/]*)");

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
   * Finds all syntactically valid IP addresses in the given string, in source order and including repeated occurrences.
   * Returns an empty list if there is none. The addresses are not classified: whether an address is permitted
   * by a security rule is left to the caller, see {@link IpAddressClassifier}.
   *
   * <p>Unlike {@link #extract(String)}, the whole string is searched, so addresses can be found in path segments,
   * after ordinary text, in lists separated by whitespace, commas, or semicolons, and after an equals sign.
   * Quotes and parentheses can enclose values. IPv4, IPv6, IPv6 with an embedded IPv4 tail and IPv6 zones are supported.
   * A complete IPv6 address is a single match.
   *
   * <p>A match must be delimited: it must not touch a letter, digit, dot, hyphen or underscore.
   * Hence, no address is recovered from a longer malformed address or identifier, such as {@code 1.2.3.4.5},
   * {@code v1.2.3.4} or {@code 1.2.3.4-beta}. A sentence-ending dot, as in {@code "see 1.2.3.4."}, prevents a match.
   *
   * <p>The returned text is stripped of URL syntax (scheme, user information, and path), IPv6 endpoint brackets,
   * and ports. Ports are removed from IPv4 endpoints and bracketed IPv6 endpoints only, and must be valid
   * (one to five ASCII digits, up to 65535); an invalid port invalidates the endpoint.
   * Bracketed IPv4 endpoints and bracketed lists are not supported.
   * The zone of an IPv6 address and a valid CIDR suffix of a standalone address entry, such as {@code 10.0.0.0/8}, are preserved.
   * An invalid suffix invalidates the entry. Slashes in URLs, in endpoints with ports, and in paths are path separators.
   * An entry is a run of text between whitespace, quotes, parentheses, commas, and semicolons; its values are separated by {@code =} and {@code &}.
   * From the first URL of an entry on, the rest of the entry is URL text, so later values do not get CIDR handling.
   *
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    int start = 0;
    Matcher delimiter = ENTRY_DELIMITER.matcher(content);
    while (delimiter.find()) {
      collectFromEntry(content.substring(start, delimiter.start()), addresses);
      start = delimiter.end();
    }
    collectFromEntry(content.substring(start), addresses);
    return addresses;
  }

  private static void collectFromEntry(String entry, List<String> addresses) {
    if (entry.isEmpty()) {
      return;
    }
    // Detect URLs before splitting values, because user information can contain `=` and `&`.
    // Everything from the first URL on is URL text, where slashes are path separators.
    Matcher url = URL_START.matcher(entry);
    boolean hasUrl = url.find();
    String values = hasUrl ? entry.substring(0, url.start()) : entry;
    // Without URL syntax, CIDR suffixes are only recognized on a standalone value, not in a path or query.
    boolean standalone = entry.indexOf('?') < 0 && entry.indexOf('#') < 0;
    for (String value : standalone ? ASSIGNMENT_DELIMITER.split(values) : new String[] {values}) {
      Matcher cidr = STANDALONE_CIDR.matcher(value);
      if (standalone && cidr.matches()) {
        collectCidr(cidr.group(1), cidr.group(2), addresses);
      } else {
        collectFromText(value, addresses);
      }
    }
    if (hasUrl) {
      collectFromText(entry.substring(url.start()), addresses);
    }
  }

  /** An address followed by a single slash is a CIDR entry: an invalid suffix invalidates it. Otherwise, the slash separates a path. */
  private static void collectCidr(String endpoint, String suffix, List<String> addresses) {
    Resolved resolved = resolve(endpoint);
    if (resolved == null) {
      collectFromText(endpoint + "/" + suffix, addresses);
    } else if (resolved.portRemoved()) {
      // Slash suffixes after ports are paths.
      collectFromText(endpoint + "/" + suffix, addresses);
    } else if (IpAddressClassifier.parse(resolved.address() + "/" + suffix).isPresent()) {
      addresses.add(resolved.address() + "/" + suffix);
    }
  }

  /** Collects addresses from text that may contain URLs, whose scheme and user information are not candidates. */
  private static void collectFromText(String text, List<String> addresses) {
    int position = 0;
    Matcher url = URL_START.matcher(text);
    while (position < text.length() && url.find(position)) {
      collectFromTokens(text.substring(position, url.start()), addresses);
      int hostStart = hostStart(text, url.end());
      int hostEnd = tokenEnd(text, hostStart);
      addResolved(text.substring(hostStart, hostEnd), addresses);
      position = hostEnd;
    }
    if (position < text.length()) {
      collectFromTokens(text.substring(position), addresses);
    }
  }

  /** Skips the user information of the URL authority starting at the given index. */
  private static int hostStart(String text, int authorityStart) {
    int authorityEnd = authorityStart;
    while (authorityEnd < text.length() && "/?#".indexOf(text.charAt(authorityEnd)) < 0) {
      authorityEnd++;
    }
    int userInfoEnd = text.lastIndexOf('@', authorityEnd - 1);
    return userInfoEnd >= authorityStart ? (userInfoEnd + 1) : authorityStart;
  }

  /** Every maximal run of characters that can be part of an address is a candidate. */
  private static void collectFromTokens(String text, List<String> addresses) {
    int i = 0;
    while (i < text.length()) {
      int end = tokenEnd(text, i);
      if (end > i) {
        addResolved(text.substring(i, end), addresses);
        i = end;
      } else {
        i += Character.charCount(text.codePointAt(i));
      }
    }
  }

  private static int tokenEnd(String text, int start) {
    int end = start;
    while (end < text.length() && isTokenChar(text.codePointAt(end))) {
      end += Character.charCount(text.codePointAt(end));
    }
    return end;
  }

  private static boolean isTokenChar(int codePoint) {
    return Character.isLetterOrDigit(codePoint) || (codePoint < 128 && ".-_:%[]".indexOf(codePoint) >= 0);
  }

  private static void addResolved(String token, List<String> addresses) {
    Resolved resolved = resolve(token);
    if (resolved != null) {
      addresses.add(resolved.address());
    }
  }

  /** Strips IPv6 brackets and the port of an endpoint, and returns it only if the remaining address is valid. */
  @Nullable
  private static Resolved resolve(String token) {
    Resolved endpoint = token.startsWith("[") ? resolveBracketed(token) : resolveUnbracketed(token);
    if (endpoint == null
      || endpoint.address().indexOf('[') >= 0
      || endpoint.address().indexOf(']') >= 0
      || IpAddressClassifier.parse(endpoint.address()).isEmpty()) {
      return null;
    }
    return endpoint;
  }

  @Nullable
  private static Resolved resolveBracketed(String token) {
    int closing = token.indexOf(']');
    if (closing < 0) {
      return null;
    }
    String address = token.substring(1, closing);
    // IPv4 addresses must not be enclosed in brackets.
    if (address.indexOf(':') < 0) {
      return null;
    }
    String rest = token.substring(closing + 1);
    if (rest.isEmpty()) {
      return new Resolved(address, false);
    }
    return rest.charAt(0) == ':' && isPort(rest.substring(1)) ? new Resolved(address, true) : null;
  }

  @Nullable
  private static Resolved resolveUnbracketed(String token) {
    int colon = token.indexOf(':');
    // Only an IPv4 endpoint has a port without brackets; the last group of an IPv6 address is not a port.
    if (colon > 0 && token.substring(0, colon).indexOf('.') >= 0) {
      return isPort(token.substring(colon + 1)) ? new Resolved(token.substring(0, colon), true) : null;
    }
    return new Resolved(token, false);
  }

  private record Resolved(String address, boolean portRemoved) {
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
