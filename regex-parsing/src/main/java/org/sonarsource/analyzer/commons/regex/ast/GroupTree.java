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
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public abstract class GroupTree extends RegexTree {

  private final RegexTree.Kind kind;

  /**
   * Can only be null for non-capturing groups (by design).
   * If non-null - it represents an empty group iff `element` is a SequenceTree with empty `items` list.
   */
  @Nullable
  protected final RegexTree element;

  @Nullable
  private final RegexToken groupHeader;

  protected GroupTree(RegexSource source, RegexTree.Kind kind, @Nullable RegexTree element, IndexRange range, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.kind = kind;
    this.element = element;
    if (element != null) {
      this.groupHeader = new RegexToken(source, new IndexRange(range.getBeginningOffset(), element.getRange().getBeginningOffset()));
    } else {
      this.groupHeader = null;
    }
  }

  @Override
  public final RegexTree.Kind kind() {
    return kind;
  }

  /**
   * The opening sequence of the group from the ( to the :. Returns null for non-capturing groups without a colon/body.
   */
  @Nullable
  public RegexToken getGroupHeader() {
    return groupHeader;
  }

  /**
   * Can only be null for non-capturing groups
   */
  @Nullable
  public RegexTree getElement() {
    return element;
  }

  @Nonnull
  @Override
  public List<AutomatonState> successors() {
    return Collections.singletonList(element != null ? element : continuation());
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    setContinuation(continuation, element);
  }

  protected void setContinuation(AutomatonState continuation, @Nullable RegexTree element) {
    super.setContinuation(continuation);
    if (element != null) {
      element.setContinuation(continuation);
    }
  }

}
