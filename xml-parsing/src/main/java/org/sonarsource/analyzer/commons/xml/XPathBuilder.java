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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class XPathBuilder {

  private static final XPathFactory FACTORY = XPathFactory.newInstance();

  private final String expression;
  private final XPathContext namespaceContext = new XPathContext();

  private XPathBuilder(String expression) {
    this.expression = expression;
  }

  public static XPathBuilder forExpression(String expression) {
    return new XPathBuilder(expression);
  }

  public XPathBuilder withNamespace(String prefix, String namespaceURI) {
    namespaceContext.add(prefix, namespaceURI);
    return this;
  }

  public XPathExpression build() {
    try {
      XPath xpath = FACTORY.newXPath();
      xpath.setNamespaceContext(namespaceContext);
      return xpath.compile(expression);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException("Failed to compile XPath expression [" + expression + "]: " + e.getMessage(), e);
    }
  }

  // Visible for testing
  static class XPathContext implements NamespaceContext {

    private final Map<String, String> namespaceByPrefixMap = new HashMap<>();

    // Visible for testing
    void add(String prefix, String namespaceURI) {
      namespaceByPrefixMap.put(prefix, namespaceURI);
    }

    @Override
    public String getNamespaceURI(String prefix) {
      return namespaceByPrefixMap.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(String namespaceURI) {
      throw new UnsupportedOperationException("Only provides 'getNamespaceURI(prefix)' conversion");
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
      throw new UnsupportedOperationException("Only provides 'getNamespaceURI(prefix)' conversion");
    }

  }
}
