package org.sonarsource.analyzer.commons.regex.finders;

import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

import java.util.Collections;

public class SuperfluousCurlyBraceFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Rework this part of the regex to not match the empty string.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public SuperfluousCurlyBraceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {

    Quantifier quantifier = tree.getQuantifier();
    int min = quantifier.getMinimumRepetitions();
    Integer max = quantifier.getMaximumRepetitions();

    if (max != null && max == min) { // is it (xyz){N,N}
      if (min == 1) { // is it (xyz){1}
        super.visitRepetition(tree);
        regexElementIssueReporter.report(quantifier, "Remove this unnecessary quantifier.", null, Collections.emptyList());
      } else if (min == 0) {  // is it (xyz){0}
        regexElementIssueReporter.report(tree, "Remove this unnecessarily quantified expression.", null, Collections.emptyList());
      }
    }
  }
}
