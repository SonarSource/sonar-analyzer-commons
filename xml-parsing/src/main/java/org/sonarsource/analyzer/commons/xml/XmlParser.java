/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.sonarsource.analyzer.commons.xml.PrologElement.PrologAttribute;
import org.sonarsource.analyzer.commons.xml.XmlFile.Location;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

class XmlParser {

  private static final String BOM_CHAR = "\ufeff";
  private static final String XML_DECLARATION_TAG = "<?xml";

  private XmlFilePosition xmlFileStartLocation;
  private XmlFilePosition currentNodeStartLocation = null;
  private XmlTextRange currentNodeStartRange = null;
  private String content;

  // latest processed node
  private Node currentNode;
  private boolean currentNodeIsClosed = false;
  private boolean previousEventIsText = false;
  private Deque<Node> nodes = new LinkedList<>();
  private XmlFile xmlFile;

  XmlParser(XmlFile xmlFile, boolean namespaceAware) {
    this.xmlFile = xmlFile;
    try {
      setContent();
      ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(xmlFile.getCharset()));
      Document document = SafeDomParserFactory.createDocumentBuilder(namespaceAware).parse(stream);
      xmlFile.setDocument(document, namespaceAware);
      currentNode = document;
      nodes.push(currentNode);

      parseXmlDeclaration();
      parseXml();

      setDocumentLocation(xmlFile);

    } catch (XMLStreamException|SAXException|IOException e) {
      throw new ParseException(e);
    }
  }

  private static void setDocumentLocation(XmlFile xmlFile) {
    Document document = xmlFile.getDocument();
    XmlTextRange startRange = XmlFile.nodeLocation(document.getFirstChild());
    XmlTextRange end = XmlFile.nodeLocation(document.getLastChild());
    Optional<PrologElement> prologElement = xmlFile.getPrologElement();
    if (prologElement.isPresent()) {
      startRange = prologElement.get().getPrologStartLocation();
    }
    document.setUserData(Location.NODE.name(), new XmlTextRange(startRange, end), null);
  }

  private void setContent() throws XMLStreamException {
    String fullContent = xmlFile.getContents();

    if (fullContent.startsWith(BOM_CHAR)) {
      // remove it immediately
      fullContent = fullContent.substring(1);
    }
    int realStartIndex = fullContent.indexOf(XML_DECLARATION_TAG);

    if (realStartIndex == -1) {
      xmlFileStartLocation = new XmlFilePosition(fullContent);
      content = fullContent;
    } else {
      content = fullContent.substring(realStartIndex);
      xmlFileStartLocation = new XmlFilePosition(fullContent).moveBefore(XML_DECLARATION_TAG);
    }
  }

  private void parseXml() throws XMLStreamException {
    XMLStreamReader xmlReader = SafeStaxParserFactory.createXMLInputFactory().createXMLStreamReader(new StringReader(content));
    boolean emptyCdata = false;

    while (xmlReader.hasNext()) {
      previousEventIsText = (emptyCdata && previousEventIsText) || (xmlReader.getEventType() == XMLStreamConstants.CHARACTERS);
      emptyCdata = false;
      xmlReader.next();
      XmlFilePosition startLocation = new XmlFilePosition(content, xmlReader.getLocation());

      finalizePreviousNode(startLocation);

      switch (xmlReader.getEventType()) {
        case XMLStreamConstants.ENTITY_REFERENCE:
        case XMLStreamConstants.COMMENT:
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
          setNextNode();
          currentNodeStartLocation = startLocation;
          break;

        case XMLStreamConstants.CHARACTERS:
          visitTextNode(startLocation);
          break;

        case XMLStreamConstants.START_ELEMENT:
          visitStartElement(xmlReader, startLocation);
          break;

        case XMLStreamConstants.END_ELEMENT:
          visitEndElement(startLocation);
          break;

        case XMLStreamConstants.CDATA:
          if (xmlReader.getText().isEmpty()) {
            emptyCdata = true;
          } else {
            // Empty CDATA are not detected by the xerces DocumentBuilder
            visitCdata(startLocation);
          }
          break;

        case XMLStreamConstants.DTD:
          visitDTD(startLocation);
          break;

        default:
          break;
      }

      if (xmlReader.getEventType() != XMLStreamConstants.START_ELEMENT
        && xmlReader.getEventType() != XMLStreamConstants.END_ELEMENT
        && !emptyCdata) {
        // as no end event for non-element nodes, consider them closed
        currentNodeIsClosed = true;
      }
    }
  }

  private void visitTextNode(XmlFilePosition startLocation) {
    if (previousEventIsText) {
      // text can appear after another text when it's not coalesced (see XMLInputFactory.IS_COALESCING)
      // so both events stand for the same node in DOM
      currentNodeStartRange = XmlFile.nodeLocation(currentNode);
    } else {
      setNextNode();
      currentNodeStartLocation = startLocation;
    }
  }

  private void finalizePreviousNode(XmlFilePosition endLocation) {
    if (currentNodeStartLocation != null) {
      setLocation(currentNode, Location.NODE, currentNodeStartLocation, endLocation);
      // for entity reference having a child which is it's text replacement
      // setting the same location
      if (currentNode.getFirstChild() != null) {
        setLocation(currentNode.getFirstChild(), Location.NODE, currentNodeStartLocation, endLocation);
      }
    } else if (currentNodeStartRange != null) {
      currentNode.setUserData(Location.NODE.name(), new XmlTextRange(currentNodeStartRange, endLocation, xmlFileStartLocation), null);
    }

    currentNodeStartLocation = null;
    currentNodeStartRange = null;
  }

  private void visitStartElement(XMLStreamReader xmlReader, XmlFilePosition startLocation) throws XMLStreamException {
    setNextNode();
    nodes.push(currentNode);
    XmlFilePosition nameEndLocation = startLocation.shift(getNameWithNamespaceLength(xmlReader) + 1);
    XmlFilePosition closingBracketEndLocation = startLocation.moveAfterClosingBracket();
    setLocation(currentNode, Location.START, startLocation, closingBracketEndLocation);
    setLocation(currentNode, Location.NAME, startLocation.shift(1), nameEndLocation);
    visitAttributes(nameEndLocation, closingBracketEndLocation.moveBackward());
  }

  private void visitEndElement(XmlFilePosition startLocation) throws XMLStreamException {
    currentNode = nodes.pop();
    XmlFilePosition closingBracketEndLocation = startLocation.moveAfterClosingBracket();
    setLocation(currentNode, Location.END, startLocation, closingBracketEndLocation);
    XmlTextRange startRange = (XmlTextRange) currentNode.getUserData(Location.START.name());
    currentNode.setUserData(Location.NODE.name(), new XmlTextRange(startRange, closingBracketEndLocation, xmlFileStartLocation), null);
    currentNodeIsClosed = true;
  }

  private void setNextNode() {
    if (currentNodeIsClosed) {
      // when currentNode (last processed node) is closed, it's impossible that we visit its child
      currentNode = currentNode.getNextSibling();
    } else {
      currentNode = currentNode.getFirstChild();
    }

    currentNodeIsClosed = false;
  }

  private void parseXmlDeclaration() throws XMLStreamException {
    XmlFilePosition startLocation = new XmlFilePosition(content);
    if (startLocation.startsWith(XML_DECLARATION_TAG)) {
      XmlFilePosition endLocation =  startLocation.moveAfterClosingBracket();
      XmlFilePosition attributesStart = startLocation.moveAfter(XML_DECLARATION_TAG);

      List<PrologAttribute> prologAttributes = visitPrologAttributes(attributesStart, endLocation.moveBackward());

      xmlFile.setPrologElement(new PrologElement(
        prologAttributes,
        new XmlTextRange(startLocation, attributesStart, xmlFileStartLocation),
        new XmlTextRange(endLocation.moveBackward().moveBackward(), endLocation, xmlFileStartLocation)
      ));
    }
  }

  private void visitDTD(XmlFilePosition startLocation) throws XMLStreamException {
    setNextNode();
    XmlFilePosition endLocation = startLocation.moveAfterClosingBracket();
    setLocation(currentNode, Location.NODE, startLocation, endLocation);
  }

  private void visitCdata(XmlFilePosition startLocation) throws XMLStreamException {
    if (!startLocation.startsWith("<![CDATA[")) {
      // Ignoring secondary CDATA event
      // See https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLStreamReader.html#next()
      return;
    }
    setNextNode();

    XmlFilePosition beforeClosingTag = startLocation.moveBefore("]]>");
    XmlFilePosition endLocation = beforeClosingTag.moveAfter("]]>");
    setLocation(currentNode, Location.START, startLocation, startLocation.moveAfter("<![CDATA["));
    setLocation(currentNode, Location.END, beforeClosingTag, endLocation);
    setLocation(currentNode, Location.NODE, startLocation, endLocation);
  }

  private void setLocation(Node node, Location locationKind, XmlFilePosition start, XmlFilePosition end) {
    node.setUserData(locationKind.name(), new XmlTextRange(start, end, xmlFileStartLocation), null);
  }

  private void visitAttributes(XmlFilePosition start, XmlFilePosition end) throws XMLStreamException {
    NamedNodeMap attributes = currentNode.getAttributes();
    XmlFilePosition currentLocation = start.moveAfterWhitespaces();

    while (currentLocation.has("=", end)) {
      XmlFilePosition attributeNameEnd = currentLocation.moveBefore("=");

      XmlFilePosition attributeValueStart = attributeNameEnd.moveAfter("=").moveAfterWhitespaces();
      char c = attributeValueStart.readChar();
      XmlFilePosition attributeValueEnd = attributeValueStart.shift(1).moveAfter(String.valueOf(c));

      String attributeName = currentLocation.textUntil(attributeNameEnd).trim();
      Node attr = Objects.requireNonNull(attributes.getNamedItem(attributeName), String.format("Attribute '%s' not found.", attributeName));

      setLocation(attr, Location.NAME, currentLocation, attributeNameEnd);
      setLocation(attr, Location.VALUE, attributeValueStart, attributeValueEnd);
      setLocation(attr, Location.NODE, currentLocation, attributeValueEnd);

      currentLocation = attributeValueEnd.moveAfterWhitespaces();
    }
  }

  private List<PrologAttribute> visitPrologAttributes(XmlFilePosition start, XmlFilePosition end) throws XMLStreamException {
    XmlFilePosition currentLocation = start.moveAfterWhitespaces();
    List<PrologAttribute> attributes = new ArrayList<>();

    while (currentLocation.has("=", end)) {
      XmlFilePosition attributeNameEnd = currentLocation.moveBefore("=");

      XmlFilePosition attributeValueStart = attributeNameEnd.moveAfter("=").moveAfterWhitespaces();
      char c = attributeValueStart.readChar();
      XmlFilePosition attributeValueEnd = attributeValueStart.shift(1).moveAfter(String.valueOf(c));

      attributes.add(new PrologAttribute(
        currentLocation.textUntil(attributeNameEnd),
        new XmlTextRange(currentLocation, attributeNameEnd, xmlFileStartLocation),
        removeQuotes(attributeValueStart.textUntil(attributeValueEnd)),
        new XmlTextRange(attributeValueStart, attributeValueEnd, xmlFileStartLocation)
      ));
      currentLocation = attributeValueEnd.moveAfterWhitespaces();
    }

    return attributes;
  }

  private static String removeQuotes(String str) {
    if ((str.startsWith("\"") || str.startsWith("'")) && str.length() > 1) {
      return str.substring(1, str.length() - 1);
    }

    return str;
  }

  private static int getNameWithNamespaceLength(XMLStreamReader streamReader) {
    int prefixLength = 0;
    if (!streamReader.getName().getPrefix().isEmpty()) {
      prefixLength = streamReader.getName().getPrefix().length() + 1;
    }

    return prefixLength + streamReader.getLocalName().length();
  }
}
