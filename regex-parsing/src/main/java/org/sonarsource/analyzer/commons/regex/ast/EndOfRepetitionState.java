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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class EndOfRepetitionState implements AutomatonState {

  private final RepetitionTree parent;
  private final AutomatonState continuation;

  public EndOfRepetitionState(RepetitionTree parent, AutomatonState continuation) {
    this.parent = parent;
    this.continuation = continuation;
  }

  @Nonnull
  @Override
  public FlagSet activeFlags() {
    return parent.activeFlags();
  }

  @CheckForNull
  @Override
  public AutomatonState continuation() {
    return continuation;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

}
