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
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class RepetitionTree extends RegexTree {

  private final RegexTree element;

  private final Quantifier quantifier;

  public RepetitionTree(RegexSource source, IndexRange range, RegexTree element, Quantifier quantifier, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.element = element;
    this.quantifier = quantifier;
  }

  public RegexTree getElement() {
    return element;
  }

  public Quantifier getQuantifier() {
    return quantifier;
  }

  public boolean isPossessive() {
    return quantifier.getModifier() == Quantifier.Modifier.POSSESSIVE;
  }

  public boolean isReluctant() {
    return quantifier.getModifier() == Quantifier.Modifier.RELUCTANT;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitRepetition(this);
  }

  @Override
  public Kind kind() {
    return Kind.REPETITION;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  @Nonnull
  @Override
  public List<AutomatonState> successors() {
    if (quantifier.getMinimumRepetitions() == 0) {
      Integer max = quantifier.getMaximumRepetitions();
      if (max != null && max == 0) {
        return Collections.singletonList(continuation());
      } else {
        return flipIfReluctant(element, continuation());
      }
    } else {
      return Collections.singletonList(element);
    }
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    continuation = new EndOfRepetitionState(this, continuation);
    super.setContinuation(continuation);
    int min = quantifier.getMinimumRepetitions();
    Integer max = quantifier.getMaximumRepetitions();
    if (max != null && max == 1) {
      element.setContinuation(continuation);
    } else if (min >= 1) {
      element.setContinuation(new BranchState(this, flipIfReluctant(this, continuation), activeFlags()));
    } else {
      element.setContinuation(this);
    }
  }

  private List<AutomatonState> flipIfReluctant(AutomatonState tree1, AutomatonState tree2) {
    if (quantifier.getModifier() == Quantifier.Modifier.RELUCTANT) {
      return Arrays.asList(tree2, tree1);
    } else {
      return Arrays.asList(tree1, tree2);
    }
  }
}
