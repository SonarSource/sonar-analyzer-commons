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
package org.sonarsource.analyzer.commons.regex.php;

import java.util.EnumSet;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.CharacterParser;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class PhpRegexSource extends RegexSource {

  private static final Set<RegexFeature> FEATURES = EnumSet.of(
    RegexFeature.RECURSION,
    RegexFeature.CONDITIONAL_SUBPATTERN,
    RegexFeature.POSIX_CHARACTER_CLASS,
    RegexFeature.JAVA_SYNTAX_GROUP_NAME,
    RegexFeature.DOTNET_SYNTAX_GROUP_NAME,
    RegexFeature.PERL_SYNTAX_GROUP_NAME,
    RegexFeature.PYTHON_SYNTAX_GROUP_NAME,
    RegexFeature.ATOMIC_GROUP,
    RegexFeature.POSSESSIVE_QUANTIFIER,
    RegexFeature.ESCAPED_CHARACTER_CLASS
  );
  private final char quote;

  public PhpRegexSource(String source, char quote) {
    super(source);
    this.quote = quote;
  }

  @Override
  public CharacterParser createCharacterParser() {
    if (quote == '\'') {
      return PhpStringCharacterParser.forSingleQuotedString(this);
    }
    return PhpStringCharacterParser.forDoubleQuotedString(this);
  }

  @Override
  public Set<RegexFeature> features() {
    return FEATURES;
  }
}
