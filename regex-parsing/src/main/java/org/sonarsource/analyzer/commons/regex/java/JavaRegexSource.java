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
package org.sonarsource.analyzer.commons.regex.java;

import java.util.EnumSet;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.CharacterParser;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class JavaRegexSource extends RegexSource {

  private static final Set<RegexFeature> FEATURES = EnumSet.of(
    RegexFeature.JAVA_SYNTAX_GROUP_NAME,
    RegexFeature.ATOMIC_GROUP,
    RegexFeature.POSSESSIVE_QUANTIFIER,
    RegexFeature.ESCAPED_CHARACTER_CLASS,
    RegexFeature.BACKSLASH_ESCAPING,
    RegexFeature.NESTED_CHARTER_CLASS
  );

  public JavaRegexSource(String sourceText) {
    super(sourceText);
  }

  @Override
  public CharacterParser createCharacterParser() {
    return new JavaCharacterParser(this);
  }

  @Override
  public Set<RegexFeature> features() {
    return FEATURES;
  }
}
