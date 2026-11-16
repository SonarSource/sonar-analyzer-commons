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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntPredicate;
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

  private static final int MAX_ASCII = 0x7F;

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

  /**
   * True as long as every unknown character is above ASCII, so the membership of ASCII characters is still known exactly.
   */
  private boolean unknownCharactersAreNonAscii = true;

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

  private void markUnknown() {
    containsUnknownCharacters = true;
    unknownCharactersAreNonAscii = false;
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

    private static final IntPredicate POSIX_LETTER = asciiRange('a', 'z').or(asciiRange('A', 'Z'));

    private static final Map<String, IntPredicate> POSIX_MEMBERS = posixMembersByName();

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
      characters.markUnknown();
    }

    @Override
    public void visitCharacterClass(CharacterClassTree tree) {
      if (tree.isNegated()) {
        SimplifiedRegexCharacterClass old = characters;
        SimplifiedRegexCharacterClass inner = new SimplifiedRegexCharacterClass();
        characters = inner;
        super.visitCharacterClass(tree);
        characters = old;
        if (inner.containsUnknownCharacters && inner.unknownCharactersAreNonAscii) {
          // Only the characters above ASCII are unknown: the ASCII characters outside of the inner class are in the
          // negation, and the other characters remain unknown
          addAsciiComplement(inner, tree);
          characters.containsUnknownCharacters = true;
          return;
        }
        if (inner.containsUnknownCharacters) {
          // When negating a class that contains unknown characters, we can't know for sure whether any character is in the
          // class, so we don't add any known characters to it
          characters.markUnknown();
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

    private void addAsciiComplement(SimplifiedRegexCharacterClass inner, CharacterClassTree tree) {
      addAsciiRanges(c -> {
        Map.Entry<Integer, RegexSyntaxElement> entry = inner.contents.floorEntry(c);
        return entry == null || entry.getValue() == null;
      }, tree);
    }

    private void addAsciiRanges(IntPredicate members, CharacterClassElementTree tree) {
      int rangeStart = -1;
      for (int c = 0; c <= MAX_ASCII + 1; c++) {
        boolean member = c <= MAX_ASCII && members.test(c);
        if (member && rangeStart < 0) {
          rangeStart = c;
        } else if (!member && rangeStart >= 0) {
          characters.addRange(rangeStart, c - 1, tree);
          rangeStart = -1;
        }
      }
    }

    @Override
    public void visitInCharClass(CharacterClassElementTree tree) {
      if (tree instanceof PosixCharacterClassElementTree posixTree) {
        addPosixClass(posixTree);
      } else {
        super.visitInCharClass(tree);
      }
    }

    /**
     * Models the PHP/PCRE POSIX bracket classes with their ASCII meaning in the default locale. Anything else (the word
     * boundary markers, or the characters above ASCII in Unicode character class mode) is unknown and never treated as
     * an empty or universal set.
     */
    private void addPosixClass(PosixCharacterClassElementTree tree) {
      String property = tree.property();
      IntPredicate posixMembers = posixMembers(property, tree.activeFlags().contains(Pattern.CASE_INSENSITIVE));
      if (posixMembers == null) {
        characters.markUnknown();
        return;
      }
      boolean negation = tree.isNegation();
      IntPredicate asciiMembers = negation ? posixMembers.negate() : posixMembers;
      addAsciiRanges(asciiMembers, tree);
      if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS) && !"ascii".equals(property)) {
        characters.containsUnknownCharacters = true;
      } else if (negation) {
        // Without Unicode support, all the characters above ASCII are outside of the positive class
        characters.addRange(MAX_ASCII + 1, Character.MAX_CODE_POINT, tree);
      }
    }

    @Nullable
    private static IntPredicate posixMembers(String property, boolean caseInsensitive) {
      if (caseInsensitive && ("lower".equals(property) || "upper".equals(property))) {
        return POSIX_LETTER;
      }
      return POSIX_MEMBERS.get(property);
    }

    private static IntPredicate asciiRange(int from, int to) {
      return c -> c >= from && c <= to;
    }

    private static Map<String, IntPredicate> posixMembersByName() {
      IntPredicate digit = asciiRange('0', '9');
      IntPredicate alnum = POSIX_LETTER.or(digit);
      IntPredicate graph = asciiRange(0x21, 0x7E);
      Map<String, IntPredicate> members = new HashMap<>();
      members.put("alnum", alnum);
      members.put("alpha", POSIX_LETTER);
      members.put("ascii", asciiRange(0, MAX_ASCII));
      members.put("blank", asciiRange(' ', ' ').or(asciiRange('\t', '\t')));
      members.put("cntrl", asciiRange(0, 0x1F).or(asciiRange(0x7F, 0x7F)));
      members.put("digit", digit);
      members.put("graph", graph);
      members.put("lower", asciiRange('a', 'z'));
      members.put("print", asciiRange(' ', 0x7E));
      members.put("punct", graph.and(alnum.negate()));
      members.put("space", asciiRange('\t', '\r').or(asciiRange(' ', ' ')));
      members.put("upper", asciiRange('A', 'Z'));
      members.put("word", alnum.or(asciiRange('_', '_')));
      members.put("xdigit", digit.or(asciiRange('a', 'f')).or(asciiRange('A', 'F')));
      // Word boundary markers ("<" and ">") are not character sets: they have no entry
      return members;
    }

    @Override
    public void visitCharacterClassIntersection(CharacterClassIntersectionTree tree) {
      characters.markUnknown();
    }

    @Override
    public void visitEscapedCharacterClass(EscapedCharacterClassTree tree) {
      switch (tree.getType()) {
        case 'd':
          characters.addRange('0', '9', tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.markUnknown();
          }
          break;
        case 'D':
          characters.addRange(0x00, '0' - 1, tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange('9' + 1, 0xff, tree);
            characters.markUnknown();
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
            characters.markUnknown();
          }
          break;
        case 'W':
          characters.addRange(0x00, '0' - 1, tree);
          characters.addRange('9' + 1, 'A' - 1, tree);
          characters.addRange('Z' + 1, '_' - 1, tree);
          characters.addRange('`', '`', tree);
          if (tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS)) {
            characters.addRange('z' + 1, 'µ' - 1, tree);
            characters.markUnknown();
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
          characters.markUnknown();
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
