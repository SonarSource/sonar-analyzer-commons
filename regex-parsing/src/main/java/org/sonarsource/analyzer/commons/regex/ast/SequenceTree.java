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
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class SequenceTree extends RegexTree {

  private final List<RegexTree> items;

  public SequenceTree(RegexSource source, IndexRange range, List<RegexTree> items, FlagSet activeFlags) {
    super(source, range, activeFlags);
    this.items = items;
    for (int i = 0; i < items.size() - 1; i++) {
      items.get(i).setContinuation(items.get(i + 1));
    }
  }

  public List<RegexTree> getItems() {
    return Collections.unmodifiableList(items);
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitSequence(this);
  }

  @Override
  public Kind kind() {
    return Kind.SEQUENCE;
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.EPSILON;
  }

  @Nonnull
  @Override
  public List<AutomatonState> successors() {
    if (items.isEmpty()) {
      return Collections.singletonList(continuation());
    } else {
      return Collections.singletonList(items.get(0));
    }
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    super.setContinuation(continuation);
    if (!items.isEmpty()) {
      items.get(items.size() - 1).setContinuation(continuation);
    }
  }
}
