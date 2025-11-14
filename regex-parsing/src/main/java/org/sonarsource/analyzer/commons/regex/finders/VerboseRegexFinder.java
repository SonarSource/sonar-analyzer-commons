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

import java.util.Collections;
import java.util.regex.Pattern;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassUnionTree;
import org.sonarsource.analyzer.commons.regex.ast.EscapedCharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.CHARACTER_RANGE;
import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.ESCAPED_CHARACTER_CLASS;
import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.PLAIN_CHARACTER;
import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.UNION;

public class VerboseRegexFinder extends RegexBaseVisitor {

  private static final String VERBOSE_QUANTIFIER_MESSAGE = "Use concise quantifier syntax '%s' instead of '%s'.";
  private static final String VERBOSE_CHARACTER_CLASS_MESSAGE = "Use concise character class syntax '%s' instead of '%s'.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public VerboseRegexFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }


  @Override
  public void visitCharacterClass(CharacterClassTree tree) {
    checkBulkyAlphaNumericCharacterClass(tree);
    checkBulkyNumericCharacterClass(tree);
    checkBulkyAnyCharacterClass(tree);
    super.visitCharacterClass(tree);
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    checkBulkyQuantifier(tree.getQuantifier());

    super.visitRepetition(tree);
  }

  private void checkBulkyAlphaNumericCharacterClass(CharacterClassTree tree) {
    CharacterClassElementTree element = tree.getContents();
    if (element.is(UNION) && ((CharacterClassUnionTree) element).getCharacterClasses().size() == 4) {
      boolean hasDigit = false;
      boolean hasLowerCase = false;
      boolean hasUpperCase = false;
      boolean hasUnderscore = false;

      for (CharacterClassElementTree subElement : ((CharacterClassUnionTree) element).getCharacterClasses()) {
        String raw = subElement.getText();
        if (subElement.is(CHARACTER_RANGE)) {
          hasDigit |= "0-9".equals(raw);
          hasLowerCase |= "a-z".equals(raw);
          hasUpperCase |= "A-Z".equals(raw);
        } else if (subElement.is(PLAIN_CHARACTER)) {
          hasUnderscore |= "_".equals(raw);
        }
      }

      if (hasDigit && hasLowerCase && hasUpperCase && hasUnderscore) {
        String expected = backslash(tree) + (tree.isNegated() ? "W" : "w");
        reportVerboseCharacterClass(expected, tree);
      }
    }
  }

  private void checkBulkyNumericCharacterClass(CharacterClassTree tree) {
    CharacterClassElementTree element = tree.getContents();
    if (element.is(CHARACTER_RANGE) && "0-9".equals(element.getText())) {
      String expected = backslash(tree) + (tree.isNegated() ? "D" : "d");
      reportVerboseCharacterClass(expected, tree);
    }
  }

  private void checkBulkyAnyCharacterClass(CharacterClassTree tree) {
    CharacterClassElementTree element = tree.getContents();
    if (tree.isNegated() || !element.is(UNION) || ((CharacterClassUnionTree) element).getCharacterClasses().size() != 2) {
      return;
    }

    boolean hasLowerEscapeW = false;
    boolean hasUpperEscapeW = false;
    boolean hasLowerEscapeD = false;
    boolean hasUpperEscapeD = false;
    boolean hasLowerEscapeS = false;
    boolean hasUpperEscapeS = false;

    for (CharacterClassElementTree subElement : ((CharacterClassUnionTree) element).getCharacterClasses()) {
      if (subElement.is(ESCAPED_CHARACTER_CLASS)) {
        char type = ((EscapedCharacterClassTree) subElement).getType();
        hasLowerEscapeW |= 'w' == type;
        hasUpperEscapeW |= 'W' == type;
        hasLowerEscapeD |= 'd' == type;
        hasUpperEscapeD |= 'D' == type;
        hasLowerEscapeS |= 's' == type;
        hasUpperEscapeS |= 'S' == type;
      }
    }

    boolean isBulkyAnyCharacterClass = (hasLowerEscapeW && hasUpperEscapeW)
      || (hasLowerEscapeD && hasUpperEscapeD)
      || (hasLowerEscapeS && hasUpperEscapeS && tree.activeFlags().contains(Pattern.DOTALL));
    if (isBulkyAnyCharacterClass) {
      reportVerboseCharacterClass(".", tree);
    }
  }

  private void reportVerboseCharacterClass(String expected, CharacterClassTree tree) {
    String message = String.format(VERBOSE_CHARACTER_CLASS_MESSAGE, expected, tree.getText());
    regexElementIssueReporter.report(tree, message, null, Collections.emptyList());
  }

  private void checkBulkyQuantifier(Quantifier quantifier) {
    String raw = quantifier.getText();
    BulkyQuantifier bulkyQuantifier = null;
    if (Pattern.matches("\\{0,1}\\??$", raw)) {
      bulkyQuantifier = new BulkyQuantifier("?", "{0,1}");
    } else if (Pattern.matches("\\{0,}\\??", raw)) {
      bulkyQuantifier = new BulkyQuantifier("*", "{0,}");
    } else if (Pattern.matches("\\{1,}\\??$", raw)) {
      bulkyQuantifier = new BulkyQuantifier("+", "{1,}");
    } else if (Pattern.matches("\\{(\\d+),\\1}\\??$", raw)) {
      int min = quantifier.getMinimumRepetitions();
      bulkyQuantifier = new BulkyQuantifier(String.format("{%d}", min), String.format("{%d,%d}", min, min));
    }
    if (bulkyQuantifier != null) {
      regexElementIssueReporter.report(quantifier, bulkyQuantifier.getMessage(), null, Collections.emptyList());
    }
  }

  private static class BulkyQuantifier{
    private final String concise;
    private final String verbose;

    protected BulkyQuantifier(String concise, String verbose) {
      this.concise = concise;
      this.verbose = verbose;
    }

    protected String getMessage() {
      return String.format(VERBOSE_QUANTIFIER_MESSAGE, concise, verbose);
    }
  }
}
