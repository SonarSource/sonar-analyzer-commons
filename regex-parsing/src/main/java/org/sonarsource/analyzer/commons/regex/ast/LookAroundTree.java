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
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class LookAroundTree extends GroupTree {

  public enum Direction {
    AHEAD, BEHIND
  }

  public enum Polarity {
    POSITIVE, NEGATIVE
  }

  private final Polarity polarity;

  private final Direction direction;

  private AutomatonState inner;

  public LookAroundTree(RegexSource source, IndexRange range, Polarity polarity, Direction direction, RegexTree element, FlagSet activeFlags) {
    super(source, RegexTree.Kind.LOOK_AROUND, element, range, activeFlags);
    this.polarity = polarity;
    this.direction = direction;
    element.setContinuation(new EndOfLookaroundState(this, activeFlags));
    inner = polarity == Polarity.NEGATIVE ? new NegationState(element, activeFlags) : element;
    if (direction == Direction.BEHIND) {
      inner = new StartOfLookBehindState(inner, activeFlags);
    }
  }

  public Polarity getPolarity() {
    return polarity;
  }

  public Direction getDirection() {
    return direction;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitLookAround(this);
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  @Nonnull
  @Override
  public RegexTree getElement() {
    return element;
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    setContinuation(continuation, null);
  }

  @Nonnull
  @Override
  public List<AutomatonState> successors() {
    return Arrays.asList(inner, continuation());
  }

  public static LookAroundTree positiveLookAhead(RegexSource source, IndexRange range, RegexTree element, FlagSet activeFlags) {
    return new LookAroundTree(source, range, Polarity.POSITIVE, Direction.AHEAD, element, activeFlags);
  }

  public static LookAroundTree negativeLookAhead(RegexSource source, IndexRange range, RegexTree element, FlagSet activeFlags) {
    return new LookAroundTree(source, range, Polarity.NEGATIVE, Direction.AHEAD, element, activeFlags);
  }

  public static LookAroundTree positiveLookBehind(RegexSource source, IndexRange range, RegexTree element, FlagSet activeFlags) {
    return new LookAroundTree(source, range, Polarity.POSITIVE, Direction.BEHIND, element, activeFlags);
  }

  public static LookAroundTree negativeLookBehind(RegexSource source, IndexRange range, RegexTree element, FlagSet activeFlags) {
    return new LookAroundTree(source, range, Polarity.NEGATIVE, Direction.BEHIND, element, activeFlags);
  }
}
