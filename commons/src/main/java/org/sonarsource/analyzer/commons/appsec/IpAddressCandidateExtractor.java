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
  // The boundary prevents failed scheme searches from restarting within the same scheme-like run.
  private static final Pattern SEARCH_URL_PREFIX = Pattern.compile("(?<![a-zA-Z0-9+.-])[a-zA-Z][a-zA-Z0-9+.-]*://|//");
  private static final Pattern ADDRESS_DELIMITER = Pattern.compile("[?#,\\s]", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern ASCII_DIGITS = Pattern.compile("\\d+");
  private static final int STANDALONE = 0;
  private static final int PATH = 1;
  private static final int URL_AUTHORITY = 2;
  private static final int URL_TAIL = 3;

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
   * Returns all valid IP address literals in source order, including repeated occurrences.
   * Searches text, lists, paths, URL hosts, query values, and fragments. URL schemes and
   * authority user information are excluded. This method validates syntax only; it does
   * not decide whether an address is allowed to be hardcoded.
   *
   * <p>IPv6 brackets and valid endpoint ports are removed. Spelling and zones are preserved.
   * A standalone CIDR keeps its valid prefix. Slashes in URLs, paths, or after endpoint
   * ports remain path separators. Invalid endpoints and standalone CIDRs produce no match.
   * Addresses adjacent to letters, digits, dots, hyphens, or underscores are not extracted.
   * Whitespace, commas, semicolons, quotes, and parentheses separate entries.
   *
   * @return an empty list when no valid address is found
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher urlPrefix = SEARCH_URL_PREFIX.matcher(content);
    int nextUrl = urlPrefix.find() ? urlPrefix.start() : -1;
    int context = STANDALONE;
    int urlDelimiter = 0;
    int urlParentheses = 0;
    StringBuilder enclosures = new StringBuilder();
    int index = 0;
    while (index < content.length()) {
      nextUrl = nextUrlPrefix(urlPrefix, nextUrl, index);
      int start = index;
      int character = content.codePointAt(index);
      index += Character.charCount(character);
      if (isEntryDelimiter(character)) {
        // URL punctuation separates values without changing the meaning of a later slash.
        context = context == URL_TAIL && !isUrlTerminator(character) && !closesEnclosure(character, urlDelimiter, urlParentheses) ? URL_TAIL : STANDALONE;
        urlParentheses = parenthesisDepth(context, character, urlDelimiter, urlParentheses);
        updateEnclosures(enclosures, content, start, context);
      } else if (nextUrl == start) {
        urlDelimiter = enclosures.isEmpty() ? 0 : enclosures.charAt(enclosures.length() - 1);
        urlParentheses = 0;
        index = extractUrlHost(content, skipUserInfo(content, urlPrefix.end(), urlDelimiter), urlDelimiter, addresses);
        context = URL_AUTHORITY;
      } else if (character == '/' || isValueDelimiter(character)) {
        context = afterSeparator(context, character);
      } else if (isAddressCharacter(character)) {
        index = extractAddress(content, start, context == STANDALONE, addresses);
      }
    }
    return addresses;
  }

  private static int extractAddress(String content, int start, boolean standalone, List<String> addresses) {
    int end = addressTokenEnd(content, start);
    String token = content.substring(start, end);
    Optional<StrippedCandidate> endpoint = validEndpoint(token);
    if (endpoint.isEmpty()) {
      return end;
    }
    StrippedCandidate stripped = endpoint.get();
    String candidate = stripped.candidate();
    if (standalone && !stripped.portRemoved() && end < content.length() && content.charAt(end) == '/') {
      int suffixEnd = slashSuffixEnd(content, end + 1);
      boolean path = end + 1 < suffixEnd && Character.isLetter(content.codePointAt(end + 1));
      if (!path && (suffixEnd == content.length() || content.charAt(suffixEnd) != '/')) {
        candidate += content.substring(end, suffixEnd);
        end = suffixEnd;
      }
    }
    if (IpAddressClassifier.parse(candidate).isPresent()) {
      addresses.add(candidate);
    }
    return end;
  }

  private static Optional<StrippedCandidate> validEndpoint(String token) {
    Optional<StrippedCandidate> endpoint = stripBracketsAndPort(token);
    if (endpoint.isPresent()) {
      String candidate = endpoint.get().candidate();
      if (!candidate.endsWith(".") && IpAddressClassifier.parse(candidate).isPresent()) {
        return endpoint;
      }
    }
    return Optional.empty();
  }

  private static int extractUrlHost(String content, int start, int delimiter, List<String> addresses) {
    int end = bracketedEnd(content, start);
    int parentheses = 0;
    while (end < content.length()) {
      int character = content.codePointAt(end);
      if (character == '/' || character == '?' || character == '#' || character == ',' || character == ';'
        || isUrlTerminator(character) || closesEnclosure(character, delimiter, parentheses)) {
        break;
      }
      parentheses = parenthesisDepth(URL_TAIL, character, delimiter, parentheses);
      end += Character.charCount(character);
    }
    Optional<StrippedCandidate> endpoint = validEndpoint(content.substring(start, end));
    if (endpoint.isPresent()) {
      addresses.add(endpoint.get().candidate());
    }
    return end;
  }

  private static void updateEnclosures(StringBuilder enclosures, String content, int index, int context) {
    if (context == URL_TAIL) {
      return;
    }
    int character = content.codePointAt(index);
    int previous = index == 0 ? 0 : content.codePointBefore(index);
    if (character == '\'' && isWordCharacter(previous) && index + 1 < content.length() && isWordCharacter(content.codePointAt(index + 1))) {
      return;
    }
    int last = enclosures.length() - 1;
    if (last >= 0 && character == enclosures.charAt(last)) {
      enclosures.setLength(last);
    } else if (character == '(' || ((last < 0 || enclosures.charAt(last) == ')')
      && (character == '\'' || character == '"') && !isWordCharacter(previous))) {
      enclosures.append(character == '(' ? ')' : (char) character);
    }
  }

  private static int skipUserInfo(String content, int authorityStart, int delimiter) {
    int hostStart = authorityStart;
    int parentheses = 0;
    int index = authorityStart;
    while (index < content.length()) {
      int bracket = bracketedEnd(content, index);
      if (bracket > index) {
        index = bracket;
      } else {
        int character = content.codePointAt(index);
        if (character == '/' || character == '?' || character == '#' || isUrlTerminator(character)
          || closesEnclosure(character, delimiter, parentheses)) {
          break;
        }
        if (character == '@') {
          hostStart = index + 1;
        }
        parentheses = parenthesisDepth(URL_TAIL, character, delimiter, parentheses);
        index += Character.charCount(character);
      }
    }
    return hostStart;
  }

  private static int addressTokenEnd(String content, int start) {
    int end = bracketedEnd(content, start);
    boolean zone = false;
    boolean colon = false;
    boolean dot = false;
    boolean port = false;
    while (end < content.length()) {
      int character = content.codePointAt(end);
      if (isEntryDelimiter(character) || (!zone && isValueDelimiter(character)) || character == '/'
        || (!zone && !port && !isAddressCharacter(character))) {
        break;
      }
      zone |= character == '%' && colon;
      port |= character == ':' && ((!colon && dot) || (end > start && content.charAt(end - 1) == ']'));
      colon |= character == ':';
      dot |= character == '.';
      end += Character.charCount(character);
    }
    return end;
  }

  private static int bracketedEnd(String content, int start) {
    if (start < content.length() && content.charAt(start) == '[') {
      boolean zone = false;
      for (int end = start + 1; end < content.length(); end++) {
        char character = content.charAt(end);
        if (character == ']') {
          return end + 1;
        }
        if (character == '[' || character == '/' || (!zone && (isEntryDelimiter(character) || isValueDelimiter(character)))) {
          break;
        }
        zone |= character == '%';
      }
    }
    return start;
  }

  private static int slashSuffixEnd(String content, int start) {
    int end = start;
    while (end < content.length()) {
      int character = content.codePointAt(end);
      if (character == '/' || isEntryDelimiter(character) || isValueDelimiter(character)) {
        break;
      }
      end += Character.charCount(character);
    }
    return end;
  }

  private static boolean isAddressCharacter(int character) {
    return isWordCharacter(character)
      || character == '.' || character == '-' || character == '_' || character == ':'
      || character == '%' || character == '[' || character == ']';
  }

  private static boolean isWordCharacter(int character) {
    int type = Character.getType(character);
    return Character.isLetterOrDigit(character) || type == Character.NON_SPACING_MARK
      || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
  }

  private static boolean isValueDelimiter(int character) {
    return character == '=' || character == '?' || character == '#' || character == '&';
  }

  private static boolean isUrlTerminator(int character) {
    return Character.isWhitespace(character) || Character.isSpaceChar(character)
      || character == '"';
  }

  private static boolean isEntryDelimiter(int character) {
    return isUrlTerminator(character) || character == '\'' || character == '(' || character == ')' || character == ',' || character == ';';
  }

  private static boolean closesEnclosure(int character, int delimiter, int parentheses) {
    return character == delimiter && (character == '\'' || (character == ')' && parentheses == 0));
  }

  private static int nextUrlPrefix(Matcher matcher, int nextUrl, int index) {
    if (nextUrl >= 0 && nextUrl < index) {
      return matcher.find(index) ? matcher.start() : -1;
    }
    return nextUrl;
  }

  private static int afterSeparator(int context, int character) {
    if (context == URL_AUTHORITY || context == URL_TAIL) {
      return URL_TAIL;
    }
    return character == '/' ? PATH : context;
  }

  private static int parenthesisDepth(int context, int character, int delimiter, int depth) {
    if (context == URL_TAIL && delimiter == ')') {
      if (character == '(') {
        return depth + 1;
      } else if (character == ')' && depth > 0) {
        return depth - 1;
      }
    }
    return depth;
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
