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
package org.sonarsource.analyzer.commons.regex.python;

import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.CharacterParser;
import org.sonarsource.analyzer.commons.regex.RegexFeature;

import static org.assertj.core.api.Assertions.assertThat;

class PythonRegexSourceTest {

  @Test
  void testFeatures() {
    var source = new PythonRegexSource("") {
      @Override
      public CharacterParser createCharacterParser() {
        return null;
      }
    };

    var features = source.features();
    assertThat(features)
      .hasSize(8)
      .containsOnly(
        RegexFeature.RECURSION,
        RegexFeature.CONDITIONAL_SUBPATTERN,
        RegexFeature.PYTHON_SYNTAX_GROUP_NAME,
        RegexFeature.PYTHON_OCTAL_ESCAPE,
        RegexFeature.UNESCAPED_CURLY_BRACKET,
        RegexFeature.ONLY_UPPER_BOUND_QUANTIFIER,
        RegexFeature.POSSESSIVE_QUANTIFIER,
        RegexFeature.ATOMIC_GROUP);
  }

}
