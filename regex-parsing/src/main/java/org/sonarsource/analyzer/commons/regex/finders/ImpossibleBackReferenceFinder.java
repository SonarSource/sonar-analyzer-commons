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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BackReferenceTree;
import org.sonarsource.analyzer.commons.regex.ast.CapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.EndOfCapturingGroupState;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

public class ImpossibleBackReferenceFinder extends RegexBaseVisitor {

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private Set<BackReferenceTree> impossibleBackReferences = new LinkedHashSet<>();
  private Map<String, CapturingGroupTree> capturingGroups = new HashMap<>();

  public ImpossibleBackReferenceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitBackReference(BackReferenceTree tree) {
    if (!capturingGroups.containsKey(tree.groupName())) {
      impossibleBackReferences.add(tree);
    }
  }

  @Override
  public void visitCapturingGroup(CapturingGroupTree group) {
    super.visitCapturingGroup(group);
    addGroup(group);
  }

  private void addGroup(CapturingGroupTree group) {
    capturingGroups.put("" + group.getGroupNumber(), group);
    group.getName().ifPresent(name -> capturingGroups.put(name, group));
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    Map<String, CapturingGroupTree> originalCapturingGroups = capturingGroups;
    Map<String, CapturingGroupTree> allCapturingGroups = new HashMap<>();
    for (RegexTree alternative : tree.getAlternatives()) {
      capturingGroups = new HashMap<>(originalCapturingGroups);
      visit(alternative);
      allCapturingGroups.putAll(capturingGroups);
    }
    capturingGroups = allCapturingGroups;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    Integer maximumRepetitions = tree.getQuantifier().getMaximumRepetitions();
    if (maximumRepetitions != null && maximumRepetitions < 2) {
      super.visitRepetition(tree);
      return;
    }
    Set<BackReferenceTree> originalImpossibleBackReferences = impossibleBackReferences;
    impossibleBackReferences = new LinkedHashSet<>();
    Map<String, CapturingGroupTree> originalCapturingGroups = new HashMap<>(capturingGroups);
    super.visitRepetition(tree);
    if (!impossibleBackReferences.isEmpty()) {
      capturingGroups = originalCapturingGroups;
      findReachableGroups(tree.getElement(), tree.continuation(), impossibleBackReferences, new HashSet<>());
      // Visit the body of the loop a second time, this time with the groups that could be set in the first iteration
      impossibleBackReferences = originalImpossibleBackReferences;
      super.visitRepetition(tree);
    }
  }

  private void findReachableGroups(AutomatonState start, AutomatonState stop, Set<BackReferenceTree> preliminaryImpossibleReferences, Set<AutomatonState> visited) {
    if (start == stop || (start instanceof BackReferenceTree && preliminaryImpossibleReferences.contains(start)) || visited.contains(start)) {
      return;
    }
    visited.add(start);
    if (start instanceof EndOfCapturingGroupState) {
      addGroup(((EndOfCapturingGroupState) start).group());
    }
    for (AutomatonState successor: start.successors()) {
      findReachableGroups(successor, stop, preliminaryImpossibleReferences, visited);
    }
  }

  @Override
  protected void after(RegexParseResult regexParseResult) {
    for (BackReferenceTree backReference : impossibleBackReferences) {
      String message;
      List<RegexIssueLocation> secondaries = new ArrayList<>();
      if (capturingGroups.containsKey(backReference.groupName())) {
        message = "Fix this backreference, so that it refers to a group that can be matched before it.";
        CapturingGroupTree group = capturingGroups.get(backReference.groupName());
        secondaries.add(new RegexIssueLocation(group, "This group is used in a backreference before it is defined"));
      } else {
        message = "Fix this backreference - it refers to a capturing group that doesn't exist.";
      }
      regexElementIssueReporter.report(backReference, message, null, secondaries);
    }
  }
}
