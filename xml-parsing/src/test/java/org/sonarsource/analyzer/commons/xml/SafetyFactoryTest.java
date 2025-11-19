/*
 * SonarSource Analyzers XML Parsing Commons
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
package org.sonarsource.analyzer.commons.xml;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SafetyFactoryTest {

  @Test
  public void test_createXMLInputFactory() {
    assertThat(SafetyFactory.createXMLInputFactory()).isNotNull();
  }

  @Test
  public void test_createDocumentBuilder() {
    assertThat(SafetyFactory.createDocumentBuilder(true)).isNotNull();
    assertThat(SafetyFactory.createDocumentBuilder(false)).isNotNull();
  }

}
