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

import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParseResult;

class OpeningQuoteTest {

  @Test
  void testLocation() {
    RegexParseResult result = assertSuccessfulParseResult("abc");
    assertEquals(new IndexRange(-1, 0), result.openingQuote().getRange());
  }

  @Test
  void testGetTextException() {
    RegexParseResult result = assertSuccessfulParseResult("abc");
    RegexSyntaxElement openingQuote = result.openingQuote();
    assertThrows(UnsupportedOperationException.class, openingQuote::getText);
  }

}
