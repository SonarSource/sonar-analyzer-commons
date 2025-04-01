/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassUnionTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.helpers.GraphemeHelper;

public class GraphemeInClassFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Extract %d Grapheme Cluster(s) from this character class.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;
  private final List<RegexIssueLocation> graphemeClusters = new ArrayList<>();

  public GraphemeInClassFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitCharacterClass(CharacterClassTree tree) {
    super.visitCharacterClass(tree);
    if (!graphemeClusters.isEmpty()) {
      regexElementIssueReporter.report(tree, String.format(MESSAGE, graphemeClusters.size()), null, graphemeClusters);
    }
    graphemeClusters.clear();
  }

  @Override
  public void visitCharacterClassUnion(CharacterClassUnionTree tree) {
    graphemeClusters.addAll(GraphemeHelper.getGraphemeInList(tree.getCharacterClasses()));
    super.visitCharacterClassUnion(tree);
  }

}
