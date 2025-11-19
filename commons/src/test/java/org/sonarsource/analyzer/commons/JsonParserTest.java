/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons;

import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonParserTest {

  @Test
  public void parse() throws Exception {
    JsonParser parser = new JsonParser();
    Map<String, Object> map = parser.parse("{ \"name\" : \"Paul\" }");
    Object name = map.get("name");
    assertThat(name).isEqualTo("Paul");
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalid_json() {
    new JsonParser().parse("{{}");
  }
}
