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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLInputFactory;

public class SafetyFactory {

  private SafetyFactory(){
    // class with static methods only
  }

  /**
   * @deprecated Use {@link SafeStaxParserFactory#createXMLInputFactory()} instead.
   */
  @Deprecated
  public static XMLInputFactory createXMLInputFactory() {
    return SafeStaxParserFactory.createXMLInputFactory();
  }

  /**
   * @deprecated Use {@link SafeDomParserFactory#createDocumentBuilder(boolean)} instead.
   */
  @Deprecated
  public static DocumentBuilder createDocumentBuilder(boolean namespaceAware) {
    return SafeDomParserFactory.createDocumentBuilder(namespaceAware);
  }

}
