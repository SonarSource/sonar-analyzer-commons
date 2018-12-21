/*
 * Sonar Analyzers XML Parsing Commons
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.analyzer.commons.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;

public class SafetyFactory {

  private SafetyFactory(){
    // class with static methods only
  }

  public static XMLInputFactory createXMLInputFactory() {
    // forcing the XMLInputFactory implementation class, in order to be sure that we are going to use the adequate
    // stream reader while retrieving locations
    XMLInputFactory factory = new com.ctc.wstx.stax.WstxInputFactory();

    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
    factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    return factory;
  }

  public static DocumentBuilder createDocumentBuilder(boolean namespaceAware) {
    try {
      // forcing the DocumentBuilderFactory implementation class, in order to be sure that we are going to use the
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
    }
  }

}
