/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
