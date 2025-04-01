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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.MatchType;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.AtomicGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BackReferenceTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.helpers.IntersectAutomataChecker;
import org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker;
import org.sonarsource.analyzer.commons.regex.helpers.SimplifiedRegexCharacterClass;
import org.sonarsource.analyzer.commons.regex.helpers.SubAutomaton;

import static org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker.canReachWithoutConsumingInput;
import static org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker.canReachWithoutConsumingInputNorCrossingBoundaries;
import static org.sonarsource.analyzer.commons.regex.helpers.RegexTreeHelper.isAnchoredAtEnd;


public abstract class RedosFinder {

  private final RegexReachabilityChecker reachabilityChecker = new RegexReachabilityChecker(false);
  private final IntersectAutomataChecker intersectionChecker = new IntersectAutomataChecker(false);

  /**
   * The maximum number of repetitions we keep track of in order to find overlapping consecutive repetitions.
   * If a regex contains more repetitions than this, we will ignore some combinations of them to avoid performance
   * problems (possibly causing FNs).
   */
  private static final int MAX_TRACKED_REPETITIONS = 10;

  /**
   * The maximum regex length that we analyze. If a regex contains more characters than this, we skip this rule to avoid
   * performance problems.
   */
  private static final int MAX_REGEX_LENGTH = 1000;

  /**
   * "Optimized" here should be understood as the optimization performed by the Java9 regex engine.
   * If no such optimization is performed, these cases are exponential.
   */
  public enum BacktrackingType {
    NO_ISSUE(0),
    LINEAR_WHEN_OPTIMIZED(1),
    ALWAYS_QUADRATIC(2),
    QUADRATIC_WHEN_OPTIMIZED(3),
    ALWAYS_EXPONENTIAL(4);

    private final int priority;

    BacktrackingType(int priority) {
      this.priority = priority;
    }

    public BacktrackingType max(BacktrackingType another) {
      return this.priority > another.priority ? this : another;
    }
  }

  private boolean regexContainsBackReference;
  private BacktrackingType foundBacktrackingType;

  protected abstract Optional<String> message(BacktrackingType foundBacktrackingType, boolean regexContainsBackReference);

  public void checkRegex(RegexParseResult regexForLiterals, MatchType matchType, RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    if (regexForLiterals.getResult().getText().length() > MAX_REGEX_LENGTH) {
      return;
    }
    regexContainsBackReference = false;
    foundBacktrackingType = BacktrackingType.NO_ISSUE;
    reachabilityChecker.clearCache();
    intersectionChecker.clearCache();
    boolean isUsedForFullMatch = matchType == MatchType.FULL || matchType == MatchType.BOTH;
    boolean isUsedForPartialMatch = matchType == MatchType.PARTIAL || matchType == MatchType.BOTH;
    RedosVisitor visitor = new RedosVisitor(regexForLiterals.getStartState(), regexForLiterals.getFinalState(), isUsedForFullMatch, isUsedForPartialMatch);
    visitor.visit(regexForLiterals);
    message(foundBacktrackingType, regexContainsBackReference)
      .ifPresent(m -> regexElementIssueReporter.report(regexForLiterals.getResult(), m, null, Collections.emptyList()));
  }

  private class RedosVisitor extends RegexBaseVisitor {

    private final Deque<RepetitionTree> nonPossessiveRepetitions = new ArrayDeque<>();
    private final Map<AutomatonState, Boolean> canFailCache = new HashMap<>();

    private final AutomatonState startOfRegex;
    private final AutomatonState endOfRegex;
    private final boolean isUsedForFullMatch;
    private final boolean isUsedForPartialMatch;

    public RedosVisitor(AutomatonState startOfRegex, AutomatonState endOfRegex, boolean isUsedForFullMatch, boolean isUsedForPartialMatch) {
      this.startOfRegex = startOfRegex;
      this.endOfRegex = endOfRegex;
      this.isUsedForFullMatch = isUsedForFullMatch;
      this.isUsedForPartialMatch = isUsedForPartialMatch;
    }

    @Override
    public void visitRepetition(RepetitionTree tree) {
      if (canFail(tree.continuation())) {
        if (!tree.isPossessive() && tree.getQuantifier().isOpenEnded()) {
          new BacktrackingFinder(tree.isReluctant(), tree.continuation()).visit(tree.getElement());
        } else {
          super.visitRepetition(tree);
        }
        checkForOverlappingRepetitions(tree);
      }
    }

    private void checkForOverlappingRepetitions(RepetitionTree tree) {
      if (tree.getQuantifier().isOpenEnded() && canFail(tree)) {
        for (RepetitionTree repetition : nonPossessiveRepetitions) {
          if (reachabilityChecker.canReach(repetition, tree)) {
            SubAutomaton repetitionAuto = new SubAutomaton(repetition.getElement(), repetition.continuation(), false);
            SubAutomaton continuationAuto = new SubAutomaton(repetition.continuation(), tree, false);
            SubAutomaton treeAuto = new SubAutomaton(tree.getElement(), tree.continuation(), false);
            if (subAutomatonCanConsume(repetitionAuto, continuationAuto)
              && automatonIsEmptyOrIntersects(continuationAuto, treeAuto)
              && intersectionChecker.check(repetitionAuto, treeAuto)) {
              addBacktracking(BacktrackingType.ALWAYS_QUADRATIC);
            }
          }
        }
        if (overlapsWithImplicitMatchAlls(tree)) {
          addBacktracking(BacktrackingType.ALWAYS_QUADRATIC);
        }
        addIfNonPossessive(tree);
      }
    }

    private boolean subAutomatonCanConsume(SubAutomaton auto1, SubAutomaton auto2) {
      return canReachWithoutConsumingInputNorCrossingBoundaries(auto1.end, auto2.end)
        || intersectionChecker.check(auto1, auto2);
    }

    private boolean automatonIsEmptyOrIntersects(SubAutomaton auto1, SubAutomaton auto2) {
      return canReachWithoutConsumingInputNorCrossingBoundaries(auto1.start, auto1.end)
        || intersectionChecker.check(auto1, auto2);
    }

    private void addIfNonPossessive(RepetitionTree tree) {
      if (!tree.isPossessive()) {
        nonPossessiveRepetitions.add(tree);
        if (nonPossessiveRepetitions.size() > MAX_TRACKED_REPETITIONS) {
          nonPossessiveRepetitions.removeFirst();
        }
      }
    }

    /**
     * When used for partial matches, a regex acts as if it had `(?s:.*)` attached to its beginning and end unless anchored.
     */
    private boolean overlapsWithImplicitMatchAlls(RepetitionTree tree) {
      return isUsedForPartialMatch && canReachWithoutConsumingInputNorCrossingBoundaries(startOfRegex, tree);
    }

    @Override
    public void visitBackReference(BackReferenceTree tree) {
      regexContainsBackReference = true;
    }

    private boolean canFail(AutomatonState state) {
      return canFail(state, !isUsedForFullMatch && !isAnchoredAtEnd(state));
    }

    private boolean canFail(AutomatonState state, boolean succeedOnEnd) {
      if (canFailCache.containsKey(state)) {
        return canFailCache.get(state);
      }
      canFailCache.put(state, true);
      if (state.incomingTransitionType() != AutomatonState.TransitionType.EPSILON) {
        return true;
      }
      if (canMatchAnything(state)) {
        succeedOnEnd = true;
        state = state.continuation();
      }
      if ((succeedOnEnd && canReachWithoutConsumingInput(state, endOfRegex))) {
        canFailCache.put(state, false);
        return false;
      }
      for (AutomatonState successor : state.successors()) {
        if (!canFail(successor, succeedOnEnd)) {
          canFailCache.put(state, false);
          return false;
        }
      }
      return true;
    }

    private boolean canMatchAnything(AutomatonState state) {
      if (!(state instanceof RepetitionTree)) {
        return false;
      }
      RepetitionTree repetition = (RepetitionTree) state;
      return repetition.getQuantifier().getMinimumRepetitions() == 0 && repetition.getQuantifier().isOpenEnded()
        && canMatchAnyCharacter(repetition.getElement());
    }

    private boolean canMatchAnyCharacter(RegexTree tree) {
      SimplifiedRegexCharacterClass characterClass = new SimplifiedRegexCharacterClass();
      for (RegexTree singleCharacter : collectSingleCharacters(tree, new ArrayList<>())) {
        if (singleCharacter.is(RegexTree.Kind.DOT)) {
          characterClass.add((DotTree) singleCharacter);
        } else {
          characterClass.add((CharacterClassElementTree) singleCharacter);
        }
      }
      return characterClass.matchesAnyCharacter();
    }

    private List<RegexTree> collectSingleCharacters(@Nullable RegexTree tree, List<RegexTree> accumulator) {
      if (tree == null) {
        return accumulator;
      }
      if (tree instanceof CharacterClassElementTree || tree.is(RegexTree.Kind.DOT)) {
        accumulator.add(tree);
      } else if (tree.is(RegexTree.Kind.DISJUNCTION)) {
        for (RegexTree alternative : ((DisjunctionTree) tree).getAlternatives()) {
          collectSingleCharacters(alternative, accumulator);
        }
      } else if (tree instanceof GroupTree) {
        collectSingleCharacters(((GroupTree) tree).getElement(), accumulator);
      } else if (tree.is(RegexTree.Kind.REPETITION)) {
        RepetitionTree repetition = (RepetitionTree) tree;
        if (repetition.getQuantifier().getMinimumRepetitions() <= 1) {
          collectSingleCharacters(repetition.getElement(), accumulator);
        }
      }
      return accumulator;
    }

  }

  private void addBacktracking(BacktrackingType newBacktrackingType) {
    foundBacktrackingType = foundBacktrackingType.max(newBacktrackingType);
  }

  private class BacktrackingFinder extends RegexBaseVisitor {

    private final boolean isReluctant;
    private final AutomatonState endOfLoop;

    public BacktrackingFinder(boolean isReluctant, AutomatonState endOfLoop) {
      this.isReluctant = isReluctant;
      this.endOfLoop = endOfLoop;
    }

    @Override
    public void visitAtomicGroup(AtomicGroupTree tree) {
      new RedosVisitor(tree, tree.continuation(), false, false).visit(tree);
    }

    @Override
    public void visitRepetition(RepetitionTree tree) {
      if (tree.isPossessive()) {
        new RedosVisitor(tree, tree.continuation(), false, false).visit(tree);
      } else if (containsIntersections(List.of(tree.getElement(), tree.continuation()))) {
        BacktrackingType greedyComplexity = tree.getQuantifier().isOpenEnded() ? BacktrackingType.QUADRATIC_WHEN_OPTIMIZED : BacktrackingType.LINEAR_WHEN_OPTIMIZED;
        addBacktracking(isReluctant ? BacktrackingType.ALWAYS_EXPONENTIAL : greedyComplexity);
        super.visitRepetition(tree);
      } else {
        super.visitRepetition(tree);
      }
    }

    @Override
    public void visitDisjunction(DisjunctionTree tree) {
      if (containsIntersections(tree.getAlternatives())) {
        addBacktracking(isReluctant ? BacktrackingType.ALWAYS_EXPONENTIAL : BacktrackingType.LINEAR_WHEN_OPTIMIZED);
      } else {
        super.visitDisjunction(tree);
      }
    }

    @Override
    public void visitBackReference(BackReferenceTree tree) {
      regexContainsBackReference = true;
    }

    boolean containsIntersections(List<? extends AutomatonState> alternatives) {
      for (int i = 0; i < alternatives.size() - 1; i++) {
        AutomatonState state1 = alternatives.get(i);
        for (int j = i + 1; j < alternatives.size(); j++) {
          AutomatonState state2 = alternatives.get(j);
          SubAutomaton auto1 = new SubAutomaton(state1, endOfLoop, false);
          SubAutomaton auto2 = new SubAutomaton(state2, endOfLoop, false);
          if (intersectionChecker.check(auto1, auto2)) {
            return true;
          }
        }
      }
      return false;
    }
  }

}
