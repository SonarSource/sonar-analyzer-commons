/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2022 SonarSource SA
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassUnionTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.helpers.SimplifiedRegexCharacterClass;

public class DuplicatesInCharacterClassFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove duplicates in this character class.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public DuplicatesInCharacterClassFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitCharacterClassUnion(CharacterClassUnionTree tree) {
    Set<RegexSyntaxElement> duplicates = new LinkedHashSet<>();
    SimplifiedRegexCharacterClass characterClass = new SimplifiedRegexCharacterClass();
    for (CharacterClassElementTree element : tree.getCharacterClasses()) {
      SimplifiedRegexCharacterClass elementCharacterClass;
      try {
        elementCharacterClass = new SimplifiedRegexCharacterClass(element);
      } catch (IllegalArgumentException e) {
        // TODO: remove exception catching once the underlying problem is fixed: https://github.com/SonarSource/sonar-analyzer-commons/issues/156
        return;
      }

      List<RegexSyntaxElement> intersections = elementCharacterClass.findIntersections(characterClass);
      if (!intersections.isEmpty()) {
        // The element the current element is intersecting with should be included as well.
        duplicates.addAll(intersections);
        duplicates.add(element);
      }
      characterClass.add(element);
    }
    if (!duplicates.isEmpty()) {
      List<RegexIssueLocation> secondaries = duplicates.stream()
        .skip(1)
        .map(duplicate -> new RegexIssueLocation(duplicate, "Additional duplicate"))
        .collect(Collectors.toList());
      regexElementIssueReporter.report(duplicates.iterator().next(), MESSAGE, null, secondaries);
    }
    super.visitCharacterClassUnion(tree);
  }

}
