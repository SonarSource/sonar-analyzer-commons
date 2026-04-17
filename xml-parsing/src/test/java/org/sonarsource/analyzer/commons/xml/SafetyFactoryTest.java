/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
