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

import javax.annotation.Nonnull;

public class StartOfLookBehindState extends ActiveFlagsState {

  private final AutomatonState content;

  public StartOfLookBehindState(AutomatonState content, FlagSet activeFlags) {
    super(activeFlags);
    this.content = content;
  }

  @Nonnull
  @Override
  public AutomatonState continuation() {
    return content;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.LOOKAROUND_BACKTRACKING;
  }

}
