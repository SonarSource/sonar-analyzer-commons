/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassIntersectionTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterRangeTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.EscapedCharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.MiscEscapeSequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

public class SimplifiedRegexCharacterClass {

  // Inclusive range pairs, in ascending order. These properties use ASCII unless UNICODE_CHARACTER_CLASS is enabled.
  private static final Map<String, int[]> POSIX_RANGES = Map.ofEntries(
    Map.entry("Lower", new int[] {'a', 'z'}),
    Map.entry("Upper", new int[] {'A', 'Z'}),
    Map.entry("ASCII", new int[] {0, 0x7F}),
    Map.entry("Alpha", new int[] {'A', 'Z', 'a', 'z'}),
    Map.entry("Digit", new int[] {'0', '9'}),
    Map.entry("Alnum", new int[] {'0', '9', 'A', 'Z', 'a', 'z'}),
    Map.entry("Punct", new int[] {'!', '/', ':', '@', '[', '`', '{', '~'}),
    Map.entry("Graph", new int[] {'!', '~'}),
    Map.entry("Print", new int[] {' ', '~'}),
    Map.entry("Blank", new int[] {'\t', '\t', ' ', ' '}),
    Map.entry("Cntrl", new int[] {0, 0x1F, 0x7F, 0x7F}),
    Map.entry("XDigit", new int[] {'0', '9', 'A', 'F', 'a', 'f'}),
    Map.entry("Space", new int[] {'\t', '\r', ' ', ' '}));

  /**
   * This map defines the contents of the character class in the following way:<br>
   * For any entry {@code codepoint -> tree}, all the codepoints from {@code codepoint} up to (and excluding) the next
   * entry are in the character class and belong to the given tree.<br>
   * For any entry {@code codepoint -> null}, all the codepoints from {@code codepoint} up to (and excluding) the next
   * entry are not part of the character class.<br>
   * So a codepoint is contained in this class if and only if {@code contents.floorEntry(codePoint).getValue()} is
   * non-null and the tree returned by {@code getValue} will be the element of the character class which matches that
   * code point.
   */
  private TreeMap<Integer, RegexSyntaxElement> contents = new TreeMap<>();

  private boolean containsUnknownCharacters = false;
  private boolean containsPosixProperty = false;
  private boolean containsUnicodeCase = false;

  public SimplifiedRegexCharacterClass() {
  }

  public SimplifiedRegexCharacterClass(CharacterClassElementTree tree) {
    add(tree);
  }

  public SimplifiedRegexCharacterClass(DotTree tree) {
    add(tree);
  }

  @Nullable
  public static SimplifiedRegexCharacterClass of(AutomatonState tree) {
    return switch (tree) {
      case CharacterClassElementTree characterClassElementTree -> new SimplifiedRegexCharacterClass(characterClassElementTree);
      case DotTree dotTree -> new SimplifiedRegexCharacterClass(dotTree);
      case null, default -> null;
    };
  }

  public boolean isEmpty() {
    return contents.isEmpty() && !containsUnknownCharacters && !hasUnsupportedCaseFolding(this);
  }

  public void add(CharacterClassElementTree tree) {
    new Builder(this).visitInCharClass(tree);
  }

  public void add(DotTree tree) {
    char[] orderedExcludedCharacters;
    if (tree.activeFlags().contains(Pattern.DOTALL)) {
      orderedExcludedCharacters = new char[] {};
    } else if (tree.activeFlags().contains(Pattern.UNIX_LINES)) {
      orderedExcludedCharacters = new char[] {'\n'};
    } else {
      orderedExcludedCharacters = new char[] {'\n', '\r', '\u0085', '\u2028', '\u2029'};
    }
    int from = 0;
    for (char excludedCharacter : orderedExcludedCharacters) {
      int to = excludedCharacter - 1;
      if (to > from) {
        addRange(from, to, tree);
      }
      from = excludedCharacter + 1;
    }
    addRange(from, Character.MAX_CODE_POINT, tree);
  }

  public boolean matchesAnyCharacter() {
    return !hasUnsupportedCaseFolding(this) && contents.containsKey(0) && !contents.containsValue(null);
  }

  public boolean intersects(SimplifiedRegexCharacterClass that, boolean defaultAnswer) {
    if (hasUnsupportedCaseFolding(that)) {
      return defaultAnswer;
    }
    if (defaultAnswer && ((containsUnknownCharacters && !that.isEmpty()) || (!isEmpty() && that.containsUnknownCharacters))) {
      return true;
    }
    return !findIntersections(that, true).isEmpty();
  }

  public List<RegexSyntaxElement> findIntersections(SimplifiedRegexCharacterClass that) {
    return findIntersections(that, false);
  }

  private List<RegexSyntaxElement> findIntersections(SimplifiedRegexCharacterClass that, boolean stopAtFirst) {
    if (hasUnsupportedCaseFolding(that)) {
      return List.of();
    }
    Iterator<Map.Entry<Integer, RegexSyntaxElement>> iter = that.contents.entrySet().iterator();
    List<RegexSyntaxElement> intersections = new ArrayList<>();
    if (!iter.hasNext()) {
      return intersections;
    }
    Map.Entry<Integer, RegexSyntaxElement> entry = iter.next();
    while (iter.hasNext()) {
      Map.Entry<Integer, RegexSyntaxElement> nextEntry = iter.next();
      int to = (nextEntry.getValue() == null) ? (nextEntry.getKey() - 1) : nextEntry.getKey();
      RegexSyntaxElement value = entry.getValue();
      if (value != null && hasEntryBetween(entry.getKey(), to)) {
        intersections.add(value);
        if (stopAtFirst) {
          return intersections;
        }
      }
      entry = nextEntry;
    }

    RegexSyntaxElement value = entry.getValue();
    if (value != null && hasEntryBetween(entry.getKey(), Character.MAX_CODE_POINT)) {
      intersections.add(value);
    }
    return intersections;
  }

  /**
   * @param from inclusive
   * @param to inclusive
   */
  private boolean hasEntryBetween(int from, int to) {
    Map.Entry<Integer, RegexSyntaxElement> before = contents.floorEntry(from);
    return ((before != null && before.getValue() != null) ||
      !contents.subMap(from, false, to, true).isEmpty());
  }

  public boolean supersetOf(SimplifiedRegexCharacterClass that, boolean defaultAnswer) {
    if (hasUnsupportedCaseFolding(that)) {
      return defaultAnswer;
    }
    if ((isEmpty() && !that.isEmpty()) || (that.containsUnknownCharacters && !defaultAnswer)) {
      return false;
    }
    Iterator<Map.Entry<Integer, RegexSyntaxElement>> thatIter = that.contents.entrySet().iterator();
    if (!thatIter.hasNext()) {
      // that.contents is empty, any set is a superset of it
      return true;
    }
    Map.Entry<Integer, RegexSyntaxElement> thatEntry = thatIter.next();
    while (thatIter.hasNext()) {
      Map.Entry<Integer, RegexSyntaxElement> thatNextEntry = thatIter.next();
      if (notSupersetOfEntries(thatEntry, thatNextEntry)) {
        return false;
      }
      thatEntry = thatNextEntry;
    }
    if (thatEntry.getValue() == null) {
      return true;
    }
    Map.Entry<Integer, RegexSyntaxElement> lastEntry = contents.lastEntry();
    return lastEntry.getValue() != null && lastEntry.getKey() <= thatEntry.getKey();
  }

  private boolean hasUnsupportedCaseFolding(SimplifiedRegexCharacterClass that) {
    // Unicode folding of literals/ranges is only partially modeled. Do not compare that approximation with POSIX sets.
    return (containsPosixProperty || that.containsPosixProperty) && (containsUnicodeCase || that.containsUnicodeCase);
  }

  static boolean hasUnsupportedCaseFolding(SubAutomaton first, SubAutomaton second) {
    // A property and an incompletely modeled fold can occur at different positions in the same comparison.
    SimplifiedRegexCharacterClass flags = new SimplifiedRegexCharacterClass();
    return flags.scanFlags(first) || flags.scanFlags(second);
  }

  private boolean scanFlags(SubAutomaton automaton) {
    Set<AutomatonState> visited = new HashSet<>();
    ArrayDeque<AutomatonState> pending = new ArrayDeque<>();
    pending.add(automaton.start);
    while (!pending.isEmpty()) {
      AutomatonState state = pending.removeFirst();
      if (state == automaton.end || !visited.add(state)) {
        continue;
      }
      if (visited.size() > AbstractAutomataChecker.OrderedAutomataPairCache.MAX_CACHE_SIZE) {
        return true;
      }
      if (state instanceof CharacterClassElementTree element) {
        SimplifiedRegexCharacterClass characters = new SimplifiedRegexCharacterClass(element);
        if (hasUnsupportedCaseFolding(characters)) {
          return true;
        }
        containsPosixProperty |= characters.containsPosixProperty;
        containsUnicodeCase |= characters.containsUnicodeCase;
      }
      pending.addAll(state.successors());
    }
    return false;
  }

  private boolean notSupersetOfEntries(Map.Entry<Integer, RegexSyntaxElement> thatEntry, Map.Entry<Integer, RegexSyntaxElement> thatNextEntry) {
    if (thatEntry.getValue() != null) {
      Map.Entry<Integer, RegexSyntaxElement> thisBefore = contents.floorEntry(thatEntry.getKey());
      if (thisBefore == null || thisBefore.getValue() == null) {
        return true;
      }
      int to = (thatNextEntry.getValue() == null) ? (thatNextEntry.getKey() - 1) : thatNextEntry.getKey();
      return contents.subMap(thatEntry.getKey(), false, to, true).values().stream()
        .anyMatch(Objects::isNull);
    }
    return false;
  }

  public void addRange(int from, int to, RegexSyntaxElement tree) {
    Map.Entry<Integer, RegexSyntaxElement> oldEntry = contents.floorEntry(to);
    Integer oldEnd = oldEntry == null ? null : contents.higherKey(oldEntry.getKey());
    contents.put(from, tree);
    for (Map.Entry<Integer, RegexSyntaxElement> entry : contents.subMap(from, false, to, true).entrySet()) {
      if (entry.getValue() == null) {
        entry.setValue(tree);
      }
    }
    int next = to + 1;
    if (next <= Character.MAX_CODE_POINT) {
      if (oldEntry != null && oldEntry.getValue() != null && (oldEnd == null || oldEnd > next)) {
        contents.put(next, oldEntry.getValue());
      } else if (!contents.containsKey(next)) {
        contents.put(next, null);
      }
    }
  }

  private static class Builder extends RegexBaseVisitor {

    private SimplifiedRegexCharacterClass characters;

    public Builder(SimplifiedRegexCharacterClass characters) {
      this.characters = characters;
    }

    @Override
    public void visitInCharClass(CharacterClassElementTree tree) {
      characters.containsUnicodeCase |= tree.activeFlags().contains(Pattern.CASE_INSENSITIVE) && tree.activeFlags().contains(Pattern.UNICODE_CASE);
      super.visitInCharClass(tree);
    }

    @Override
    public void visitCharacter(CharacterTree tree) {
      addRange(tree.codePointOrUnit(), tree.codePointOrUnit(), tree);
    }

    @Override
    public void visitCharacterRange(CharacterRangeTree tree) {
      addRange(tree.getLowerBound().codePointOrUnit(), tree.getUpperBound().codePointOrUnit(), tree);
    }

    @Override
    public void visitMiscEscapeSequence(MiscEscapeSequenceTree tree) {
      characters.containsUnknownCharacters = true;
    }

    @Override
    public void visitCharacterClass(CharacterClassTree tree) {
      if (tree.isNegated()) {
        SimplifiedRegexCharacterClass old = characters;
        SimplifiedRegexCharacterClass inner = new SimplifiedRegexCharacterClass();
        characters = inner;
        super.visitCharacterClass(tree);
        characters = old;
        characters.containsPosixProperty |= inner.containsPosixProperty;
        characters.containsUnicodeCase |= inner.containsUnicodeCase;
        if (inner.containsUnknownCharacters) {
          // When negating a class that contains unknown characters, we can't know for sure whether any character is in the
          // class, so we don't add any known characters to it
          characters.containsUnknownCharacters = true;
          characters.contents = new TreeMap<>();
          return;
        }
        addComplement(inner, tree);
      } else {
        super.visitCharacterClass(tree);
      }
    }

    private void addComplement(SimplifiedRegexCharacterClass inner, RegexSyntaxElement tree) {
      int from = 0;
      RegexSyntaxElement previous = null;
      for (Map.Entry<Integer, RegexSyntaxElement> entry : inner.contents.entrySet()) {
        if (previous == null && from < entry.getKey()) {
          characters.addRange(from, entry.getKey() - 1, tree);
        }
        from = entry.getKey();
        previous = entry.getValue();
      }
      if (previous == null) {
        characters.addRange(from, Character.MAX_CODE_POINT, tree);
      }
    }

    @Override
    public void visitCharacterClassIntersection(CharacterClassIntersectionTree tree) {
      characters.containsUnknownCharacters = true;
    }

    @Override
    public void visitEscapedCharacterClass(EscapedCharacterClassTree tree) {
      switch (tree.getType()) {
        case 'p', 'P':
          addPosixProperty(tree);
          break;
        case 'd':
          characters.addRange('0', '9', tree);
          markUnknownUnicodeCharacters(tree);
          break;
        case 'D':
          characters.addRange(0x00, '0' - 1, tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange('9' + 1, 0xff, tree);
            characters.containsUnknownCharacters = true;
          } else {
            characters.addRange('9' + 1, Character.MAX_CODE_POINT, tree);
          }
          break;
        case 'w':
          characters.addRange('0', '9', tree);
          characters.addRange('A', 'Z', tree);
          characters.addRange('_', '_', tree);
          characters.addRange('a', 'z', tree);
          markUnknownUnicodeCharacters(tree);
          break;
        case 'W':
          characters.addRange(0x00, '0' - 1, tree);
          characters.addRange('9' + 1, 'A' - 1, tree);
          characters.addRange('Z' + 1, '_' - 1, tree);
          characters.addRange('`', '`', tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange('z' + 1, 'µ' - 1, tree);
            characters.containsUnknownCharacters = true;
          } else {
            characters.addRange('z' + 1, Character.MAX_CODE_POINT, tree);
          }
          break;
        case 's':
          characters.addRange('\t', '\r', tree);
          characters.addRange(' ', ' ', tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange(0x85, 0x85, tree);
            characters.addRange(0xA0, 0xA0, tree);
            characters.addRange(0x1680, 0x1680, tree);
            characters.addRange(0x2000, 0x200A, tree);
            characters.addRange(0x2028, 0x2029, tree);
            characters.addRange(0x202F, 0x202F, tree);
            characters.addRange(0x205F, 0x205F, tree);
            characters.addRange(0x3000, 0x3000, tree);
          }
          break;
        case 'S':
          characters.addRange(0x00, '\t' - 1, tree);
          characters.addRange('\r' + 1, ' ' - 1, tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange(' ' + 1, 0x84, tree);
            characters.addRange(0x86, 0x9F, tree);
            characters.addRange(0xA1, 0x167F, tree);
            characters.addRange(0x1681, 0x1FFF, tree);
            characters.addRange(0x200B, 0x2027, tree);
            characters.addRange(0x202A, 0x202E, tree);
            characters.addRange(0x2030, 0x205E, tree);
            characters.addRange(0x2060, 0x2FFF, tree);
            characters.addRange(0x3001, Character.MAX_CODE_POINT, tree);
          } else {
            characters.addRange(' ' + 1, Character.MAX_CODE_POINT, tree);
          }
          break;
        default:
          characters.containsUnknownCharacters = true;
          break;
      }
    }

    private void markUnknownUnicodeCharacters(EscapedCharacterClassTree tree) {
      if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
        characters.containsUnknownCharacters = true;
      }
    }

    private void addPosixProperty(EscapedCharacterClassTree tree) {
      String property = tree.property();
      if (tree.activeFlags().contains(Pattern.CASE_INSENSITIVE) && ("Lower".equals(property) || "Upper".equals(property))) {
        property = "Alpha";
      }
      int[] ranges = property == null ? null : POSIX_RANGES.get(property);
      if (ranges == null || !(tree.getSource() instanceof JavaRegexSource) || tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
        characters.containsUnknownCharacters = true;
        return;
      }
      characters.containsPosixProperty = true;
      SimplifiedRegexCharacterClass propertyCharacters = tree.isNegation() ? new SimplifiedRegexCharacterClass() : characters;
      for (int i = 0; i < ranges.length; i += 2) {
        // POSIX properties do not use the Unicode folding applied to literal ranges, even with UNICODE_CASE.
        propertyCharacters.addRange(ranges[i], ranges[i + 1], tree);
      }
      if (tree.isNegation()) {
        addComplement(propertyCharacters, tree);
      }
    }

    private void addRange(int from, int to, CharacterClassElementTree tree) {
      characters.addRange(from, to, tree);
      if (tree.activeFlags().contains(Pattern.CASE_INSENSITIVE)) {
        addCaseInsensitiveRangeFor(from, to, tree, 'A', 'Z', 'a', 'z');
        if (tree.activeFlags().contains(Pattern.UNICODE_CASE)) {
          addCaseInsensitiveRangeFor(from, to, tree, 'À', 'Þ', 'à', 'þ');
        }
      }
    }

    private void addCaseInsensitiveRangeFor(int from, int to, CharacterClassElementTree tree, char upperStart, char upperEnd, char lowerStart, char lowerEnd) {
      final int lowerCaseShift = lowerStart - upperStart;
      if (from <= upperEnd && to >= upperStart) {
        characters.addRange(Math.max(from, upperStart) + lowerCaseShift, Math.min(to, upperEnd) + lowerCaseShift, tree);
      }
      if (from <= lowerEnd && to >= lowerStart) {
        characters.addRange(Math.max(from, lowerStart) - lowerCaseShift, Math.min(to, lowerEnd) - lowerCaseShift, tree);
      }
    }

  }

}
