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

import org.sonarsource.analyzer.commons.regex.RegexSource;

// TODO should be merged this BackReferenceTree
public class ReferenceConditionTree extends GroupTree {

  private final String reference;

  public ReferenceConditionTree(RegexSource source, IndexRange range, String reference,  FlagSet activeFlags) {
    super(source, Kind.BACK_REFERENCE, null, range, activeFlags);
    this.reference = reference;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    // do nothing
  }

  public String getReference() {
    return reference;
  }
}
