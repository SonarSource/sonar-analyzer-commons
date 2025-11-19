/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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

import java.util.List;
import javax.annotation.Nonnull;

public class BranchState extends ActiveFlagsState {

  private final RegexTree parent;

  private final List<AutomatonState> successors;

  public BranchState(RegexTree parent, List<AutomatonState> successors, FlagSet activeFlags) {
    super(activeFlags);
    this.parent = parent;
    this.successors = successors;
  }

  @Nonnull
  @Override
  public AutomatonState continuation() {
    return parent.continuation();
  }

  @Nonnull
  @Override
  public List<AutomatonState> successors() {
    return successors;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

}
