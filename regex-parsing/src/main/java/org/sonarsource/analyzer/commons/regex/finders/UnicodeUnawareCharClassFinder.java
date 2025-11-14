/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.CharacterRangeTree;
import org.sonarsource.analyzer.commons.regex.ast.EscapedCharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;

public class UnicodeUnawareCharClassFinder extends RegexBaseVisitor {

  private static final List<Character> unicodeAwareClassesWithFlag = Arrays.asList('s', 'S', 'w', 'W');
  private static final Set<String> unicodeAwarePropertiesWithFlag = new HashSet<>(Arrays.asList(
    "Lower", "Upper", "Alpha", "Alnum", "Punct", "Graph", "Print", "Blank", "Space"));

  private static final Map<Character, Character> unicodeUnawareCharacterRanges = new HashMap<>();
  static {
    unicodeUnawareCharacterRanges.put('a', 'z');
    unicodeUnawareCharacterRanges.put('A', 'Z');
  }

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;
  private final RegexIssueReporter.InvocationIssue invocationIssueReporter;

  private final List<CharacterRangeTree> unicodeUnawareRanges = new ArrayList<>();
  private final List<RegexTree> unicodeAwareWithFlag = new ArrayList<>();
  private boolean containsUnicodeCharacterFlag = false;

  public UnicodeUnawareCharClassFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
    this.invocationIssueReporter = invocationIssueReporter;
  }

  @Override
  protected void before(RegexParseResult regexParseResult) {
    containsUnicodeCharacterFlag |= regexParseResult.getInitialFlags().contains(Pattern.UNICODE_CHARACTER_CLASS);
  }

  @Override
  protected void after(RegexParseResult regexParseResult) {
    int unicodeUnawareRangeSize = unicodeUnawareRanges.size();
    if (unicodeUnawareRangeSize == 1) {
      regexElementIssueReporter.report(unicodeUnawareRanges.get(0), "Replace this character range with a Unicode-aware character class.", null, Collections.emptyList());
    } else if (unicodeUnawareRangeSize > 1) {
      List<RegexIssueLocation> secondaries = unicodeUnawareRanges.stream()
        .map(tree -> new RegexIssueLocation(tree, "Character range"))
        .collect(Collectors.toList());
      regexElementIssueReporter.report(regexParseResult.getResult(), "Replace these character ranges with Unicode-aware character classes.", null, secondaries);
    }


    if (!unicodeAwareWithFlag.isEmpty() && !containsUnicodeCharacterFlag) {
      List<RegexIssueLocation> secondaries = unicodeAwareWithFlag.stream()
        .map(tree -> new RegexIssueLocation(tree, "Predefined/POSIX character class"))
        .collect(Collectors.toList());
      invocationIssueReporter.report("Enable the \"u\" flag or use a Unicode-aware alternative.", null, secondaries);
    }
  }

  @Override
  public void visitCharacterRange(CharacterRangeTree tree) {
    int lowerBound = tree.getLowerBound().codePointOrUnit();
    if (lowerBound < 0xFFFF) {
      Character expectedUpperBoundChar = unicodeUnawareCharacterRanges.get((char) lowerBound);
      if (expectedUpperBoundChar != null && expectedUpperBoundChar == tree.getUpperBound().codePointOrUnit()) {
        unicodeUnawareRanges.add(tree);
      }
    }
  }

  @Override
  public void visitEscapedCharacterClass(EscapedCharacterClassTree tree) {
    String property = tree.property();
    if ((property != null && unicodeAwarePropertiesWithFlag.contains(property)) ||
      unicodeAwareClassesWithFlag.contains(tree.getType())) {

      unicodeAwareWithFlag.add(tree);
    }
  }

  @Override
  public void visitNonCapturingGroup(NonCapturingGroupTree tree) {
    containsUnicodeCharacterFlag |= tree.activeFlags().contains(Pattern.UNICODE_CHARACTER_CLASS);
    super.visitNonCapturingGroup(tree);
  }
}
