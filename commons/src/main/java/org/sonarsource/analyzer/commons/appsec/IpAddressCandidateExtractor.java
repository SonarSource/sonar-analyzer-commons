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
  private static final Pattern URL_START = Pattern.compile("(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");
  private static final String ZONE_PUNCTUATION = "|~!$*^";
  private static final Pattern URL_PIECE_DELIMITER = Pattern.compile("[?#&=]");
  // A signed number is still an attempt at a prefix length, which the classifier then rejects.
  private static final Pattern CIDR_ATTEMPT = Pattern.compile("[+-]?\\d+");
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");

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
   * Finds every syntactically valid IP address literal in the complete string, in source order, keeping repeated occurrences.
   * Returns an empty list if there is none. Whether an address is permitted by a rule is not decided here.
   * The existing {@link #extract(String)} is unchanged and still examines only the start of the string.
   *
   * <p>Entries are delimited by any character other than a letter, a digit, {@code .}, {@code -}, {@code _},
   * {@code :}, {@code %}, {@code /}, {@code @}, {@code +}, {@code [} and {@code ]}. In particular, whitespace, commas,
   * semicolons, equals signs, quotes and parentheses delimit entries. The plus sign belongs to the entry so that URL
   * schemes such as {@code git+ssh} stay intact; it prevents a match when it touches an address, but an IPv6 zone can contain it.
   * An IPv6 zone is opaque: after a {@code %} that follows an IPv6 address, the characters {@code |}, {@code ~}, {@code !},
   * {@code $}, {@code *} and {@code ^} also stay in the entry, up to the next {@code /}, {@code ?}, {@code #},
   * {@code &}, {@code =} or {@code ]}, which closes a bracketed zone. An {@code @} inside a zone belongs to it.
   * A percent sign after other text, such as {@code load:50%}, does not start a zone.
   * Other characters still delimit the entry, so a zone is not arbitrary text. An address must span a whole entry or a whole path segment,
   * so it never touches a letter, digit, extra dot, hyphen or underscore; a sentence-ending dot prevents a match.
   * A valid-looking address is not recovered from a longer malformed address or identifier.
   *
   * <ul>
   *   <li>Supports IPv4, IPv6, IPv6 with an embedded IPv4 tail, and IPv6 zones as accepted by {@link IpAddressClassifier#parse(String)}.
   *     A complete IPv6 address is a single match.</li>
   *   <li>In URLs, excludes the scheme and the user information, and searches the host, path segments, query values and fragment.
   *     A host name does not stop the search of the rest of the URL.</li>
   *   <li>Removes the brackets of an IPv6 endpoint, and the port of an IPv4 endpoint or bracketed IPv6 endpoint.
   *     A valid port has one to five ASCII digits and a value up to 65535; an invalid port invalidates the endpoint.
   *     A bracketed IPv4 address is not supported, and neither are bracketed lists.</li>
   *   <li>Preserves a valid CIDR prefix on an entry that is a standalone address, for example {@code 10.0.0.0/8}.
   *     A prefix that is invalid for the address family invalidates the entry.
   *     Other slashes, including all slashes within a URL, are path separators.</li>
   * </ul>
   *
   * <p>Every returned text is accepted by {@link IpAddressClassifier#parse(String)}.
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    int length = content.length();
    int i = 0;
    while (i < length) {
      if (!isEntryChar(content, i)) {
        i++;
        continue;
      }
      // The scheme characters are all entry characters, so this scan cannot restart inside the entry.
      boolean url = URL_START.matcher(content).region(i, length).lookingAt();
      int end = entryEnd(content, i, url);
      String entry = content.substring(i, end);
      if (url) {
        collectFromUrl(entry, addresses);
      } else {
        collectFromEntry(entry, addresses);
      }
      i = end;
    }
    return addresses;
  }

  private static int entryEnd(String content, int start, boolean url) {
    int length = content.length();
    // A segment is the text since the last delimiter of a URL, an authority, a bracketed address or a path.
    int segmentStart = start;
    int colons = 0;
    boolean zone = false;
    boolean notZone = false;
    int end = start;
    while (end < length) {
      char c = content.charAt(end);
      // A zone is opaque, so it keeps the punctuation that would otherwise delimit entries.
      boolean accepted = (url ? isUrlChar(content, end) : isEntryChar(content, end)) || (zone && ZONE_PUNCTUATION.indexOf(c) >= 0);
      if (!accepted) {
        break;
      }
      // User information ends at an at sign, but an at sign inside a zone belongs to the opaque zone.
      if ("/?#&=]".indexOf(c) >= 0 || (c == '@' && !zone)) {
        segmentStart = end + 1;
        colons = 0;
        zone = false;
        notZone = false;
      } else if (c == ':') {
        colons++;
      } else if (c == '%' && !zone && !notZone) {
        // Only the first percent sign can start a zone, and only after an IPv6 address: this is checked once per segment.
        zone = colons >= 2 && isIpv6(content, segmentStart, end);
        notZone = !zone;
      }
      end++;
    }
    return end;
  }

  private static boolean isIpv6(String content, int segmentStart, int end) {
    int from = content.startsWith("[", segmentStart) ? segmentStart + 1 : segmentStart;
    return IpAddressClassifier.parse(content.substring(from, end)).isPresent();
  }

  private static boolean isEntryChar(String content, int index) {
    return isLetterOrDigit(content, index) || "._-+:%/@[]".indexOf(content.charAt(index)) >= 0;
  }

  private static boolean isUrlChar(String content, int index) {
    return isLetterOrDigit(content, index) || "._-~:%/@[]?#&=!$*+".indexOf(content.charAt(index)) >= 0;
  }

  // Judge the whole code point, so that the halves of a supplementary letter or digit are not separators.
  private static boolean isLetterOrDigit(String content, int index) {
    char c = content.charAt(index);
    int codePoint = (Character.isLowSurrogate(c) && index > 0 && Character.isHighSurrogate(content.charAt(index - 1)))
      ? content.codePointAt(index - 1)
      : content.codePointAt(index);
    return Character.isLetterOrDigit(codePoint);
  }

  private static void collectFromUrl(String url, List<String> addresses) {
    Matcher start = URL_START.matcher(url);
    if (!start.lookingAt()) {
      collectFromPath(url, addresses);
      return;
    }
    int authorityEnd = start.end();
    while (authorityEnd < url.length() && "/?#".indexOf(url.charAt(authorityEnd)) < 0) {
      authorityEnd++;
    }
    collectFromAuthority(url.substring(start.end(), authorityEnd), addresses);
    // Query values and fragments can hold nested URLs, so delimit pieces before looking for URL starts.
    for (String piece : URL_PIECE_DELIMITER.split(url.substring(authorityEnd))) {
      collectFromPiece(piece, addresses);
    }
  }

  // A piece has no query or fragment delimiter, so it holds a chain of nested authorities, then a path.
  // Iterate instead of recursing: the nesting depth is only bounded by the length of the content.
  private static void collectFromPiece(String piece, List<String> addresses) {
    Matcher start = URL_START.matcher(piece);
    int from = 0;
    while (start.region(from, piece.length()).lookingAt()) {
      int authorityEnd = piece.indexOf('/', start.end());
      if (authorityEnd < 0) {
        authorityEnd = piece.length();
      }
      collectFromAuthority(piece.substring(start.end(), authorityEnd), addresses);
      from = authorityEnd;
    }
    collectFromPath(piece.substring(from), addresses);
  }

  private static void collectFromAuthority(String authority, List<String> addresses) {
    // The host follows the last at sign outside a bracketed host, because a zone can contain an at sign.
    int hostStart = 0;
    int lastAt = -1;
    boolean bracketed = false;
    for (int i = 0; i < authority.length(); i++) {
      char c = authority.charAt(i);
      if (c == '[') {
        bracketed |= i == hostStart;
      } else if (c == ']') {
        bracketed = false;
      } else if (c == '@') {
        lastAt = i;
        if (!bracketed) {
          hostStart = i + 1;
        }
      }
    }
    if (bracketed) {
      // The bracket was never closed, so it was no host bracket.
      hostStart = lastAt + 1;
    }
    collectFromPath(authority.substring(hostStart), addresses);
  }

  private static void collectFromEntry(String entry, List<String> addresses) {
    int slash = entry.indexOf('/');
    if (slash > 0 && slash == entry.lastIndexOf('/') && CIDR_ATTEMPT.matcher(entry.substring(slash + 1)).matches()) {
      String standalone = entry.substring(0, slash);
      Optional<String> address = parseEndpoint(standalone);
      // Without a port, the endpoint is the address, possibly in brackets.
      if (address.isPresent() && (standalone.equals(address.get()) || standalone.equals("[" + address.get() + "]"))) {
        // A standalone address entry: the suffix is a CIDR prefix, which invalidates the entry when it is invalid.
        String cidr = address.get() + entry.substring(slash);
        if (IpAddressClassifier.parse(cidr).isPresent()) {
          addresses.add(cidr);
        }
        return;
      }
    }
    collectFromPath(entry, addresses);
  }

  private static void collectFromPath(String path, List<String> addresses) {
    for (String segment : path.split("/")) {
      parseEndpoint(segment).ifPresent(addresses::add);
    }
  }

  /** Parses a bare IP address, an IPv4 address with a port, or a bracketed IPv6 address with an optional port. */
  private static Optional<String> parseEndpoint(String text) {
    Optional<String> address = text.startsWith("[") ? unbracket(text) : removeIpv4Port(text);
    // The classifier ignores zones, so it cannot reject stray brackets in them.
    return address.filter(a -> a.indexOf('[') < 0 && a.indexOf(']') < 0 && IpAddressClassifier.parse(a).isPresent());
  }

  private static Optional<String> unbracket(String text) {
    int closing = text.indexOf(']');
    if (closing < 0 || (closing + 1 < text.length() && !isPortSuffix(text.substring(closing + 1)))) {
      return Optional.empty();
    }
    String address = text.substring(1, closing);
    return address.indexOf(':') < 0 ? Optional.empty() : Optional.of(address);
  }

  private static Optional<String> removeIpv4Port(String text) {
    int colon = text.indexOf(':');
    if (colon > 0 && colon == text.lastIndexOf(':') && text.lastIndexOf('.', colon) >= 0) {
      return isPort(text.substring(colon + 1)) ? Optional.of(text.substring(0, colon)) : Optional.empty();
    }
    return Optional.of(text);
  }

  private static boolean isPortSuffix(String suffix) {
    return suffix.startsWith(":") && isPort(suffix.substring(1));
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
