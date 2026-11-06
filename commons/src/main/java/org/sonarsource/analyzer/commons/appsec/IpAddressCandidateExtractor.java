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

  private static final Pattern ENTRY = Pattern.compile("[^\\s\"'()]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern LIST_ENTRY = Pattern.compile("[^,;]+");
  private static final Pattern URL_START = Pattern.compile("(?<![a-zA-Z0-9+.-])(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");
  // Keep entire identifiers and malformed endpoints together, rather than recovering shorter valid addresses.
  // Zones are opaque to address validation, but stop at path and value separators.
  private static final Pattern ADDRESS_TOKEN = Pattern.compile("[\\p{L}\\p{N}_.:\\[\\]+-]+(?:%[^,;/?#=&]*)?");

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
   * Repeated occurrences are retained. Returns an empty list when no address is found.
   * Unlike {@link #extract(String)}, every result is validated with {@link IpAddressClassifier#parse(String)};
   * address classification and security-rule exceptions are left to the caller.
   *
   * <p>Supports text, paths, values after an equals sign, and lists separated by whitespace, commas, or semicolons.
   * Quotes and parentheses may enclose entries. Addresses touching letters, digits, dots, hyphens, or underscores
   * are rejected, including sentence-ending dots. A complete IPv6 address, including a dotted tail or zone,
   * is returned as one result with its spelling preserved.
   *
   * <p>URL schemes and authority user information are skipped; hosts, paths, queries, and fragments are searched.
   * IPv6 endpoint brackets and valid ports on IPv4 or bracketed IPv6 endpoints are removed.
   * Invalid endpoints are rejected. Bracketed IPv4 endpoints and bracketed lists are not supported.
   * The last group of an unbracketed IPv6 address is never interpreted as a port.
   *
   * <p>A standalone address with a decimal CIDR suffix retains that suffix if its prefix length is valid.
   * Invalid prefix lengths invalidate the entry. Slashes in URLs, after ports, or in paths are separators.
   * A single slash followed by a digit or sign on a bare address is treated as a CIDR attempt;
   * named path segments, leading slashes, and multiple slashes identify paths.
   *
   * @throws NullPointerException if {@code content} is null
   */
  public static List<String> extractAll(String content) {
    List<String> addresses = new ArrayList<>();
    Matcher entries = ENTRY.matcher(content);
    while (entries.find()) {
      extractEntry(entries.group(), addresses);
    }
    return addresses;
  }

  private static void extractEntry(String entry, List<String> addresses) {
    Matcher urls = URL_START.matcher(entry);
    int start = 0;
    boolean inUrl = false;
    while (urls.find(start)) {
      extractText(entry.substring(start, urls.start()), inUrl, addresses);
      int authorityEnd = urls.end();
      while (authorityEnd < entry.length() && "/?#".indexOf(entry.charAt(authorityEnd)) < 0) {
        authorityEnd++;
      }
      String authority = entry.substring(urls.end(), authorityEnd);
      int hostStart = authority.lastIndexOf('@') + 1;
      int hostEnd = hostStart;
      while (hostEnd < authority.length() && ",;".indexOf(authority.charAt(hostEnd)) < 0) {
        hostEnd++;
      }
      String host = authority.substring(hostStart, hostEnd);
      stripBracketsAndPort(host).map(StrippedCandidate::candidate).ifPresent(candidate -> addValid(candidate, addresses));
      start = urls.end() + hostEnd;
      inUrl = true;
    }
    extractText(entry.substring(start), inUrl, addresses);
  }

  private static void extractText(String text, boolean inUrl, List<String> addresses) {
    Matcher entries = LIST_ENTRY.matcher(text);
    boolean inUrlValues = false;
    while (entries.find()) {
      String entry = entries.group();
      inUrlValues |= entry.indexOf('?') >= 0 || entry.indexOf('#') >= 0;
      extractListEntry(entry, inUrl && (entries.start() == 0 || inUrlValues || entry.indexOf('=') >= 0), addresses);
    }
  }

  private static void extractListEntry(String text, boolean inUrl, List<String> addresses) {
    Matcher tokens = ADDRESS_TOKEN.matcher(text);
    while (tokens.find()) {
      Optional<StrippedCandidate> stripped = stripBracketsAndPort(tokens.group());
      if (stripped.isEmpty()) {
        continue;
      }
      StrippedCandidate endpoint = stripped.get();
      String candidate = endpoint.candidate();
      if (!inUrl && !endpoint.portRemoved() && isStandalonePrefix(text, tokens.end())) {
        candidate += text.substring(tokens.end());
        // Consume even an invalid CIDR suffix so it cannot yield a shorter address.
        addValid(candidate, addresses);
        return;
      }
      addValid(candidate, addresses);
    }
  }

  private static boolean isStandalonePrefix(String text, int end) {
    if (end + 1 >= text.length() || text.charAt(end) != '/' || text.indexOf('/') != end || text.lastIndexOf('/') != end) {
      return false;
    }
    String suffix = text.substring(end + 1);
    // An address in the next segment identifies a path, not a prefix length.
    if (stripBracketsAndPort(suffix).filter(endpoint -> IpAddressClassifier.parse(endpoint.candidate()).isPresent()).isPresent()) {
      return false;
    }
    char first = suffix.charAt(0);
    return Character.isDigit(first) || first == '+' || first == '-';
  }

  private static void addValid(String candidate, List<String> addresses) {
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
