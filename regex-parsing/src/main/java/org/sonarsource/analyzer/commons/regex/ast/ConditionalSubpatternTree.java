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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class ConditionalSubpatternTree extends GroupTree {

  private final GroupTree condition;
  private final RegexTree yesPattern;
  @Nullable
  private final SourceCharacter pipe;
  @Nullable
  private final RegexTree noPattern;

  public ConditionalSubpatternTree(RegexSource source, SourceCharacter openingParen, SourceCharacter closingParen, GroupTree condition,
    RegexTree yesPattern, FlagSet activeFlags) {
    this(source, openingParen, closingParen, condition, yesPattern, null, null, activeFlags);
  }

  public ConditionalSubpatternTree(RegexSource source, SourceCharacter openingParen, SourceCharacter closingParen, GroupTree condition,
    RegexTree yesPattern, @Nullable SourceCharacter pipe, @Nullable RegexTree noPattern, FlagSet activeFlags) {
    this(source, openingParen.getRange().merge(closingParen.getRange()), condition, yesPattern, pipe, noPattern, activeFlags);
  }

  public ConditionalSubpatternTree(RegexSource source, IndexRange range, GroupTree condition, RegexTree yesPattern, @Nullable SourceCharacter pipe,
    @Nullable RegexTree noPattern, FlagSet activeFlags) {
    super(source, Kind.CONDITIONAL_SUBPATTERNS, null, range, activeFlags);
    this.condition = condition;
    this.yesPattern = yesPattern;
    this.pipe = pipe;
    this.noPattern = noPattern;

    EndOfConditionalSubpatternsState continuation = new EndOfConditionalSubpatternsState(this, activeFlags);
    yesPattern.setContinuation(continuation);
    if (noPattern != null) {
      noPattern.setContinuation(continuation);
    }
  }

  @Override
  protected void setContinuation(AutomatonState continuation, @Nullable RegexTree element) {
    super.setContinuation(continuation, element);
    condition.setContinuation(new BranchState(this,
      Arrays.asList(yesPattern, noPattern == null ? continuation : noPattern), activeFlags()));
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitConditionalSubpattern(this);
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  public GroupTree getCondition() {
    return condition;
  }

  public RegexTree getYesPattern() {
    return yesPattern;
  }

  @Nullable
  public SourceCharacter getPipe() {
    return pipe;
  }

  @Nullable
  public RegexTree getNoPattern() {
    return noPattern;
  }
}
