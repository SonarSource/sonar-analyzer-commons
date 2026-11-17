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
  // Separate entries of a list, values, and the enclosing quotes or parentheses.
  private static final Pattern WORD_SEPARATOR = Pattern.compile("[\"'`()\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern LIST_SEPARATOR = Pattern.compile("[,;]");
  private static final String PUNCTUATION = "!|+*<>{}\\~$^";
  private static final Pattern VALUE_SEPARATOR = Pattern.compile("[=&?#]");

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
   * Finds all syntactically valid IP address literals throughout the given string, in source order.
   * Repeated occurrences are kept. The result is empty when no address is found.
   * The result is not filtered by any security rule: classification is left to {@link IpAddressClassifier}.
   *
   * <p>The string is split into entries at whitespace, commas, semicolons, equals signs, ampersands,
   * question marks, number signs, quotes, and parentheses. Each entry is then examined as a whole:
   *
   * <ul>
   *   <li>An address must not touch a letter, digit, dot, hyphen, underscore, or other text. Nothing is recovered
   *     from a longer malformed address or identifier; a sentence-ending dot prevents a match.</li>
   *   <li>The scheme and user information of a URL are skipped. The host is returned if it is an address,
   *     and the path segments, query values, and fragment are searched in either case.</li>
   *   <li>Brackets around an IPv6 address are removed. A bracketed IPv4 address is not supported.
   *     An ASCII decimal port from 0 through 65535 is removed from IPv4 endpoints and bracketed IPv6 endpoints.
   *     An invalid port invalidates the endpoint. The last group of an unbracketed IPv6 address is never a port.</li>
   *   <li>A valid CIDR prefix is preserved on an entry that consists only of an address and the suffix.
   *     An invalid prefix length invalidates the entry. Other slashes separate path segments.</li>
   *   <li>An IPv6 address with an embedded IPv4 tail is a single match. Zones are preserved as written.</li>
   * </ul>
   *
   * <p>{@link #extract(String)} is unchanged and examines only the start of the string.
   */
  public static List<String> extractAll(String content) {
    List<String> result = new ArrayList<>();
    for (String word : WORD_SEPARATOR.split(content)) {
      collectFromWord(word, result);
    }
    return result;
  }

  private static void collectFromWord(String word, List<String> result) {
    int start = 0;
    while (start <= word.length()) {
      int end;
      if (isUrlAt(word, start)) {
        // A comma or semicolon within a URL path, query or user information belongs to it, unless another URL follows.
        end = nextListSeparator(word, start, true);
        collectFromUrl(word.substring(start, end), result);
      } else {
        end = nextListSeparator(word, start, false);
        collectFromText(word.substring(start, end), result);
      }
      start = end + 1;
    }
  }

  private static boolean isUrlAt(String text, int start) {
    return URL_PREFIX.matcher(text).region(start, text.length()).lookingAt();
  }

  private static int nextListSeparator(String word, int start, boolean inUrl) {
    Matcher separator = LIST_SEPARATOR.matcher(word);
    int authorityEnd = word.length();
    if (inUrl) {
      int authorityStart = word.indexOf("//", start) + 2;
      authorityEnd = authorityStart;
      while (authorityEnd < word.length() && "/?#".indexOf(word.charAt(authorityEnd)) < 0) {
        authorityEnd++;
      }
    }
    int from = start;
    while (separator.find(from)) {
      int index = separator.start();
      // Within a URL, a separator ends the entry if another URL follows, or in the authority unless it is user information.
      boolean endsUrl = isUrlAt(word, separator.end())
        || (index < authorityEnd && !(word.indexOf('@', index) >= 0 && word.indexOf('@', index) < authorityEnd));
      if (!inUrl || endsUrl) {
        return index;
      }
      from = separator.end();
    }
    return word.length();
  }

  private static void collectFromText(String text, List<String> result) {
    // A URL after a value separator extends to the end of the text: punctuation, query and fragment stay in its context.
    int urlStart = text.length();
    for (int i = 1; i < text.length(); i++) {
      char previous = text.charAt(i - 1);
      if ((VALUE_SEPARATOR.matcher(String.valueOf(previous)).matches() || PUNCTUATION.indexOf(previous) >= 0) && isUrlAt(text, i)) {
        urlStart = i;
        break;
      }
    }
    for (String entry : splitKeepingZones(text.substring(0, urlStart), "=&?#")) {
      collectFromEntry(entry, result);
    }
    if (urlStart < text.length()) {
      collectFromUrl(text.substring(urlStart), result);
    }
  }

  /**
   * Splits at the given delimiters and at ordinary punctuation, except that punctuation inside the zone of an IPv6 address
   * (after a '%' in a piece containing a colon) is kept: the zone is opaque text that ends only at a delimiter.
   */
  private static List<String> splitKeepingZones(String text, String delimiters) {
    List<String> pieces = new ArrayList<>();
    StringBuilder piece = new StringBuilder();
    boolean inZone = false;
    for (char c : text.toCharArray()) {
      boolean punctuation = PUNCTUATION.indexOf(c) >= 0;
      if (delimiters.indexOf(c) >= 0 || (punctuation && !inZone)) {
        pieces.add(piece.toString());
        piece.setLength(0);
        inZone = false;
      } else {
        inZone |= c == '%' && piece.indexOf(":") >= 0;
        piece.append(c);
      }
    }
    pieces.add(piece.toString());
    return pieces;
  }

  private static void collectFromEntry(String entry, List<String> result) {
    int slash = entry.indexOf('/');
    if (slash >= 0 && slash == entry.lastIndexOf('/')) {
      String suffix = entry.substring(slash + 1);
      if (ASCII_DIGITS.matcher(suffix).matches()) {
        parseWithCidr(entry.substring(0, slash), suffix, result);
        return;
      }
    }
    collectFromSegments(entry, result);
  }

  private static void collectFromUrl(String url, List<String> result) {
    Matcher protocol = URL_PREFIX.matcher(url);
    if (protocol.find()) {
      String rest = url.substring(protocol.end());
      int pathStart = indexOfAny(rest, "/?#&=");
      parseEndpoint(pathStart >= 0 ? rest.substring(0, pathStart) : rest).ifPresent(result::add);
      if (pathStart >= 0) {
        for (String part : VALUE_SEPARATOR.split(rest.substring(pathStart))) {
          collectFromSegments(part, result);
        }
      }
    }
  }

  private static int indexOfAny(String text, String chars) {
    for (int i = 0; i < text.length(); i++) {
      if (chars.indexOf(text.charAt(i)) >= 0) {
        return i;
      }
    }
    return -1;
  }

  private static void parseWithCidr(String address, String suffix, List<String> result) {
    Optional<StrippedCandidate> stripped = stripBracketsAndPort(address).filter(IpAddressCandidateExtractor::isAddress);
    if (stripped.isEmpty()) {
      // Possibly a path such as user@192.0.2.1/24
      collectFromSegments(address, result);
      return;
    }
    StrippedCandidate candidate = stripped.get();
    if (candidate.portRemoved()) {
      // An endpoint is not a network: the digits are a path.
      result.add(candidate.candidate());
    } else if (isValidPrefixLength(suffix, candidate.candidate().indexOf(':') >= 0 ? 128 : 32)) {
      result.add(candidate.candidate() + "/" + suffix);
    }
  }

  private static boolean isValidPrefixLength(String digits, int maxBits) {
    String significant = digits.replaceFirst("^0+(?=\\d)", "");
    return significant.length() <= 3 && Integer.parseInt(significant) <= maxBits;
  }

  private static void collectFromSegments(String path, List<String> result) {
    for (String segment : splitKeepingZones(path, "/@,;")) {
      parseEndpoint(segment).ifPresent(result::add);
    }
  }

  private static Optional<String> parseEndpoint(String segment) {
    return stripBracketsAndPort(segment).filter(IpAddressCandidateExtractor::isAddress).map(StrippedCandidate::candidate);
  }

  private static boolean isAddress(StrippedCandidate stripped) {
    return !stripped.candidate().endsWith(".") && IpAddressClassifier.parse(stripped.candidate()).isPresent();
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
