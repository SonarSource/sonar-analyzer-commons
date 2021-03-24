/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class CharacterClassIntersectionTree extends AbstractRegexSyntaxElement implements CharacterClassElementTree {

  private final List<CharacterClassElementTree> characterClasses;

  private final List<RegexToken> andOperators;

  private final FlagSet activeFlags;

  public CharacterClassIntersectionTree(RegexSource source, IndexRange range, List<CharacterClassElementTree> characterClasses,
    List<RegexToken> andOperators, FlagSet activeFlags) {
    super(source, range);
    this.characterClasses = Collections.unmodifiableList(characterClasses);
    this.andOperators = Collections.unmodifiableList(andOperators);
    this.activeFlags = activeFlags;
  }

  public List<CharacterClassElementTree> getCharacterClasses() {
    return characterClasses;
  }

  public List<RegexToken> getAndOperators() {
    return andOperators;
  }

  @Override
  public void accept(RegexVisitor visitor) {
    visitor.visitCharacterClassIntersection(this);
  }

  @Nonnull
  @Override
  public Kind characterClassElementKind() {
    return Kind.INTERSECTION;
  }

  @Nonnull
  @Override
  public FlagSet activeFlags() {
    return activeFlags;
  }

}