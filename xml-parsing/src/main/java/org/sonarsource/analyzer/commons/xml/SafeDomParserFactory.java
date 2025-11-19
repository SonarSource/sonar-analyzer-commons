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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class SafeDomParserFactory {

  private SafeDomParserFactory() {
    // class with static methods only
  }

  public static DocumentBuilder createDocumentBuilder(boolean namespaceAware) {
    ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      // Force a new classloader during initialization to be sure that we are going to use the classloader containing
      // all the Xerces-related classes during instantiation of the XML parser
      Thread.currentThread().setContextClassLoader(SafetyFactory.class.getClassLoader());

      // Forcing the DocumentBuilderFactory implementation class, in order to be sure that we are going to use the
      // adequate parser, handling correctly all the elements
      DocumentBuilderFactory documentBuilderFactory = new org.apache.xerces.jaxp.DocumentBuilderFactoryImpl();

      documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      documentBuilderFactory.setFeature("http://apache.org/xml/features/dom/create-entity-ref-nodes", false);
      documentBuilderFactory.setValidating(false);
      documentBuilderFactory.setExpandEntityReferences(false);
      documentBuilderFactory.setNamespaceAware(namespaceAware);
      documentBuilderFactory.setXIncludeAware(false);
      DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

      // Implementations of DocumentBuilder usually provide Error Handlers, which may add some extra logic, such as logging.
      // This line disable these custom handlers during parsing, as we don't need it
      documentBuilder.setErrorHandler(null);
      return documentBuilder;
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    } finally {
      // Set back the classloader in order to retrieve the previous state
      Thread.currentThread().setContextClassLoader(currentClassLoader);
    }
  }

}
