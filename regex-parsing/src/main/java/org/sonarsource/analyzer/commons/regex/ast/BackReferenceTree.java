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
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class BackReferenceTree extends RegexTree {

  private final String groupName;
  @Nullable
  private final SourceCharacter key;
  @Nullable
  private CapturingGroupTree group;

  public BackReferenceTree(RegexSource source, SourceCharacter opener, @Nullable SourceCharacter key, SourceCharacter start, SourceCharacter end, FlagSet activeFlags) {
    super(source, opener.getRange().merge(end.getRange()), activeFlags);
    this.key = key;
    char startCharacter = start.getCharacter();
    if (startCharacter != '<' && startCharacter != '\'' && startCharacter != '{' && startCharacter != '=') {
      // numerical case
      this.groupName = source.substringAt(start.getRange().merge(end.getRange()));
    } else {
      // named
      this.groupName = source.substringAt(
        new IndexRange(
          start.getRange().getBeginningOffset() + 1,
          end.getRange().getBeginningOffset()));
    }
  }

  public void setGroup(@Nullable CapturingGroupTree group) {
    this.group = group;
  }

  @Nullable
  public CapturingGroupTree group() {
    return group;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitBackReference(this);
  }

  @Override
  public RegexTree.Kind kind() {
    return RegexTree.Kind.BACK_REFERENCE;
  }

  public boolean isNamedGroup() {
    return key != null;
  }

  public boolean isNumerical() {
    return key == null;
  }

  public String groupName() {
    return groupName;
  }

  public int groupNumber() {
    if (!isNumerical()) {
      return -1;
    }
    return Integer.parseInt(groupName);
  }

  @Nonnull
  @Override
  public TransitionType incomingTransitionType() {
    return TransitionType.BACK_REFERENCE;
  }
}
