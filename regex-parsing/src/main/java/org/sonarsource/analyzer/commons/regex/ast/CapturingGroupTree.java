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

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CapturingGroupTree extends GroupTree {

  @Nullable
  private final String name;
  private final int groupNumber;

  public CapturingGroupTree(RegexSource source, IndexRange range, @Nullable String name, int groupNumber, RegexTree element, FlagSet activeFlags) {
    super(source, Kind.CAPTURING_GROUP, element, range, activeFlags);
    this.name = name;
    this.groupNumber = groupNumber;
    element.setContinuation(new EndOfCapturingGroupState(this, activeFlags));
  }

  @Override
  public void setContinuation(AutomatonState continuation) {
    setContinuation(continuation, null);
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitCapturingGroup(this);
  }

  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  public int getGroupNumber() {
    return groupNumber;
  }

  @Nonnull
  @Override
  public RegexTree getElement() {
    return element;
  }
}
