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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlFile {

  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
  private static final String ELEMENT = "element";

  public enum Location {
    NODE,
    START,
    END,
    NAME,
    VALUE
  }

  private InputFile inputFile;
  private Document documentNamespaceAware;
  // set lazely in getDocument when called with "false" argument
  private Document documentNamespaceUnaware;
  private String contents;
  private Charset charset;

  void setDocument(Document document, boolean namespaceAware) {
    if (namespaceAware) {
      documentNamespaceAware = document;
    } else {
      documentNamespaceUnaware = document;
    }
  }

  void setPrologElement(PrologElement prologElement) {
    this.prologElement = prologElement;
  }

  private PrologElement prologElement = null;

  private XmlFile(InputFile inputFile) throws IOException {
    this.inputFile = inputFile;
    this.contents = inputFile.contents();
    this.charset = inputFile.charset();
  }

  private XmlFile(String str) {
    this.inputFile = null;
    this.contents = str;
    this.charset = DEFAULT_CHARSET;
  }

  public static XmlFile create(InputFile inputFile) throws IOException {
    XmlFile xmlFile = new XmlFile(inputFile);
    new XmlParser(xmlFile, true);
    return xmlFile;
  }

  public static XmlFile create(String str) {
    XmlFile xmlFile = new XmlFile(str);
    new XmlParser(xmlFile, true);
    return xmlFile;
  }

  /**
   * @return null when created based on string
   */
  @Nullable
  public InputFile getInputFile() {
    return inputFile;
  }

  public String getContents() {
    return contents;
  }

  public Charset getCharset() {
    return charset;
  }

  /**
   * @return document with namespace information
   */
  public Document getDocument() {
    return getNamespaceAwareDocument();
  }

  public Document getNamespaceAwareDocument() {
    return documentNamespaceAware;
  }

  public Document getNamespaceUnawareDocument() {
    if (documentNamespaceUnaware == null) {
      new XmlParser(this, false);
    }

    return documentNamespaceUnaware;
  }

  public Optional<PrologElement> getPrologElement() {
    return Optional.ofNullable(prologElement);
  }

  public static XmlTextRange startLocation(CDATASection node) {
    return getRangeOrThrow(node, Location.START, "CDATA");
  }

  public static XmlTextRange endLocation(CDATASection node) {
    return getRangeOrThrow(node, Location.END, "CDATA");
  }

  public static XmlTextRange startLocation(Element node) {
    return getRangeOrThrow(node, Location.START, ELEMENT);
  }

  public static XmlTextRange endLocation(Element node) {
    return getRangeOrThrow(node, Location.END, ELEMENT);
  }

  public static XmlTextRange nameLocation(Element node) {
    return getRangeOrThrow(node, Location.NAME, ELEMENT);
  }

  public static XmlTextRange attributeNameLocation(Attr node) {
    return getRangeOrThrow(node, Location.NAME, "attribute");
  }

  public static XmlTextRange attributeValueLocation(Attr node) {
    return getRangeOrThrow(node, Location.VALUE, "attribute");
  }

  public static XmlTextRange nodeLocation(Node node) {
    return getRangeOrThrow(node, Location.NODE, "");
  }

  public static Optional<XmlTextRange> getRange(Node node, Location location) {
    return Optional.ofNullable((XmlTextRange) node.getUserData(location.name()));
  }

  private static XmlTextRange getRangeOrThrow(Node node, Location location, String nodeType) {
    return getRange(node, location)
      .orElseThrow(() -> new IllegalStateException(String.format("Missing %s location on XML %s node", location.name().toLowerCase(Locale.ENGLISH), nodeType)));
  }

  /**
   * Get all the children of a node, as a proper java list.
   *
   * @param node the node to get children from
   * @return a list of nodes, possibly empty.
   */
  public static List<Node> children(Node node) {
    return asList(node.getChildNodes());
  }

  /**
   * Transform a NodeList (from DOM interface) into a java List
   *
   * @param nodeList the nodeList to be transformed into a List, possibly null
   * @return The equivalent java list. Note that a null NodeList will produce an empty list.
   */
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

  /**
   * Try to retrieve the attribute node corresponding to an attribute name in a given node.
   *
   * @param node The node to query for an attribute
   * @param attribute The name of the attribute to search for
   * @return The corresponding attribute node, if the attribute can be found, or null if not found.
   */
  @CheckForNull
  public static Node nodeAttribute(Node node, String attribute) {
    NamedNodeMap attributes = node.getAttributes();
    if (attributes == null) {
      return null;
    }
    return attributes.getNamedItem(attribute);
  }
}
