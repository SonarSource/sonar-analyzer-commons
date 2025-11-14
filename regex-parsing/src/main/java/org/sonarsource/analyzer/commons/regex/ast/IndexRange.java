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

import java.util.Objects;

public class IndexRange {

  private final int beginningOffset;
  private final int endingOffset;

  public IndexRange(int beginningOffset, int endingOffset) {
    this.beginningOffset = beginningOffset;
    this.endingOffset = endingOffset;
  }

  public static IndexRange inaccessible() {
    return new IndexRange(-1, -1);
  }

  public int getBeginningOffset() {
    return beginningOffset;
  }

  public int getEndingOffset() {
    return endingOffset;
  }

  public IndexRange merge(IndexRange other) {
    return extendTo(other.endingOffset);
  }

  public IndexRange extendTo(int newEnd) {
    return new IndexRange(beginningOffset, newEnd);
  }

  public boolean contains(IndexRange other) {
    return this.beginningOffset <= other.beginningOffset && other.endingOffset <= this.endingOffset;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof IndexRange
      && beginningOffset == ((IndexRange) other).beginningOffset
      && endingOffset == ((IndexRange) other).endingOffset;
  }

  @Override
  public int hashCode() {
    return Objects.hash(beginningOffset, endingOffset);
  }

  @Override
  public String toString() {
    return beginningOffset + "-" + endingOffset;
  }

}
