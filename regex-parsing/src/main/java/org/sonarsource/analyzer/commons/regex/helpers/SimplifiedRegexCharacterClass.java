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
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public class SimplifiedRegexCharacterClass {

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
  private boolean containsUnknownNonAsciiCharacters = false;
  private final boolean supportPosixClasses;

  public SimplifiedRegexCharacterClass() {
    this(false);
  }

  private SimplifiedRegexCharacterClass(boolean supportPosixClasses) {
    this.supportPosixClasses = supportPosixClasses;
  }

  public SimplifiedRegexCharacterClass(CharacterClassElementTree tree) {
    this(tree, false);
  }

  public SimplifiedRegexCharacterClass(CharacterClassElementTree tree, boolean supportPosixClasses) {
    this(supportPosixClasses);
    add(tree);
  }

  public SimplifiedRegexCharacterClass(DotTree tree) {
    this();
    add(tree);
  }

  @Nullable
  public static SimplifiedRegexCharacterClass of(AutomatonState tree) {
    return of(tree, false);
  }

  @Nullable
  public static SimplifiedRegexCharacterClass of(AutomatonState tree, boolean supportPosixClasses) {
    if (tree instanceof CharacterClassElementTree characterClassElementTree) {
      return new SimplifiedRegexCharacterClass(characterClassElementTree, supportPosixClasses);
    } else if (tree instanceof DotTree dotTree) {
      return new SimplifiedRegexCharacterClass(dotTree);
    } else {
      return null;
    }
  }

  public boolean isEmpty() {
    return contents.isEmpty() && !hasUnknownCharacters();
  }

  private boolean hasUnknownCharacters() {
    return containsUnknownCharacters || containsUnknownNonAsciiCharacters;
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
    if (defaultAnswer && ((hasUnknownCharacters() && !that.isEmpty()) || (!isEmpty() && that.hasUnknownCharacters()))) {
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
    if ((isEmpty() && !that.isEmpty()) || (that.hasUnknownCharacters() && !defaultAnswer)) {
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
    return lastEntry != null && lastEntry.getValue() != null && lastEntry.getKey() <= thatEntry.getKey();
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
    private final boolean supportPosixClasses;

    public Builder(SimplifiedRegexCharacterClass characters) {
      this.characters = characters;
      this.supportPosixClasses = characters.supportPosixClasses;
    }

    @Override
    public void visitInCharClass(CharacterClassElementTree tree) {
      if (supportPosixClasses && tree instanceof PosixCharacterClassElementTree posix) {
        addPosixCharacterClass(posix);
      } else {
        super.visitInCharClass(tree);
      }
    }

    private void addPosixCharacterClass(PosixCharacterClassElementTree tree) {
      int[] ranges = switch (tree.property()) {
        case "alnum" -> new int[] {'0', '9', 'A', 'Z', 'a', 'z'};
        case "alpha" -> new int[] {'A', 'Z', 'a', 'z'};
        case "ascii" -> new int[] {0, 127};
        case "blank" -> new int[] {'\t', '\t', ' ', ' '};
        case "cntrl" -> new int[] {0, 31, 127, 127};
        case "digit" -> new int[] {'0', '9'};
        case "graph" -> new int[] {33, 126};
        case "print" -> new int[] {32, 126};
        case "lower" -> new int[] {'a', 'z'};
        case "upper" -> new int[] {'A', 'Z'};
        case "punct" -> new int[] {33, 47, 58, 64, 91, 96, 123, 126};
        case "space" -> new int[] {'\t', '\r', ' ', ' '};
        case "word" -> new int[] {'0', '9', 'A', 'Z', '_', '_', 'a', 'z'};
        case "xdigit" -> new int[] {'0', '9', 'A', 'F', 'a', 'f'};
        default -> new int[0];
      };
      if (ranges.length == 0) {
        // Unknown properties and word-boundary markers cannot prove character coverage.
        characters.containsUnknownCharacters = true;
        return;
      }
      SimplifiedRegexCharacterClass outer = characters;
      if (tree.isNegation()) {
        characters = new SimplifiedRegexCharacterClass();
      }
      for (int i = 0; i < ranges.length; i += 2) {
        addRange(ranges[i], ranges[i + 1], tree);
      }
      boolean unknownNonAscii = tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS) && !"ascii".equals(tree.property());
      if (tree.isNegation()) {
        SimplifiedRegexCharacterClass positive = characters;
        characters = outer;
        addComplement(positive, unknownNonAscii ? 127 : Character.MAX_CODE_POINT, tree);
      }
      characters.containsUnknownNonAsciiCharacters |= unknownNonAscii;
    }

    private void addComplement(SimplifiedRegexCharacterClass inner, int max, RegexSyntaxElement tree) {
      int from = 0;
      boolean included = false;
      for (Map.Entry<Integer, RegexSyntaxElement> entry : inner.contents.entrySet()) {
        if (entry.getKey() > max) {
          break;
        }
        if (!included && from < entry.getKey()) {
          characters.addRange(from, entry.getKey() - 1, tree);
        }
        from = entry.getKey();
        included = entry.getValue() != null;
      }
      if (!included && from <= max) {
        characters.addRange(from, max, tree);
      }
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
        if (inner.containsUnknownNonAsciiCharacters) {
          // Unicode POSIX membership is exact within ASCII, including after negation.
          addComplement(inner, 127, tree);
          characters.containsUnknownNonAsciiCharacters = true;
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
