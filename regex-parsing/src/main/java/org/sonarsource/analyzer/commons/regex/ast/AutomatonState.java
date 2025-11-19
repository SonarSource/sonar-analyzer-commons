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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface AutomatonState {

  /**
   * This will only return null when called on the end-of-regex state
   */
  @Nullable
  AutomatonState continuation();

  @Nonnull
  default List<? extends AutomatonState> successors() {
    return Collections.singletonList(continuation());
  }

  default Optional<RegexTree> toRegexTree() {
    return Optional.empty();
  }

  @Nonnull
  TransitionType incomingTransitionType();

  @Nonnull
  FlagSet activeFlags();

  enum TransitionType {
    EPSILON, CHARACTER, BACK_REFERENCE, LOOKAROUND_BACKTRACKING, NEGATION
  }

}
