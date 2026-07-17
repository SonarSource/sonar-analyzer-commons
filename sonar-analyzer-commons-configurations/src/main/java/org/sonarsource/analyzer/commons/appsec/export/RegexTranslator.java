/*
 * SonarSource Analyzers Commons Configurations
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
package org.sonarsource.analyzer.commons.appsec.export;

import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

public class RegexTranslator {

  private RegexTranslator() {
    // utility class
  }

  /**
   * Rewrites the JVM classifier's possessive quantifiers to plain greedy quantifiers so a single regex is valid in
   * every engine the export targets — .NET, JavaScript and Swift: {@code X*+ -> X*}, {@code X++ -> X+},
   * {@code X?+ -> X?}, {@code X{n,m}+ -> X{n,m}}. .NET and Swift also accept atomic groups {@code (?>X+)} and the
   * possessive quantifiers themselves, but JavaScript supports neither, so the portable form drops atomicity entirely.
   *
   * <p>Dropping atomicity is match-preserving for the current {@link SecretClassifier} patterns because every
   * possessive quantifier there is followed either by a delimiter the preceding negated class excludes (e.g. the
   * {@code \}} after {@code [^}]++}) or by the {@code $} anchor; in both cases greedy backtracking has nothing to
   * give back and produces the identical match. Named groups {@code (?<name>…)} and backrefs {@code \k<name>} are
   * portable and pass through unchanged. Greedy and lazy quantifiers are preserved.
   *
   * @throws IllegalArgumentException if the regex uses a construct this translator cannot faithfully render for all
   *   target engines: a Java-only construct (see {@link #rejectUnsupportedConstructs(String)}), or a possessive
   *   quantifier whose greedy rewrite would be ReDoS-prone (see {@link #rejectNestedUnboundedQuantifier}).
   */
  static String toPortableRegex(String regex) {
    rejectUnsupportedConstructs(regex);
    StringBuilder out = new StringBuilder();
    int i = 0;
    int n = regex.length();
    while (i < n) {
      char c = regex.charAt(i);
      // Anchors and alternation are not quantifiable atoms: copy them verbatim.
      if (c == '^' || c == '$' || c == '|') {
        out.append(c);
        i++;
        continue;
      }
      String atom;
      if (c == '\\') {
        int end = readEscape(regex, i);
        atom = regex.substring(i, end);
        i = end;
      } else if (c == '[') {
        int end = readClassEnd(regex, i);
        atom = regex.substring(i, end);
        i = end;
      } else if (c == '(') {
        atom = translateGroup(regex, i);
        i = matchingParen(regex, i) + 1;
      } else {
        atom = String.valueOf(c);
        i++;
      }
      RegexTranslator.Quantifier q = readQuantifier(regex, i);
      if (q == null) {
        out.append(atom);
      } else {
        i = q.next;
        rejectNestedUnboundedQuantifier(atom, q.base, regex);
        out.append(atom).append(q.base);
        if (q.lazy) {
          out.append('?');
        }
      }
    }
    return out.toString();
  }

  /**
   * Fails fast on Java regex constructs that {@link #toPortableRegex(String)} cannot faithfully render for the
   * .NET dialects consumed by non-JVM analyzers. No current {@link SecretClassifier} pattern uses these, so
   * this is a guard against a future pattern being exported as a silently wrong regex rather than a live bug:
   * <ul>
   *   <li>{@code \Q...\E} literal quoting — unsupported by .NET, JS and RE2;</li>
   *   <li>{@code \x{...}} variable-length hex escapes — .NET/JS spell these differently with unicode escapes, while
   *       the two-digit {@code \xHH} form stays portable and is left untouched;</li>
   *   <li>{@code \0nn} octal escapes — not portably supported;</li>
   *   <li>{@code \R} linebreak, {@code \h}/{@code \H}/{@code \v}/{@code \V} horizontal/vertical whitespace classes,
   *       {@code \X} grapheme cluster and {@code \G} end-of-previous-match anchor — Java-only; {@code \v} additionally
   *       clashes with the single vertical-tab it denotes in .NET/JS;</li>
   *   <li>{@code \b{g}} grapheme boundary — Java-only, while the plain {@code \b} word boundary stays portable.</li>
   * </ul>
   * The scan is a deliberately simple pass over escape tokens, independent of the translation parser so the two do
   * not share bugs. Every backslash escapes exactly the next character, so {@code \\Q} (an escaped backslash then a
   * literal {@code Q}) is correctly left alone.
   */
  private static void rejectUnsupportedConstructs(String regex) {
    int n = regex.length();
    int i = 0;
    while (i < n) {
      if (regex.charAt(i) != '\\' || i + 1 >= n) {
        i++;
        continue;
      }
      char d = regex.charAt(i + 1);
      switch (d) {
        case 'Q', 'E':
          throw unsupportedConstruct("\\Q...\\E literal quoting", regex);
        case '0':
          throw unsupportedConstruct("\\0nn octal escape", regex);
        case 'R':
          throw unsupportedConstruct("\\R linebreak matcher", regex);
        case 'h', 'H':
          throw unsupportedConstruct("\\h / \\H horizontal-whitespace class", regex);
        case 'v', 'V':
          throw unsupportedConstruct("\\v / \\V vertical-whitespace class", regex);
        case 'X':
          throw unsupportedConstruct("\\X grapheme cluster", regex);
        case 'G':
          throw unsupportedConstruct("\\G end-of-previous-match anchor", regex);
        default:
          break;
      }
      if (d == 'x' && i + 2 < n && regex.charAt(i + 2) == '{') {
        throw unsupportedConstruct("\\x{...} variable-length hex escape", regex);
      }
      // Plain \b is a portable word boundary; only the Java \b{g} grapheme boundary is rejected.
      if (d == 'b' && i + 2 < n && regex.charAt(i + 2) == '{') {
        throw unsupportedConstruct("\\b{g} grapheme boundary", regex);
      }
      i += 2;
    }
  }

  private static IllegalArgumentException unsupportedConstruct(String construct, String regex) {
    return new IllegalArgumentException(
      "Cannot export a .NET-portable regex: unsupported construct " + construct + " in pattern: " + regex);
  }

  /** Returns the index just past the escape token starting at {@code i} (which points at a backslash). */
  private static int readEscape(String re, int i) {
    if (i + 1 >= re.length()) {
      return i + 1;
    }
    char d = re.charAt(i + 1);
    if (d == 'k' && i + 2 < re.length() && re.charAt(i + 2) == '<') {
      return re.indexOf('>', i + 2) + 1;
    }
    if ((d == 'p' || d == 'P') && i + 2 < re.length() && re.charAt(i + 2) == '{') {
      return re.indexOf('}', i + 2) + 1;
    }
    return i + 2;
  }

  /** Reconstructs the group at {@code i} with its body translated, so nested possessive quantifiers are rewritten. */
  private static String translateGroup(String re, int i) {
    int bodyStart = groupBodyStart(re, i);
    int close = matchingParen(re, i);
    String prefix = re.substring(i, bodyStart);
    String body = re.substring(bodyStart, close);
    return prefix + toPortableRegex(body) + ")";
  }

  /** Returns the index where the body of the group opening at {@code i} begins, skipping any {@code (?…)} prefix. */
  private static int groupBodyStart(String re, int i) {
    if (re.startsWith("(?", i)) {
      if (re.startsWith("(?<=", i) || re.startsWith("(?<!", i)) {
        return i + 4;
      }
      if (re.startsWith("(?<", i)) {
        // Named group (?<name>… ; body starts after the closing '>'.
        return re.indexOf('>', i + 2) + 1;
      }
      // (?:  (?=  (?!  (?>
      return i + 3;
    }
    return i + 1;
  }
  /** Returns the index of the {@code )} matching the {@code (} at {@code open}, accounting for classes and escapes. */
  private static int matchingParen(String re, int open) {
    int depth = 0;
    int i = open;
    int n = re.length();
    while (i < n) {
      char c = re.charAt(i);
      if (c == '\\') {
        i += 2;
      } else if (c == '[') {
        i = readClassEnd(re, i);
      } else if (c == '(') {
        depth++;
        i++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
        i++;
      } else {
        i++;
      }
    }
    throw new IllegalStateException("Unbalanced parentheses in regex: " + re);
  }

  /** Returns the index just past the closing {@code ]} of the character class starting at {@code i}. */
  private static int readClassEnd(String re, int i) {
    int n = re.length();
    int j = i + 1;
    if (j < n && re.charAt(j) == '^') {
      j++;
    }
    // A ']' immediately after '[' or '[^' is a literal, not the terminator.
    if (j < n && re.charAt(j) == ']') {
      j++;
    }
    while (j < n) {
      char c = re.charAt(j);
      if (c == '\\') {
        j += 2;
      } else if (c == ']') {
        return j + 1;
      } else {
        j++;
      }
    }
    return n;
  }
  
  /** Reads an optional quantifier at {@code i}. Returns {@code null} if there is none. */
  private static RegexTranslator.Quantifier readQuantifier(String re, int i) {
    int n = re.length();
    if (i >= n) {
      return null;
    }
    char c = re.charAt(i);
    String base;
    int after;
    if (c == '*' || c == '+' || c == '?') {
      base = String.valueOf(c);
      after = i + 1;
    } else if (c == '{') {
      int close = re.indexOf('}', i);
      if (close < 0) {
        return null;
      }
      String content = re.substring(i + 1, close);
      // Only digit/comma content is a quantifier; anything else means '{' is a literal.
      if (!content.matches("\\d+(,\\d*)?")) {
        return null;
      }
      base = re.substring(i, close + 1);
      after = close + 1;
    } else {
      return null;
    }
    boolean possessive = false;
    boolean lazy = false;
    if (after < n && re.charAt(after) == '+') {
      possessive = true;
      after++;
    } else if (after < n && re.charAt(after) == '?') {
      lazy = true;
      after++;
    }
    return new RegexTranslator.Quantifier(base, possessive, lazy, after);
  }

  /**
   * Rejects the one possessive&rarr;greedy rewrite that could turn a safe pattern into a catastrophic-backtracking
   * one: an unbounded quantifier applied to a group whose body itself begins with an unbounded quantifier, e.g.
   * {@code (?:X+)++} or {@code (a+)*+}. Once the possessive marker is dropped these become the classic nested
   * unbounded shape {@code (?:X+)+}, which a JavaScript/RE2 consumer has no atomic groups to guard against.
   *
   * <p>The test is deliberately conservative: it only inspects whether the group body <em>starts</em> with an
   * unbounded quantifier (see {@link #groupBodyStartsWithUnboundedQuantifier}). That is the shape every current
   * {@link SecretClassifier} pattern relies on to keep an unbounded class safe — a required delimiter at the front,
   * as in {@code (?:/[^/]++){3,}+}, is not flagged because the leading {@code /} is a bounded literal. Two
   * imprecisions follow from that narrow test, both acceptable for a build-time guard that must never let a
   * ReDoS-prone regex through:
   * <ul>
   *   <li><b>It may over-reject.</b> A pattern that anchors its unbounded class with a <em>trailing</em> delimiter,
   *       e.g. {@code (?:[^/]++/){2,}+}, is in fact safe (every iteration must consume the {@code /}) yet is still
   *       rejected. Proving such a pattern safe means proving the leading class <em>excludes</em> the trailing
   *       delimiter, and the look-alike {@code (?:.++/){2,}+} is the genuinely dangerous {@code (.&#42;/)+}. Rather than
   *       build that class-exclusion analysis we err toward rejection. No shipped pattern hits this; a future one
   *       that does fails the export loudly and can be revisited here.</li>
   *   <li><b>It may under-detect.</b> Ambiguity that is not visible in the first atom, such as an unbounded
   *       alternation branch {@code (?:a|b+)+}, is not caught. Adding such a pattern to {@link SecretClassifier}
   *       would require extending this guard.</li>
   * </ul>
   */
  private static void rejectNestedUnboundedQuantifier(String atom, String base, String regex) {
    if (isUnbounded(base) && !atom.isEmpty() && atom.charAt(0) == '(' && groupBodyStartsWithUnboundedQuantifier(atom)) {
      throw new IllegalArgumentException(
        "Cannot export a portable regex: dropping the possessive quantifier would leave a ReDoS-prone nested " +
          "unbounded quantifier (e.g. \"(?:X+)+\"), which JavaScript/RE2 cannot guard with atomic groups, in pattern: " +
          regex);
    }
  }

  /** Whether {@code base} repeats without an upper bound: {@code *}, {@code +} or {@code {n,}} (but not {@code {n,m}}). */
  private static boolean isUnbounded(String base) {
    return "*".equals(base) || "+".equals(base) || base.matches("\\{\\d+,}");
  }

  /**
   * Whether the body of {@code group} (a string starting with {@code (}) <em>starts</em> with an atom carrying an
   * unbounded quantifier. This is the deliberately narrow, first-atom-only test described on
   * {@link #rejectNestedUnboundedQuantifier}; it is not a general ambiguity analysis.
   */
  private static boolean groupBodyStartsWithUnboundedQuantifier(String group) {
    int start = groupBodyStart(group, 0);
    // the group's own closing ')'
    int close = group.length() - 1;
    if (start >= close) {
      return false;
    }
    int afterFirstAtom = endOfAtom(group, start);
    RegexTranslator.Quantifier q = readQuantifier(group, afterFirstAtom);
    return q != null && isUnbounded(q.base);
  }

  /** Returns the index just past the single atom starting at {@code i} (escape, class, group or lone character). */
  private static int endOfAtom(String re, int i) {
    char c = re.charAt(i);
    if (c == '\\') {
      return readEscape(re, i);
    }
    if (c == '[') {
      return readClassEnd(re, i);
    }
    if (c == '(') {
      return matchingParen(re, i) + 1;
    }
    return i + 1;
  }

  private static final class Quantifier {
    private final String base;
    private final boolean possessive;
    private final boolean lazy;
    private final int next;

    private Quantifier(String base, boolean possessive, boolean lazy, int next) {
      this.base = base;
      this.possessive = possessive;
      this.lazy = lazy;
      this.next = next;
    }
  }
}
