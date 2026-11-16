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

/** Heuristically extracts IP address strings from a string literal's content. */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");

  private static final Pattern URL_START = Pattern.compile(
    "(?<![a-zA-Z0-9+.-])(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#\\s\"'()]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ENTRY_SEPARATOR = Pattern.compile("[\\s,;\"'()]", Pattern.UNICODE_CHARACTER_CLASS);
  // Keep identifiers, malformed addresses, brackets and port syntax together, rather than recovering valid substrings.
  // Zones are opaque to address validation; retain their spelling up to an entry or URL delimiter.
  private static final Pattern ADDRESS_ENTRY = Pattern.compile(
    "[\\p{L}\\p{N}\\p{M}._:\\[\\]+/\\-]+(?:%[^\\s,;\"'()?#=&@<>]*)?", Pattern.UNICODE_CHARACTER_CLASS);

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
   * Finds all syntactically valid IP address literals throughout {@code content}, in source order, including duplicates.
   * Returns an empty list when none are found. Address spelling and IPv6 zones are preserved. This operation does not
   * classify addresses or decide whether they may be hardcoded.
   *
   * <p>Supports addresses in text, paths, whitespace/comma/semicolon-separated lists, and values enclosed in quotes or
   * parentheses or following an equals sign. Letters, digits, dots, hyphens and underscores remain attached to a
   * candidate, so identifiers, malformed addresses and sentence-ending dots prevent a match.
   *
   * <p>URL schemes and authority user information are excluded; hosts, paths, query values and fragments are searched.
   * IPv6 endpoint brackets and valid ports on IPv4 or bracketed IPv6 endpoints are removed. Ports must contain one to
   * five ASCII digits and be at most 65535. Invalid endpoints are rejected as a whole. Bracketed IPv4 and bracketed
   * lists are unsupported. Unbracketed IPv6 is never interpreted as having a port.
   *
   * <p>Valid standalone CIDR suffixes are preserved and invalid numeric prefixes reject the entry. Slashes in URLs,
   * paths, and after endpoint ports are path separators. A single numeric slash suffix on a bare address is a CIDR;
   * a nonnumeric path segment or additional slashes identify a path.
   *
   * @see #extract(String) the original, unvalidated, first-entry-only operation
   * @see IpAddressClassifier#parse(String) address and CIDR validation
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher url = URL_START.matcher(content);
    int start = 0;
    int urlEnd = 0;
    while (url.find(start)) {
      findBetweenUrls(content, start, url.start(), urlEnd, addresses);
      // A URL continues through its query and fragment, but stops at a list or text delimiter.
      Matcher end = ENTRY_SEPARATOR.matcher(content);
      int hostStart = url.end();
      if (hostStart >= urlEnd) {
        urlEnd = end.find(hostStart) ? end.start() : content.length();
      }
      start = hostStart;
    }
    findBetweenUrls(content, start, content.length(), urlEnd, addresses);
    return addresses;
  }

  private static void findBetweenUrls(String content, int start, int end, int urlEnd, List<String> addresses) {
    int split = Math.clamp(urlEnd, start, end);
    findAddresses(content.substring(start, split), true, addresses);
    findAddresses(content.substring(split, end), false, addresses);
  }

  private static void findAddresses(String content, boolean url, List<String> addresses) {
    Matcher entry = ADDRESS_ENTRY.matcher(content);
    while (entry.find()) {
      findInPath(entry.group(), url, addresses);
    }
  }

  private static void findInPath(String entry, boolean url, List<String> addresses) {
    int slash = entry.indexOf('/');
    if (!url && slash > 0 && slash == entry.lastIndexOf('/')) {
      Optional<StrippedCandidate> first = validEndpoint(entry.substring(0, slash));
      String suffix = entry.substring(slash + 1);
      if (first.isPresent() && !first.get().portRemoved() && isNumericPrefix(suffix)
        && validEndpoint(suffix).isEmpty()) {
        addIfValid(first.get().candidate() + "/" + suffix, addresses);
        return;
      }
    }
    // A URL or a path can contain several addresses, including one after a named host or a malformed endpoint.
    for (String part : entry.split("/")) {
      validEndpoint(part).ifPresent(stripped -> addresses.add(stripped.candidate()));
    }
  }

  private static Optional<StrippedCandidate> validEndpoint(String candidate) {
    return stripBracketsAndPort(candidate).filter(stripped -> IpAddressClassifier.parse(stripped.candidate()).isPresent());
  }

  private static boolean isNumericPrefix(String suffix) {
    return suffix.isEmpty() || Character.isDigit(suffix.codePointAt(0)) || suffix.charAt(0) == '+' || suffix.charAt(0) == '-';
  }

  private static void addIfValid(String candidate, List<String> addresses) {
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
