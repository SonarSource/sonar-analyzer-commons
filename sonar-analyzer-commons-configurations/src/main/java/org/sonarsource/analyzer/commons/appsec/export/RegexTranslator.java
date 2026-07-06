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

  /**
   * Rewrites possessive quantifiers to atomic groups so the regex is valid .NET while keeping
   * the exact same matching semantics: {@code X*+ -> (?>X*)}, {@code X++ -> (?>X+)}, {@code X?+ -> (?>X?)},
   * {@code X{n,m}+ -> (?>X{n,m})}. Named groups {@code (?<name>…)} and backrefs {@code \k<name>} are .NET-compatible
   * and pass through unchanged. Greedy/lazy quantifiers are preserved.
   *
   * @throws IllegalArgumentException if the regex uses a Java-only construct this translator cannot faithfully
   *   render for .NET (see {@link #rejectUnsupportedConstructs(String)}).
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
        if (q.possessive) {
          out.append("(?>").append(atom).append(q.base).append(')');
        } else {
          out.append(atom).append(q.base);
          if (q.lazy) {
            out.append('?');
          }
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
        case 'Q':
        case 'E':
          throw unsupportedConstruct("\\Q...\\E literal quoting", regex);
        case '0':
          throw unsupportedConstruct("\\0nn octal escape", regex);
        case 'R':
          throw unsupportedConstruct("\\R linebreak matcher", regex);
        case 'h':
        case 'H':
          throw unsupportedConstruct("\\h / \\H horizontal-whitespace class", regex);
        case 'v':
        case 'V':
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
    int bodyStart;
    if (re.startsWith("(?", i)) {
      if (re.startsWith("(?<=", i) || re.startsWith("(?<!", i)) {
        bodyStart = i + 4;
      } else if (re.startsWith("(?<", i)) {
        // Named group (?<name>… ; body starts after the closing '>'.
        bodyStart = re.indexOf('>', i + 2) + 1;
      } else {
        // (?:  (?=  (?!  (?>
        bodyStart = i + 3;
      }
    } else {
      bodyStart = i + 1;
    }
    int close = matchingParen(re, i);
    String prefix = re.substring(i, bodyStart);
    String body = re.substring(bodyStart, close);
    return prefix + toPortableRegex(body) + ")";
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
