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

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Classifies an IP-address literal (plain or CIDR, IPv4 or IPv6) as reserved for private/special-purpose use, non-reserved, or unparseable.
 *
 * <p>CIDR (Classless Inter-Domain Routing) semantics: a literal is reserved only if its <em>entire</em> range fits inside one reserved block.
 * For example, {@code 10.0.0.0/1} is NOT reserved.
 *
 * <p>Reserved ranges follow the IANA IPv4/IPv6 Special-Purpose Address registries' "not globally reachable" entries.
 *
 * <p>IPv4-mapped and IPv4-translated IPv6 addresses are classfied according to the content of the IPv4 address they carry.
 * CIDRs keep their native IPv6 prefix length: address classification applies only to ranges entirely inside either family.
 * For example, {@code ::ffff:127.0.0.1/104} has the same loopback classification as {@code 127.0.0.0/8}.
 * {@link #isUnrestrictedCidr(String)} always requires the entire native address space:
 * {@code ::ffff:0:0/96} is not unrestricted, while {@code ::/0} is.
 *
 * <h2>Usage examples</h2>
 *
 * <p>S6329 ("public network access") — flag any firewall-rule literal not provably reserved (unparseable literals stay flagged on the safe side):
 * <pre>{@code
 * if (!IpAddressClassifier.isReserved(literal)) {
 *   ctx.reportIssue(node, "Public IP allowed in firewall rule.");
 * }
 * }</pre>
 *
 * <p>S6321 ("admin services exposed to the Internet") — flag CIDR rules that cover the entire address space:
 * <pre>{@code
 * if (IpAddressClassifier.isUnrestrictedCidr(literal)) {
 *   ctx.reportIssue(node, "Restrict source addresses.");
 * }
 * }</pre>
 *
 * <p>S1313 ("hardcoded IP") — exempt only documentation, loopback, any-address, and broadcast sentinels; still flag private RFC 1918 leakage:
 * <pre>{@code
 * boolean exempt = IpAddressClassifier.isLoopback(literal)
 *               || IpAddressClassifier.isAnyAddress(literal)
 *               || IpAddressClassifier.isBroadcast(literal)
 *               || IpAddressClassifier.isDocumentation(literal);
 * if (!exempt) {
 *   ctx.reportIssue(node, "Avoid hardcoded IP addresses.");
 * }
 * }</pre>
 */
public final class IpAddressClassifier {

  private static final BigInteger IPV4_MASK = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
  private static final BigInteger MAPPED_PREFIX = BigInteger.valueOf(0xffff);
  private static final BigInteger TRANSLATED_PREFIX = BigInteger.valueOf(0xffff0000L);
  // Leading-zero decimal octets are ambiguous because some consumers interpret them as octal.
  private static final Pattern IPV4_LIKE = Pattern.compile("(?:0|[1-9]\\d{0,2})(?:\\.(?:0|[1-9]\\d{0,2})){3}");
  private static final Pattern IPV6_LIKE = Pattern.compile("[0-9a-fA-F:][0-9a-fA-F:.]*");

  // IPv4 special-purpose blocks per IANA, restricted to entries that are NOT
  // globally reachable.
  @SuppressWarnings("java:S1313")
  private static final List<Block4> RESERVED_IPV4_BLOCKS = List.of(
    block4("0.0.0.0/8", BlockKind.THIS_NETWORK),
    block4("10.0.0.0/8", BlockKind.RFC1918_PRIVATE),
    block4("172.16.0.0/12", BlockKind.RFC1918_PRIVATE),
    block4("192.168.0.0/16", BlockKind.RFC1918_PRIVATE),
    block4("100.64.0.0/10", BlockKind.CGNAT),
    block4("127.0.0.0/8", BlockKind.LOOPBACK),
    block4("169.254.0.0/16", BlockKind.LINK_LOCAL),
    block4("192.0.0.0/24", BlockKind.IETF_PROTOCOL),
    block4("192.0.2.0/24", BlockKind.DOCUMENTATION),
    block4("198.51.100.0/24", BlockKind.DOCUMENTATION),
    block4("203.0.113.0/24", BlockKind.DOCUMENTATION),
    block4("198.18.0.0/15", BlockKind.BENCHMARKING),
    block4("240.0.0.0/4", BlockKind.RESERVED_FUTURE));

  private static final List<Block6> RESERVED_IPV6_BLOCKS = List.of(
    block6("::1/128", BlockKind.LOOPBACK),
    block6("2001:db8::/32", BlockKind.DOCUMENTATION),
    block6("3fff::/20", BlockKind.DOCUMENTATION),
    block6("fc00::/7", BlockKind.UNIQUE_LOCAL),
    block6("fe80::/10", BlockKind.LINK_LOCAL));

  private IpAddressClassifier() {
  }

  /**
   * Parses a numeric IPv4 or IPv6 address, optionally followed by a CIDR prefix, without DNS or interface lookup.
   * Returns empty for malformed input, host names, or brackets.
   * Numeric spellings follow the JDK parser, including extra leading zeros in hexadecimal groups.
   * Multi-digit decimal octets starting with zero are rejected in IPv4 and dotted IPv6 tails to avoid octal ambiguity.
   * The returned value can be queried repeatedly without parsing again.
   *
   * @throws NullPointerException if {@code literal} is null
   */
  public static Optional<ParsedIp> parse(String literal) {
    boolean ipv6 = literal.indexOf(':') >= 0;
    int maxBits = ipv6 ? 128 : 32;
    int slash = literal.indexOf('/');
    String address = slash < 0 ? literal : literal.substring(0, slash);
    int prefix = slash < 0 ? maxBits : parsePrefix(literal.substring(slash + 1), maxBits);
    if (prefix < 0) {
      return Optional.empty();
    }
    AddressRange range = ipv6 ? parseIpv6Range(address, prefix) : parseIpv4Range(address, prefix);
    return Optional.ofNullable(range).map(parsed -> new ParsedIp(parsed, prefix));
  }

  /**
   * True if the literal parses and its entire range fits inside a reserved special-purpose block; false otherwise (including unparseable).
   * Examples: {@code "10.0.0.1"} → true; {@code "10.0.0.0/1"} → false (straddles a reserved boundary).
   */
  public static boolean isReserved(String literal) {
    return parse(literal).map(ParsedIp::isReserved).orElse(false);
  }

  /**
   * True if the literal fits entirely within IPv4 {@code 127.0.0.0/8} or IPv6 {@code ::1/128}.
   * Examples: {@code "127.0.0.1"} → true; {@code "::1"} → true; {@code "10.0.0.1"} → false.
   */
  public static boolean isLoopback(String literal) {
    return parse(literal).map(ParsedIp::isLoopback).orElse(false);
  }

  /**
   * True if the literal fits entirely within IPv4 {@code 169.254.0.0/16} (RFC 3927) or IPv6 {@code fe80::/10} (RFC 4291).
   * Examples: {@code "169.254.169.254"} → true; {@code "fe80::1"} → true; {@code "10.0.0.1"} → false.
   */
  public static boolean isLinkLocal(String literal) {
    return parse(literal).map(ParsedIp::isLinkLocal).orElse(false);
  }

  /**
   * True if the literal fits entirely within one of:
   * <ul>
   *   <li>IPv4 RFC 1918 — {@code 10.0.0.0/8}, {@code 172.16.0.0/12}, {@code 192.168.0.0/16}</li>
   *   <li>IPv6 Unique Local Addresses — {@code fc00::/7} (RFC 4193)</li>
   * </ul>
   * Does not cover CGNAT, link-local, loopback, or documentation; query those via their own predicates.
   */
  public static boolean isPrivate(String literal) {
    return parse(literal).map(ParsedIp::isPrivate).orElse(false);
  }

  /**
   * True if the literal fits entirely within one of:
   * <ul>
   *   <li>IPv4 TEST-NET-1/2/3 — {@code 192.0.2.0/24}, {@code 198.51.100.0/24}, {@code 203.0.113.0/24} (RFC 5737)</li>
   *   <li>IPv6 {@code 2001:db8::/32} (RFC 3849)</li>
   *   <li>IPv6 {@code 3fff::/20} (RFC 9637)</li>
   * </ul>
   * Examples: {@code "192.0.2.10"} → true; {@code "3fff::1"} → true; {@code "10.0.0.1"} → false.
   */
  public static boolean isDocumentation(String literal) {
    return parse(literal).map(ParsedIp::isDocumentation).orElse(false);
  }

  /**
   * True if the literal is the "any address" singleton: IPv4 {@code 0.0.0.0} (or {@code /32}) or IPv6 {@code ::} (or {@code /128}).
   * This is the sentinel host-binding APIs use to mean "listen on every interface".
   * Returns false for the {@code /0} CIDR {@code 0.0.0.0/0} — use {@link #isUnrestrictedCidr(String)} for that.
   */
  public static boolean isAnyAddress(String literal) {
    return parse(literal).map(ParsedIp::isAnyAddress).orElse(false);
  }

  /**
   * True if the literal is the IPv4 limited-broadcast address {@code 255.255.255.255} (or {@code /32}).
   * IPv6 literals return false except mapped and translated addresses, which use the IPv4 payload classification.
   */
  public static boolean isBroadcast(String literal) {
    return parse(literal).map(ParsedIp::isBroadcast).orElse(false);
  }

  /**
   * True if the literal is a {@code /0}-mask CIDR covering the entire IPv4 or IPv6 address space (e.g. {@code 0.0.0.0/0}, {@code ::/0}).
   * Any non-zero address combined with {@code /0} also matches ({@code 10.0.0.0/0} normalizes to the same range).
   * Plain singletons like {@code 0.0.0.0} return false — use {@link #isAnyAddress(String)} for the bind-anywhere sentinel.
   */
  public static boolean isUnrestrictedCidr(String literal) {
    return parse(literal).map(ParsedIp::isUnrestrictedCidr).orElse(false);
  }

  /**
   * Parses a plain IPv4 address (no CIDR suffix) into a 32-bit value held in a long.
   * Returns empty for CIDR literals, malformed input, IPv6 literals, or null.
   * Example: {@code "10.0.0.1"} → {@code OptionalLong.of(0x0A000001L)}; {@code "10.0.0.0/8"} → empty.
   */
  public static OptionalLong parseIpv4SingleAddress(@Nullable String literal) {
    if (literal == null || literal.indexOf('/') >= 0) {
      return OptionalLong.empty();
    }
    long addr = parseIpv4Address(literal);
    return addr < 0 ? OptionalLong.empty() : OptionalLong.of(addr);
  }

  /**
   * True if the inclusive IPv4 range {@code [start, end]} is fully contained in one of the reserved blocks.
   * Inputs are 32-bit IPv4 addresses held in a long (see {@link #parseIpv4SingleAddress}).
   * Throws {@link IllegalArgumentException} when {@code start > end}; callers must pre-check the ordering or catch.
   */
  public static boolean isAddressRangeReserved(long startInclusive, long endInclusive) {
    if (startInclusive > endInclusive) {
      throw new IllegalArgumentException("Inverted IPv4 range: start=" + startInclusive + " > end=" + endInclusive);
    }
    Range4 range = new Range4(startInclusive, endInclusive);
    return RESERVED_IPV4_BLOCKS.stream().anyMatch(b -> b.range.contains(range));
  }

  /**
   * An immutable parsed address or CIDR range. Predicates classify the entire range.
   * Mapped and translated IPv4 ranges contained within their IPv6 family use IPv4 classification,
   * while {@link #isUnrestrictedCidr()} retains the native address-family meaning.
   * For example, {@code ::ffff:0:0/96} covers all mapped IPv4 addresses but is not unrestricted.
   * A native {@code /0}, such as {@code ::/0} or {@code 0.0.0.0/0}, is unrestricted.
   */
  public static final class ParsedIp {
    private final int prefixLength;
    private final AddressRange classificationRange;

    private ParsedIp(AddressRange range, int prefixLength) {
      this.prefixLength = prefixLength;
      if (range instanceof Range6 ipv6) {
        Range4 payload = embeddedIpv4Range(ipv6);
        classificationRange = payload == null ? range : payload;
      } else {
        classificationRange = range;
      }
    }

    /** True if the entire range fits inside one reserved block. */
    public boolean isReserved() {
      return switch (classificationRange) {
        case Range4 range -> RESERVED_IPV4_BLOCKS.stream().anyMatch(b -> b.range.contains(range));
        case Range6 range -> RESERVED_IPV6_BLOCKS.stream().anyMatch(b -> b.range.contains(range));
      };
    }

    /** @see IpAddressClassifier#isLoopback(String) */
    public boolean isLoopback() {
      return matchesKind(BlockKind.LOOPBACK);
    }

    /** @see IpAddressClassifier#isLinkLocal(String) */
    public boolean isLinkLocal() {
      return matchesKind(BlockKind.LINK_LOCAL);
    }

    /** @see IpAddressClassifier#isPrivate(String) */
    public boolean isPrivate() {
      return matchesKind(BlockKind.RFC1918_PRIVATE) || matchesKind(BlockKind.UNIQUE_LOCAL);
    }

    /** @see IpAddressClassifier#isDocumentation(String) */
    public boolean isDocumentation() {
      return matchesKind(BlockKind.DOCUMENTATION);
    }

    /** @see IpAddressClassifier#isAnyAddress(String) */
    public boolean isAnyAddress() {
      return switch (classificationRange) {
        case Range4 range -> range.startInclusive == 0L && range.endInclusive == 0L;
        case Range6 range -> range.startInclusive.signum() == 0 && range.endInclusive.signum() == 0;
      };
    }

    /** @see IpAddressClassifier#isBroadcast(String) */
    public boolean isBroadcast() {
      return classificationRange instanceof Range4 range && range.startInclusive == 0xFFFFFFFFL && range.endInclusive == 0xFFFFFFFFL;
    }

    /** @see IpAddressClassifier#isUnrestrictedCidr(String) */
    public boolean isUnrestrictedCidr() {
      return prefixLength == 0;
    }

    // Extract the IPv4 payload range only when the entire range shares the same mapped or translated 96-bit prefix.
    @Nullable
    private static Range4 embeddedIpv4Range(Range6 range) {
      BigInteger prefix = range.startInclusive.shiftRight(32);
      if (!prefix.equals(range.endInclusive.shiftRight(32))
        || (!prefix.equals(MAPPED_PREFIX)
          // Old IPv4-translated IPv6 addresses (RFC 2765, sections 1 and 2.1) are meant to be routable, with a network
          // gateway using SIIT to translate them to IPv4 when the receiver is on an IPv4-only network.
          // Translated addresses corresponding to IPv4 addresses in special ranges (loopback, link-local, etc.) are
          // not really the intended use, so treating them like mapped addresses and classifying them in the same way
          // is not a "standard-endorsed" semantic. We do it anyway, for simplicity.
          && !prefix.equals(TRANSLATED_PREFIX))) {
        return null;
      }
      return new Range4(range.startInclusive.and(IPV4_MASK).longValue(), range.endInclusive.and(IPV4_MASK).longValue());
    }

    private boolean matchesKind(BlockKind kind) {
      return switch (classificationRange) {
        case Range4 range -> RESERVED_IPV4_BLOCKS.stream().filter(b -> b.kind == kind).anyMatch(b -> b.range.contains(range));
        case Range6 range -> RESERVED_IPV6_BLOCKS.stream().filter(b -> b.kind == kind).anyMatch(b -> b.range.contains(range));
      };
    }
  }

  // ---------- IPv4 parsing ----------

  private static Range4 parseIpv4Range(String addressPart, int prefix) {
    long address = parseIpv4Address(addressPart);
    if (address < 0) {
      return null;
    }
    long hostBits = 32L - prefix;
    long mask = (0xFFFFFFFFL << hostBits) & 0xFFFFFFFFL;
    long start = address & mask;
    long end = start | (0xFFFFFFFFL >>> prefix);
    return new Range4(start, end);
  }

  private static long parseIpv4Address(String dotted) {
    BigInteger address = parseNumericAddress(dotted, false);
    return address == null ? -1L : address.longValue();
  }

  // ---------- IPv6 parsing ----------

  private static Range6 parseIpv6Range(String addressPart, int prefix) {
    BigInteger address = parseNumericAddress(addressPart, true);
    if (address == null) {
      return null;
    }
    BigInteger hostBits = BigInteger.ONE.shiftLeft(128 - prefix).subtract(BigInteger.ONE);
    BigInteger start = address.andNot(hostBits);
    BigInteger end = start.or(hostBits);
    return new Range6(start, end);
  }

  @Nullable
  private static BigInteger parseNumericAddress(String literal, boolean ipv6) {
    if (ipv6 ? (literal.indexOf(':') < 0 || !IPV6_LIKE.matcher(literal).matches()) : !IPV4_LIKE.matcher(literal).matches()) {
      return null;
    }
    if (ipv6 && literal.indexOf('.') >= 0
      && !IPV4_LIKE.matcher(literal.substring(literal.lastIndexOf(':') + 1)).matches()) {
      return null;
    }
    // Delegate numeric parsing to InetAddress.getByName while preventing DNS and interface lookup.
    // Every InetAddress.getByName input contains a colon and starts with an ASCII hex digit or ':',
    // keeping malformed literals on the numeric parsing path without DNS fallback.
    String numeric = ipv6 ? literal : "::ffff:" + literal;
    try {
      byte[] bytes = InetAddress.getByName(numeric).getAddress();
      BigInteger address = new BigInteger(1, bytes);
      // The JDK returns mapped IPv6 addresses as four bytes. Restore their 128-bit value before applying a CIDR prefix.
      return (ipv6 && bytes.length == 4) ? MAPPED_PREFIX.shiftLeft(32).or(address) : address;
    } catch (UnknownHostException e) {
      return null;
    }
  }

  // ---------- common ----------

  private static int parsePrefix(String text, int maxBits) {
    // parseInt also accepts signs and non-ASCII digits.
    // CIDR prefixes should contain ASCII digits only.
    if (!text.matches("\\d+")) {
      return -1;
    }
    int prefix;
    try {
      prefix = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return -1;
    }
    return (prefix < 0 || prefix > maxBits) ? -1 : prefix;
  }

  private static Block4 block4(String cidr, BlockKind kind) {
    int slash = cidr.indexOf('/');
    Range4 range = parseIpv4Range(cidr.substring(0, slash), parsePrefix(cidr.substring(slash + 1), 32));
    if (range == null) {
      throw new IllegalStateException("Invalid hardcoded IPv4 CIDR: " + cidr);
    }
    return new Block4(kind, range);
  }

  private static Block6 block6(String cidr, BlockKind kind) {
    int slash = cidr.indexOf('/');
    Range6 range = parseIpv6Range(cidr.substring(0, slash), parsePrefix(cidr.substring(slash + 1), 128));
    if (range == null) {
      throw new IllegalStateException("Invalid hardcoded IPv6 CIDR: " + cidr);
    }
    return new Block6(kind, range);
  }

  private enum BlockKind {
    THIS_NETWORK,
    RFC1918_PRIVATE,
    CGNAT,
    LOOPBACK,
    LINK_LOCAL,
    IETF_PROTOCOL,
    DOCUMENTATION,
    BENCHMARKING,
    RESERVED_FUTURE,
    UNIQUE_LOCAL
  }

  private static final class Block4 {
    final BlockKind kind;
    final Range4 range;

    Block4(BlockKind kind, Range4 range) {
      this.kind = kind;
      this.range = range;
    }
  }

  private static final class Block6 {
    final BlockKind kind;
    final Range6 range;

    Block6(BlockKind kind, Range6 range) {
      this.kind = kind;
      this.range = range;
    }
  }

  private sealed interface AddressRange permits Range4, Range6 {
  }

  private static final class Range4 implements AddressRange {
    final long startInclusive;
    final long endInclusive;

    Range4(long startInclusive, long endInclusive) {
      this.startInclusive = startInclusive;
      this.endInclusive = endInclusive;
    }

    boolean contains(Range4 other) {
      return startInclusive <= other.startInclusive && other.endInclusive <= endInclusive;
    }
  }

  private static final class Range6 implements AddressRange {
    final BigInteger startInclusive;
    final BigInteger endInclusive;

    Range6(BigInteger startInclusive, BigInteger endInclusive) {
      this.startInclusive = startInclusive;
      this.endInclusive = endInclusive;
    }

    boolean contains(Range6 other) {
      return startInclusive.compareTo(other.startInclusive) <= 0
        && other.endInclusive.compareTo(endInclusive) <= 0;
    }
  }
}
