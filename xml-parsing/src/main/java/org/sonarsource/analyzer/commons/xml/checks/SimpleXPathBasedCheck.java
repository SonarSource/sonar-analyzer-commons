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
package org.sonarsource.analyzer.commons.xml.checks;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class SimpleXPathBasedCheck extends SonarXmlCheck {

  private static final Logger LOG = Loggers.get(SimpleXPathBasedCheck.class);

  private final XPath xpath = XPathFactory.newInstance().newXPath();

  public XPathExpression getXPathExpression(String expression) {
    try {
      return xpath.compile(expression);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException(String.format("[%s] Fail to compile XPath expression '%s'.", ruleKey(), expression), e);
    }
  }

  @CheckForNull
  public NodeList evaluate(XPathExpression expression, Node node) {
    try {
      return (NodeList) expression.evaluate(node, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      if (LOG.isDebugEnabled()) {
        RuleKey ruleKey = ruleKey();
        LOG.debug(String.format("[%s] Unable to evaluate XPath expression on file %s", ruleKey, inputFile()));
        LOG.error(String.format("[%s] XPath exception:", ruleKey), e);
      }
      return null;
    }
  }

  public List<Node> evaluateAsList(XPathExpression expression, Node node) {
    return asList(evaluate(expression, node));
  }

  public static List<Node> asList(@Nullable NodeList nodeList) {
    if (nodeList == null) {
      return Collections.emptyList();
    }
    int numberResults = nodeList.getLength();
    if (numberResults == 0) {
      return Collections.emptyList();
    }
    return IntStream.range(0, numberResults)
      .mapToObj(nodeList::item)
      .collect(Collectors.toList());
  }

  @CheckForNull
  public static Node nodeAttribute(Node node, String attribute) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes == null) {
      return null;
    }
    return attributes.getNamedItem(attribute);
  }
}
