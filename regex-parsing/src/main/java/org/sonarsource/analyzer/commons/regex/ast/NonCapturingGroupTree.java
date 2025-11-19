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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class NonCapturingGroupTree extends GroupTree {

  private final FlagSet enabledFlags;

  private final FlagSet disabledFlags;

  public NonCapturingGroupTree(
    RegexSource source,
    IndexRange range,
    FlagSet enabledFlags,
    FlagSet disabledFlags,
    @Nullable RegexTree element,
    FlagSet activeFlags
  ) {
    super(source, RegexTree.Kind.NON_CAPTURING_GROUP, element, range, activeFlags);
    this.enabledFlags = enabledFlags;
    this.disabledFlags = disabledFlags;
  }

  @Override
  @CheckForNull
  public RegexTree getElement() {
    return super.getElement();
  }

  public FlagSet getEnabledFlags() {
    return enabledFlags;
  }

  public FlagSet getDisabledFlags() {
    return disabledFlags;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitNonCapturingGroup(this);
  }
}
