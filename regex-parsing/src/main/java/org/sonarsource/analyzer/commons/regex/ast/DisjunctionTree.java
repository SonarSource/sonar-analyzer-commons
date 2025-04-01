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

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class DisjunctionTree extends RegexTree {

  private final List<RegexTree> alternatives;

  private final List<SourceCharacter> orOperators;

  public DisjunctionTree(RegexSource source, IndexRange range, List<RegexTree> alternatives, List<SourceCharacter> orOperators, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.alternatives = Collections.unmodifiableList(alternatives);
    this.orOperators = Collections.unmodifiableList(orOperators);
  }

  public List<RegexTree> getAlternatives() {
    return alternatives;
  }

  public List<SourceCharacter> getOrOperators() {
    return orOperators;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitDisjunction(this);
  }

  @Override
  public Kind kind() {
    return Kind.DISJUNCTION;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  @Nonnull
  @Override
  public List<? extends AutomatonState> successors() {
    return alternatives;
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    super.setContinuation(continuation);
    for (RegexTree alternative : alternatives) {
      alternative.setContinuation(continuation);
    }
  }

}
