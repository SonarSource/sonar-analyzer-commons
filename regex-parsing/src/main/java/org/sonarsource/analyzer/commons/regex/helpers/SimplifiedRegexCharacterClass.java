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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  // Java Pattern's POSIX properties without UNICODE_CHARACTER_CLASS (inclusive range pairs).
  private static final Map<String, int[]> POSIX_RANGES = Map.ofEntries(
    Map.entry("Lower", new int[] {'a', 'z'}),
    Map.entry("Upper", new int[] {'A', 'Z'}),
    Map.entry("ASCII", new int[] {0, 0x7F}),
    Map.entry("Alpha", new int[] {'A', 'Z', 'a', 'z'}),
    Map.entry("Digit", new int[] {'0', '9'}),
    Map.entry("Alnum", new int[] {'0', '9', 'A', 'Z', 'a', 'z'}),
    Map.entry("Punct", new int[] {'!', '/', ':', '@', '[', 0x60, '{', '~'}),
    Map.entry("Graph", new int[] {'!', '~'}),
    Map.entry("Print", new int[] {' ', '~'}),
    Map.entry("Blank", new int[] {'\t', '\t', ' ', ' '}),
    Map.entry("Cntrl", new int[] {0, 0x1F, 0x7F, 0x7F}),
    Map.entry("XDigit", new int[] {'0', '9', 'A', 'F', 'a', 'f'}),
    Map.entry("Space", new int[] {'\t', '\r', ' ', ' '})
  );

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
    if (tree instanceof CharacterClassElementTree characterClassElementTree) {
      return new SimplifiedRegexCharacterClass(characterClassElementTree);
    } else if (tree instanceof DotTree dotTree) {
      return new SimplifiedRegexCharacterClass(dotTree);
    } else {
      return null;
    }
  }

  public boolean isEmpty() {
    return contents.isEmpty() && !containsUnknownCharacters;
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
    return contents.containsKey(0) && !contents.containsValue(null);
  }

  public boolean intersects(SimplifiedRegexCharacterClass that, boolean defaultAnswer) {
    if (defaultAnswer && ((containsUnknownCharacters && !that.isEmpty()) || (!isEmpty() && that.containsUnknownCharacters))) {
      return true;
    }
    return !findIntersections(that, true).isEmpty();
  }

  public List<RegexSyntaxElement> findIntersections(SimplifiedRegexCharacterClass that) {
    return findIntersections(that, false);
  }

  private List<RegexSyntaxElement> findIntersections(SimplifiedRegexCharacterClass that, boolean stopAtFirst) {
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
        if (inner.containsUnknownCharacters) {
          // When negating a class that contains unknown characters, we can't know for sure whether any character is in the
          // class, so we don't add any known characters to it
          characters.containsUnknownCharacters = true;
          characters.contents = new TreeMap<>();
          return;
        }
        boolean lastInsertedIsNotNull = false;
        if (inner.contents.get(0) == null) {
          characters.contents.put(0, tree);
          lastInsertedIsNotNull = true;
        }
        for (Map.Entry<Integer, RegexSyntaxElement> entry : inner.contents.entrySet()) {
          if (entry.getValue() == null) {
            characters.contents.put(entry.getKey(), tree);
            lastInsertedIsNotNull = true;
          } else if (lastInsertedIsNotNull) {
            characters.contents.put(entry.getKey(), null);
            lastInsertedIsNotNull = false;
          }
        }
      } else {
        super.visitCharacterClass(tree);
      }
    }

    @Override
    public void visitCharacterClassIntersection(CharacterClassIntersectionTree tree) {
      characters.containsUnknownCharacters = true;
    }

    @Override
    public void visitEscapedCharacterClass(EscapedCharacterClassTree tree) {
      if (tree.isProperty()) {
        addPosixProperty(tree);
        return;
      }
      switch (tree.getType()) {
        case 'd':
          characters.addRange('0', '9', tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.containsUnknownCharacters = true;
          }
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
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.containsUnknownCharacters = true;
          }
          break;
        case 'W':
          characters.addRange(0x00, '0' - 1, tree);
          characters.addRange('9' + 1, 'A' - 1, tree);
          characters.addRange('Z'+1, '_' - 1, tree);
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

    private void addPosixProperty(EscapedCharacterClassTree tree) {
      String property = tree.property();
      int[] ranges = POSIX_RANGES.get(property);
      // Other dialects and Unicode properties can have different, or version-dependent, contents.
      if (!(tree.getSource() instanceof JavaRegexSource) || ranges == null ||
        (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS) && !"ASCII".equals(property))) {
        characters.containsUnknownCharacters = true;
        return;
      }
      if (tree.activeFlags().contains(Pattern.CASE_INSENSITIVE) && ("Lower".equals(property) || "Upper".equals(property))) {
        // Case-insensitive Lower/Upper only match both ASCII cases since JDK 15.
        // Retain characters guaranteed to match on either side of that change.
        characters.containsUnknownCharacters = true;
        if (tree.isNegation()) {
          ranges = POSIX_RANGES.get("Alpha");
        }
      }
      addPosixRanges(tree, ranges);
    }

    private void addPosixRanges(EscapedCharacterClassTree tree, int[] ranges) {
      int from = 0;
      for (int i = 0; i < ranges.length; i += 2) {
        if (tree.isNegation()) {
          if (from < ranges[i]) {
            characters.addRange(from, ranges[i] - 1, tree);
          }
        } else {
          characters.addRange(ranges[i], ranges[i + 1], tree);
        }
        from = ranges[i + 1] + 1;
      }
      if (tree.isNegation()) {
        characters.addRange(from, Character.MAX_CODE_POINT, tree);
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
