/*
 * SonarSource Analyzers XML Parsing Commons
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
package org.sonarsource.analyzer.commons.xml.checks;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.analyzer.commons.xml.XmlFile;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class SimpleXPathBasedCheck extends SonarXmlCheck {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleXPathBasedCheck.class);

  private final XPath xpath = XPathFactory.newInstance().newXPath();

  /**
   * Compiles an XPath 1.0 expression
   *
   * @param expression The expression to be compiled in XPath, as a String
   * @return The compiled expression
   * @throws IllegalStateException When the XPath expression can not be compiled by the XPath engine.
   *         Could occur with invalid expression, or incompatible XPath version.
   */
  public XPathExpression getXPathExpression(String expression) {
    try {
      return xpath.compile(expression);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException(String.format("[%s] Fail to compile XPath expression '%s'.", ruleKey(), expression), e);
    }
  }

  /**
   * Evaluates a XPath expression on a given node from DOM. The only situation where null is returned is when XPath fails 
   * to evaluate the expression. This could occur with strangely built DOM. Note that in such case, the check will log extra 
   * information if the debug level is set.
   *
   * @param expression The XPath expression to be used, preferably compiled using {@link #getXPathExpression(String)}
   * @param node The node to use as starting point of the XPath expression
   * @return The list of nodes, possibly empty, matching the XPath expression.
   *         Note that it will return null only when XPath fails to evaluate the expression.
   */
  @CheckForNull
  public NodeList evaluate(XPathExpression expression, Node node) {
    try {
      return (NodeList) expression.evaluate(node, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      if (LOG.isDebugEnabled()) {
        LOG.error(String.format("[%s] Unable to evaluate XPath expression on file %s", ruleKey(), inputFile()), e);
      }
      return null;
    }
  }

  /**
   * Evaluates a XPath expression on a given node from DOM, returning it as a java List of Node, possibly empty.
   *
   * @param expression The XPath expression to be used, preferably compiled using {@link #getXPathExpression(String)}
   * @param node The node to use as starting point of the XPath expression
   * @return The list of nodes, possibly empty, matching the XPath expression.
   */
  public List<Node> evaluateAsList(XPathExpression expression, Node node) {
    return XmlFile.asList(evaluate(expression, node));
  }
}
