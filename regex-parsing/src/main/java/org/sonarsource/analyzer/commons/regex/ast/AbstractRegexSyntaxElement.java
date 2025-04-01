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

import org.sonarsource.analyzer.commons.regex.RegexSource;

public abstract class AbstractRegexSyntaxElement implements RegexSyntaxElement {

  private final RegexSource source;

  private final IndexRange range;

  protected AbstractRegexSyntaxElement(RegexSource source, IndexRange range) {
    this.source = source;
    this.range = range;
  }

  @Override
  public String getText() {
    return source.substringAt(range);
  }

  @Override
  public IndexRange getRange() {
    return range;
  }

  @Override
  public RegexSource getSource() {
    return source;
  }

}
