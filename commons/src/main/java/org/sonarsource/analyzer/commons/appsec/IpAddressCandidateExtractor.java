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

/** Extracts IP address candidates from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final Pattern ENTRIES = Pattern.compile("[^\\s,;\"'()]+", Pattern.UNICODE_CHARACTER_CLASS);
  // Keep identifiers, malformed numeric literals, brackets and endpoint ports together for validation.
  private static final Pattern ADDRESS_PART = Pattern.compile(
    "[\\p{L}\\p{N}\\p{M}_.:+\\-\\[\\]]+(?:%[^\\s,;\"'()/=?#&@]*)?", Pattern.UNICODE_CHARACTER_CLASS);

  private IpAddressCandidateExtractor() {
  }

  /**
   * Finds all syntactically valid IP address literals throughout the given string, in source order,
   * retaining repeated occurrences. Returns an empty list if no address is found.
   * Unlike {@link #extract(String)}, this operation validates candidates with {@link IpAddressClassifier#parse(String)}.
   * It does not apply address classification or hardcoding exceptions.
   *
   * <p>Supports addresses in text, paths, URLs and lists separated by whitespace, commas or semicolons.
   * Quotes, parentheses and value separators can surround addresses. Letters, digits, dots, hyphens and
   * underscores attached to an address prevent a match, including a sentence-ending dot.
   * URL schemes and authority user information are skipped, but hosts, paths, query values and fragments are searched.
   *
   * <p>IPv6 endpoint brackets and valid ports on IPv4 or bracketed IPv6 endpoints are removed.
   * Invalid endpoints are rejected as a whole. Unbracketed IPv6 is never split into an address and port.
   * Zones and valid standalone CIDR suffixes retain their spelling. Slashes in URLs, paths and after ports
   * are path separators. A single numeric (possibly signed) slash suffix on a standalone address is
   * interpreted as a CIDR prefix and must be valid for its address family. Bracketed IPv4 and bracketed lists
   * are not supported.
   *
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher entries = ENTRIES.matcher(content);
    while (entries.find()) {
      extractEntry(entries.group(), addresses);
    }
    return addresses;
  }

  private static void extractEntry(String entry, List<String> addresses) {
    Matcher protocol = URL_PREFIX.matcher(entry);
    Matcher part = ADDRESS_PART.matcher(entry);
    boolean path = false;
    int index = 0;
    while (index < entry.length()) {
      // A double slash inside an existing path does not start a new URL authority.
      boolean urlStart = index == 0 || "=&#?".indexOf(entry.charAt(index - 1)) >= 0;
      if (urlStart && protocol.region(index, entry.length()).lookingAt()) {
        index = protocol.end();
        path = true;
      } else if (part.region(index, entry.length()).lookingAt()) {
        int end = part.end();
        String candidate = part.group();
        Optional<StrippedCandidate> stripped = stripBracketsAndPort(candidate)
          .filter(address -> IpAddressClassifier.parse(address.candidate()).isPresent());
        String suffix = standalonePrefix(entry, end, path || stripped.map(StrippedCandidate::portRemoved).orElse(true));
        stripped.map(address -> address.candidate() + suffix)
          .filter(address -> suffix.isEmpty() || IpAddressClassifier.parse(address).isPresent())
          .ifPresent(addresses::add);
        // A consumed CIDR slash must not turn later standalone values into path segments.
        index = end + suffix.length();
      } else {
        path |= entry.charAt(index) == '/';
        index++;
      }
    }
  }

  private static String standalonePrefix(String entry, int end, boolean path) {
    if (path || end + 1 >= entry.length() || entry.charAt(end) != '/') {
      return "";
    }
    Matcher prefix = ADDRESS_PART.matcher(entry).region(end + 1, entry.length());
    if (!prefix.lookingAt()) {
      return "";
    }
    String suffix = prefix.group();
    // Multiple segments, another address, or a named segment make this a path rather than a CIDR entry.
    // Keep a trailing dot on a numeric prefix so validation rejects a sentence-ending dot.
    int first = suffix.codePointAt(0);
    int dot = suffix.indexOf('.');
    if ((!Character.isDigit(first) && first != '+' && first != '-')
      || (prefix.end() < entry.length() && entry.charAt(prefix.end()) == '/')
      || (dot >= 0 && dot != suffix.length() - 1) || suffix.indexOf(':') >= 0) {
      return "";
    }
    return "/" + suffix;
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
