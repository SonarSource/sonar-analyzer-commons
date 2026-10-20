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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts an address candidate from the beginning of a string literal's content. */
public final class IpAddressExtractor {

  private static final Pattern URL_PREFIX = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:)?//");
  private static final Pattern ADDRESS_TOKEN = Pattern.compile("[\\w.:\\[\\]%+/-]+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern CIDR_SUFFIX = Pattern.compile("/[0-9]+");

  private IpAddressExtractor() {
  }

  /**
   * Extracts an IP address candidate from a bare address, endpoint, or URL with a {@code scheme://} or {@code //} prefix.
   *
   * Removes a single enclosing pair of brackets and ASCII decimal ports from 0 through 65535.
   * Brackets delimit IPv6 hosts in URLs and endpoints so address colons are distinct from the port separator,
   * for example {@code [2003::1]:8080}.
   *
   * Preserves IPv6 zones and numeric CIDR suffixes on bare addresses; URL slash suffixes are always paths.
   * Nonnumeric slash suffixes on bare addresses are also treated as paths.
   * Extraction stops at a query, fragment, comma, or whitespace; leading whitespace is not accepted.
   *
   * <p>The candidate is not validated as a numeric IP address. Pass it to {@link IpAddressClassifier#parse(String)}:
   * <pre>{@code
   * IpAddressExtractor.extract(content).flatMap(IpAddressClassifier::parse)
   * }</pre>
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

    // Keep malformed candidates intact: 1.2.3.4-foo must not become 1.2.3.4.
    Matcher token = ADDRESS_TOKEN.matcher(host);
    if (!token.lookingAt()) {
      return Optional.empty();
    }
    String address = token.group();

    // Preserve bare CIDRs (1.2.3.4/24); strip paths (1.2.3.4/path).
    int slash = address.indexOf('/');
    String cidr = "";
    if (slash >= 0) {
      String suffix = address.substring(slash);
      if (CIDR_SUFFIX.matcher(suffix).matches()) {
        cidr = suffix;
      }
      address = address.substring(0, slash);
    }

    // Unwrap one bracket pair and validate the port: [2003::1]:8080 becomes 2003::1.
    if (address.startsWith("[")) {
      int closing = address.indexOf(']');
      if (closing < 0) {
        return Optional.empty();
      }
      String suffix = address.substring(closing + 1);
      if (!suffix.isEmpty() && !isPort(suffix)) {
        return Optional.empty();
      }
      address = address.substring(1, closing);
    }
    if (address.indexOf('[') >= 0 || address.indexOf(']') >= 0) {
      return Optional.empty();
    }

    // Strip IPv4 ports: 1.2.3.4:8080 becomes 1.2.3.4.
    // IPv6 colons precede any dots, including mixed notation such as 2003::1.2.3.4.
    int colon = address.indexOf(':');
    if (colon > 0 && address.substring(0, colon).indexOf('.') >= 0) {
      if (!isPort(address.substring(colon))) {
        return Optional.empty();
      }
      address = address.substring(0, colon);
    }
    return Optional.of(address + cidr);
  }

  private static boolean isPort(String suffix) {
    if (suffix.length() <= 1 || suffix.charAt(0) != ':'
      || !suffix.substring(1).chars().allMatch(character -> character >= '0' && character <= '9')) {
      return false;
    }
    try {
      return Integer.parseInt(suffix.substring(1)) <= 65535;
    } catch (NumberFormatException e) {
      return false;
    }
  }

}
