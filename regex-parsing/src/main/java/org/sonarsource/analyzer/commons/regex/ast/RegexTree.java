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

import java.util.Optional;
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public abstract class RegexTree extends AbstractRegexSyntaxElement implements AutomatonState {
  public enum Kind {
    BACK_REFERENCE,
    BOUNDARY,
    CHARACTER_CLASS,
    DISJUNCTION,
    DOT,
    ESCAPED_CHARACTER_CLASS,
    CAPTURING_GROUP,
    NON_CAPTURING_GROUP,
    ATOMIC_GROUP,
    LOOK_AROUND,
    CHARACTER,
    REPETITION,
    SEQUENCE,
    MISC_ESCAPE_SEQUENCE,
    CONDITIONAL_SUBPATTERNS
  }

  private final FlagSet activeFlags;

  protected RegexTree(RegexSource source, IndexRange range, FlagSet activeFlags) {
    super(source, range);
    this.activeFlags = activeFlags;
  }

  @Nonnull
  @Override
  public FlagSet activeFlags() {
    return activeFlags;
  }

  /**
   * This method should only be called by RegexBaseVisitor (or other implementations of the RegexVisitor interface).
   * Do not call this method to invoke a visitor, use visitor.visit(tree) instead.
   */
  public abstract void accept(RegexVisitor visitor);

  public abstract Kind kind();

  public boolean is(Kind... kinds) {
    Kind thisKind = kind();
    for (Kind kind : kinds) {
      if (thisKind == kind) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Optional<RegexTree> toRegexTree() {
    return Optional.of(this);
  }

  private AutomatonState continuation;

  @Nonnull
  public AutomatonState continuation() {
    if (this.continuation == null) {
      throw new IllegalStateException("RegexTree.continuation() called before setContinuation");
    }
    return continuation;
  }

  public void setContinuation(AutomatonState continuation) {
    if (this.continuation != null) {
      throw new IllegalStateException("RegexTree.setContinuation called more than once");
    }
    this.continuation = continuation;
  }

}
