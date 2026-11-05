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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristically extracts IP address candidate strings from a string literal's content.
 * <ul>
 *   <li>{@link #extract(String)} interprets the whole content as a single address, endpoint, URL, or delimited list,
 *     and returns one candidate from its beginning.</li>
 *   <li>{@link #extractAll(String)} scans the whole content and returns every embedded address-shaped candidate,
 *     for example in path segments, surrounding text, or lists.</li>
 * </ul>
 */
public final class IpAddressCandidateExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile(
    "^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//(?:[^/?#,@\\s]*@)?", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");

  // Runs of characters that can form an IPv4 or IPv6 address. Boundaries and shapes are checked separately.
  private static final Pattern ADDRESS_RUN = Pattern.compile("[0-9A-Fa-f:.]+");
  // URL authority user information, such as "user:pass" in "http://user:pass@host", using the same delimiters as URL_PREFIX.
  private static final Pattern URL_USER_INFO = Pattern.compile("//([^/?#,@\\s]*)@", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern IPV4_SHAPE = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
  private static final Pattern HEX_GROUP = Pattern.compile("[0-9A-Fa-f]+");
  private static final int IPV6_GROUPS = 8;

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
   * Heuristically extracts all IP address candidates embedded anywhere in the given string, in order of appearance.
   * Duplicates are retained. Returns an empty list if no candidate is found.
   *
   * <p>Unlike {@link #extract(String)}, the content is not interpreted as a single address, endpoint, or URL.
   * Instead, it is scanned for address-shaped tokens, so candidates are also found in path segments
   * ({@code /registry/ipaddresses/192.168.2.3}), surrounding text ({@code connect to 192.0.2.1 now}),
   * and lists separated by any delimiter ({@code 192.0.2.1;192.0.2.2}).
   *
   * More specifically:
   *
   * <ul>
   *   <li>A candidate is either four dot-separated groups of one to three ASCII digits, or an IPv6 address shape:
   *     hexadecimal groups separated by colons, with at most one {@code ::}, eight groups (or seven when {@code ::} compresses some),
   *     and an optional dotted IPv4 tail counting as two groups. An optional IPv6 zone such as {@code %eth0} is preserved.</li>
   *   <li>A token adjacent to a letter, digit, underscore, or hyphen, or that is part of a longer dotted or colon-separated sequence,
   *     is not a candidate. For example, {@code v1.2.3.4}, {@code 1.3.6.1.4.1}, {@code host-192.0.2.1}, and {@code 12:30:45}
   *     yield no candidate, nor does a dotted name such as {@code 1.2.0.192.in-addr.arpa}.
   *     A dot followed by a non-word character, such as a full stop, ends the candidate.</li>
   *   <li>Enclosing brackets and ports are not part of the candidate, for example in {@code [2001:db8::1]:8080} or {@code 192.0.2.1:8080}.</li>
   *   <li>A slash suffix of one to three ASCII digits is preserved as a CIDR prefix, unless it follows a port
   *     or the address is a URL host or user information host (preceded by {@code //} or {@code @}),
   *     where it is a path. As with {@link #extract(String)}, out-of-range prefixes such as {@code /33} are preserved.</li>
   *   <li>Addresses in URL user information are skipped, as in {@code http://192.0.2.1:pass@example.org} or
   *     {@code http://user:192.0.2.1@example.org}, while the URL host, path, query, and fragment are still searched.
   *     An address directly followed by {@code @} is also skipped as user information, as in {@code 192.0.2.1@example.org}.</li>
   *   <li>An IPv4 address that is the version of a product token, such as {@code Chrome/120.0.0.0} in a User-Agent string,
   *     is skipped. A product token is a single name starting with an ASCII letter at the beginning of the content
   *     or after whitespace, an opening parenthesis, a comma, or a semicolon, directly followed by {@code /} and the address.
   *     A slash-separated path such as {@code /registry/192.168.2.3} is not a product token.</li>
   * </ul>
   *
   * <p>Candidates are syntactically shaped like IP addresses but not validated:
   * octets such as {@code 999}, leading zeros, or CIDR prefixes may be out of range, and other dotted quads, such as
   * four-part version numbers, may be returned. Use {@link IpAddressClassifier#parse(String)} to validate and parse each candidate.
   * Use {@link #extract(String)} when the whole content is expected to be a single address and malformed content should
   * remain observable as an unparseable candidate.
   */
  public static List<String> extractAll(String content) {
    List<String> candidates = new ArrayList<>();
    boolean[] userInfo = userInfoCharacters(content);
    Matcher run = ADDRESS_RUN.matcher(content);
    int from = 0;
    while (from < content.length() && run.find(from)) {
      from = addRunCandidates(content, run.start(), run.end(), userInfo, candidates);
    }
    return Collections.unmodifiableList(candidates);
  }

  /** Adds the candidates of an address-shaped run and returns the index where scanning continues. */
  private static int addRunCandidates(String content, int start, int runEnd, boolean[] userInfo, List<String> candidates) {
    int end = runEnd;
    // Trailing dots, such as a full stop, are not part of an address.
    while (end - 1 > start && content.charAt(end - 1) == '.') {
      end--;
    }
    // An opaque IPv6 zone identifier extends the address. Otherwise, the character after any trailing dots bounds the run.
    boolean zoned = charAt(content, end) == '%';
    int addressEnd = zoned ? zoneEnd(content, end + 1) : end;
    int boundedEnd = zoned ? addressEnd : runEnd;
    boolean leftBounded = isBoundaryBefore(content, start);
    boolean rightBounded = isBoundary(content, boundedEnd);
    String token = content.substring(start, end);
    // A single trailing colon is punctuation, as in "Server ::1: connection refused".
    boolean punctuated = !zoned && token.endsWith(":") && !token.endsWith("::");
    if (punctuated && !isIpv6Shaped(token)) {
      token = token.substring(0, token.length() - 1);
      addressEnd--;
    }
    if (leftBounded && rightBounded && isIpv6Shaped(token)) {
      addCandidate(content, start, addressEnd, userInfo, candidates);
      return boundedEnd;
    }
    addIpv4Candidates(content, start, end, leftBounded, rightBounded, userInfo, candidates);
    return runEnd;
  }

  /**
   * Adds the IPv4 addresses among the colon-separated segments of a run, such as "192.0.2.1" in "192.0.2.1:8080" or "host:192.0.2.1".
   * The first and last segments are only candidates if the run is bounded on that side.
   */
  private static void addIpv4Candidates(String content, int runStart, int runEnd, boolean leftBounded, boolean rightBounded,
    boolean[] userInfo, List<String> candidates) {
    // Scan only the run itself, keeping the overall extraction linear in the content length.
    int segmentStart = runStart;
    for (int segmentEnd = runStart; segmentEnd <= runEnd; segmentEnd++) {
      if (segmentEnd < runEnd && content.charAt(segmentEnd) != ':') {
        continue;
      }
      boolean bounded = (leftBounded || segmentStart > runStart) && (rightBounded || segmentEnd < runEnd);
      if (bounded && IPV4_SHAPE.matcher(content.substring(segmentStart, segmentEnd)).matches() && !isProductVersion(content, segmentStart)) {
        addCandidate(content, segmentStart, segmentEnd, userInfo, candidates);
      }
      segmentStart = segmentEnd + 1;
    }
  }

  private static void addCandidate(String content, int start, int end, boolean[] userInfo, List<String> candidates) {
    boolean bracketed = charAt(content, start - 1) == '[' && charAt(content, end) == ']';
    int before = bracketed ? (start - 1) : start;
    int after = bracketed ? (end + 1) : end;
    if (userInfo[start] || charAt(content, after) == '@') {
      return;
    }
    // A port (":") right after the address excludes a CIDR suffix, and URL hosts are followed by paths.
    boolean urlHost = charAt(content, before - 1) == '@' || (charAt(content, before - 1) == '/' && charAt(content, before - 2) == '/');
    String cidr = (urlHost || charAt(content, after) != '/') ? "" : cidrSuffix(content, after + 1);
    candidates.add(content.substring(start, end) + cidr);
  }

  /** Returns a slash and the CIDR prefix starting at the given index, or an empty string if there is no prefix. */
  private static String cidrSuffix(String content, int prefixStart) {
    int prefixEnd = prefixStart;
    while (prefixEnd < content.length() && isAsciiDigit(content.charAt(prefixEnd))) {
      prefixEnd++;
    }
    // Three digits cover both IPv4 and IPv6 prefixes.
    int digits = prefixEnd - prefixStart;
    boolean isPrefix = digits >= 1 && digits <= 3 && !isWordCharacter(content, prefixEnd);
    return isPrefix ? content.substring(prefixStart - 1, prefixEnd) : "";
  }

  private static boolean[] userInfoCharacters(String content) {
    boolean[] userInfo = new boolean[content.length()];
    Matcher matcher = URL_USER_INFO.matcher(content);
    while (matcher.find()) {
      Arrays.fill(userInfo, matcher.start(1), matcher.end(1), true);
    }
    return userInfo;
  }

  /** Returns the end of an opaque IPv6 zone identifier starting at the given index. */
  private static int zoneEnd(String content, int zoneStart) {
    int end = zoneStart;
    while (isWordCharacter(content, end) || charAt(content, end) == '-') {
      end += Character.charCount(content.codePointAt(end));
    }
    return end;
  }

  private static boolean isIpv6Shaped(String token) {
    String hexPart = token;
    int tailGroups = 0;
    if (token.indexOf('.') >= 0) {
      int lastColon = token.lastIndexOf(':');
      if (!IPV4_SHAPE.matcher(token.substring(lastColon + 1)).matches()) {
        return false;
      }
      // The IPv4 tail takes the place of the last two groups. Keep its colon so a preceding "::" stays intact.
      hexPart = token.substring(0, lastColon + 1) + "0";
      tailGroups = 1;
    }
    int compression = hexPart.indexOf("::");
    if (compression < 0) {
      int groups = countHexGroups(hexPart);
      return groups >= 0 && groups + tailGroups == IPV6_GROUPS;
    }
    if (hexPart.indexOf("::", compression + 1) >= 0) {
      return false;
    }
    int head = countHexGroups(hexPart.substring(0, compression));
    int tail = countHexGroups(hexPart.substring(compression + 2));
    return head >= 0 && tail >= 0 && head + tail + tailGroups < IPV6_GROUPS;
  }

  /** Returns the number of colon-separated hexadecimal groups, or -1 if a group is empty or not hexadecimal. */
  private static int countHexGroups(String groups) {
    if (groups.isEmpty()) {
      return 0;
    }
    String[] split = groups.split(":", -1);
    for (String group : split) {
      if (!HEX_GROUP.matcher(group).matches()) {
        return -1;
      }
    }
    return split.length;
  }

  private static boolean isProductVersion(String content, int addressStart) {
    int slash = addressStart - 1;
    if (charAt(content, slash) != '/') {
      return false;
    }
    int nameStart = slash;
    while (nameStart > 0 && isProductNameCharacter(content.charAt(nameStart - 1))) {
      nameStart--;
    }
    char preceding = charAt(content, nameStart - 1);
    return nameStart < slash && isAsciiLetter(content.charAt(nameStart))
      && (nameStart == 0 || Character.isWhitespace(preceding) || "(,;".indexOf(preceding) >= 0);
  }

  private static boolean isProductNameCharacter(char c) {
    return isAsciiLetter(c) || isAsciiDigit(c) || c == '_' || c == '.' || c == '-';
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /** True if the index is out of bounds or the character there cannot be part of an address token's word. */
  private static boolean isBoundary(String content, int index) {
    return !isWordCharacter(content, index) && charAt(content, index) != '-';
  }

  /** Like {@link #isBoundary(String, int)} for the code point before the index, including supplementary characters. */
  private static boolean isBoundaryBefore(String content, int index) {
    return index <= 0 || isBoundary(content, index - Character.charCount(content.codePointBefore(index)));
  }

  private static boolean isWordCharacter(String content, int index) {
    if (index < 0 || index >= content.length()) {
      return false;
    }
    int codePoint = content.codePointAt(index);
    return Character.isLetterOrDigit(codePoint) || codePoint == '_';
  }

  /** Returns the character at the given index, or {@code '\0'} if the index is out of bounds. */
  private static char charAt(String content, int index) {
    return (index >= 0 && index < content.length()) ? content.charAt(index) : '\0';
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
