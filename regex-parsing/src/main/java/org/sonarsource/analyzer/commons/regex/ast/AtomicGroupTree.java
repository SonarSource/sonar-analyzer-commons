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
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class AtomicGroupTree extends GroupTree {

  public AtomicGroupTree(RegexSource source, IndexRange range, RegexTree element, FlagSet activeFlags) {
    super(source, RegexTree.Kind.ATOMIC_GROUP, element, range, activeFlags);
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitAtomicGroup(this);
  }

  @Nonnull
  @Override
  public RegexTree getElement() {
    return element;
  }
}
