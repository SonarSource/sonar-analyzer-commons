/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.EscapedCharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.SimpleQuantifier;
import org.sonarsource.analyzer.commons.regex.helpers.SimplifiedRegexCharacterClass;

public class ReluctantQuantifierFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Replace this use of a reluctant quantifier with \"%s%s\".";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public ReluctantQuantifierFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitSequence(SequenceTree tree) {
    super.visitSequence(tree);
    List<RegexTree> items = tree.getItems();
    int repetitionPos = items.size() - 2;
    while (repetitionPos > 0 && items.get(repetitionPos).is(RegexTree.Kind.NON_CAPTURING_GROUP) &&
      ((NonCapturingGroupTree) items.get(repetitionPos)).getElement() == null) {
      repetitionPos--;
    }
    if (repetitionPos >= 0 && items.get(repetitionPos).is(RegexTree.Kind.REPETITION)) {
      RepetitionTree repetition = (RepetitionTree) items.get(repetitionPos);
      getReluctantlyQuantifiedElement(repetition).flatMap(element ->
          findNegatedCharacterClassFor(items.get(items.size() - 1), getBaseCharacter(element)))
        .ifPresent(negatedClass -> {
          String newQuantifier = makePossessiveOrGreedy(repetition.getQuantifier(), supportsAnyOfFeatures(tree, RegexFeature.POSSESSIVE_QUANTIFIER));
          String message = String.format(MESSAGE, negatedClass, newQuantifier);
          regexElementIssueReporter.report(repetition, message, null, Collections.emptyList());
        });
    }
  }

  private static Optional<RegexTree> getReluctantlyQuantifiedElement(RepetitionTree repetition) {
    RegexTree element = repetition.getElement();
    while (element.is(RegexTree.Kind.NON_CAPTURING_GROUP)) {
      element = ((NonCapturingGroupTree) element).getElement();
      if (element == null) {
        return Optional.empty();
      }
    }
    return (repetition.getQuantifier().getModifier() == Quantifier.Modifier.RELUCTANT
      && !repetition.getQuantifier().isFixed()
      && (element.is(RegexTree.Kind.DOT) || element.is(RegexTree.Kind.ESCAPED_CHARACTER_CLASS))) ? Optional.of(element)
      : Optional.empty();
  }

  protected String makePossessiveOrGreedy(Quantifier quantifier, boolean possessive) {
    String possessiveAddition = possessive ? "+" : "";
    if (quantifier instanceof SimpleQuantifier) {
      return ((SimpleQuantifier) quantifier).getKind() + possessiveAddition;
    } else {
      String max = Optional.ofNullable(quantifier.getMaximumRepetitions()).map(Object::toString).orElse("");
      return String.format("{%d,%s}%s", quantifier.getMinimumRepetitions(), max, possessiveAddition);
    }
  }

  private static Optional<String> findNegatedCharacterClassFor(RegexTree tree, @Nullable EscapedCharacterClassTree base) {
    if (tree instanceof CharacterClassElementTree && hasNoIntersection(((CharacterClassElementTree) tree), base)) {
      return Optional.empty();
    }
    String result;
    switch (tree.kind()) {
      case CHARACTER:
        result = "[^" + tree.getText() + negateEscapedCharacter(base) + "]";
        break;
      case ESCAPED_CHARACTER_CLASS:
        EscapedCharacterClassTree escapedClass = (EscapedCharacterClassTree) tree;
        result = escapedCharacterFollowedByEscapedCharacter(escapedClass, negateEscapedCharacter(base));
        break;
      case CHARACTER_CLASS:
        CharacterClassTree characterClass = (CharacterClassTree) tree;
        String body = characterClass.getContents().getText();
        if (characterClass.isNegated()) {
          result = "[" + body + escapedCharacterToString(base) + "]";
        } else {
          result = "[^" + body + negateEscapedCharacter(base) + "]";
        }
        break;
      case NON_CAPTURING_GROUP:
        RegexTree element = ((NonCapturingGroupTree)tree).getElement();
        return element == null ? Optional.empty() : findNegatedCharacterClassFor(element, base);
      default:
        return Optional.empty();
    }
    return Optional.of(result);
  }

  @Nullable
  private static EscapedCharacterClassTree getBaseCharacter(RegexTree tree) {
    return tree.is(RegexTree.Kind.DOT) ? null : (EscapedCharacterClassTree) tree;
  }

  private static boolean hasNoIntersection(CharacterClassElementTree tree, @Nullable CharacterClassElementTree base) {
    if (base == null) {
      return false;
    }
    SimplifiedRegexCharacterClass baseSimplifiedCharacterClass = new SimplifiedRegexCharacterClass(base);
    SimplifiedRegexCharacterClass treeSimplifiedCharacterClass = new SimplifiedRegexCharacterClass(tree);
    return !baseSimplifiedCharacterClass.intersects(treeSimplifiedCharacterClass, false);
  }

  private static String escapedCharacterFollowedByEscapedCharacter(EscapedCharacterClassTree escapedClass, String ignoredSymbol) {
    String negatedCharacter = backslash(escapedClass) + negateEscapedCharacterClassType(escapedClass.getType()) + getProperty(escapedClass);
    return ignoredSymbol.isEmpty() ? negatedCharacter : String.format("[%s%s]", negatedCharacter, ignoredSymbol);
  }

  private static String getProperty(EscapedCharacterClassTree escapedClass) {
    return escapedClass.isProperty() ? ("{" + escapedClass.property() + "}") : "";
  }

  private static char negateEscapedCharacterClassType(char type) {
    return Character.isLowerCase(type) ? Character.toUpperCase(type) : Character.toLowerCase(type);
  }

  private static String negateEscapedCharacter(@Nullable EscapedCharacterClassTree escapedClass) {
    return (escapedClass == null) ? "" : escapedCharacterFollowedByEscapedCharacter(escapedClass, "");
  }

  private static String escapedCharacterToString(@Nullable EscapedCharacterClassTree escapedClass) {
    return (escapedClass == null) ? "" : (backslash(escapedClass) + escapedClass.getType() + getProperty(escapedClass));
  }
}
