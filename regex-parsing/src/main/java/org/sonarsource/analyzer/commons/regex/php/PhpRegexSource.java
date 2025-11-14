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
    RegexFeature.ESCAPED_CHARACTER_CLASS,
    RegexFeature.UNESCAPED_CURLY_BRACKET,
    RegexFeature.PHP_BINARY_ZERO
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
