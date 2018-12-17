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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
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
      Document document = getDocumentBuilder(namespaceAware).parse(stream);
      xmlFile.setDocument(document, namespaceAware);
      currentNode = document;
      nodes.push(currentNode);

      parseXmlDeclaration();
      parseXml();

      setDocumentLocation(xmlFile);

    } catch (XMLStreamException|SAXException|IOException|ParserConfigurationException e) {
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
    XMLStreamReader xmlReader = getXmlStreamReader();

    while (xmlReader.hasNext()) {
      previousEventIsText = xmlReader.getEventType() == XMLStreamConstants.CHARACTERS;
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
          if (!xmlReader.getText().isEmpty()) {
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
        && xmlReader.getEventType() != XMLStreamConstants.END_ELEMENT) {
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

  private XMLStreamReader getXmlStreamReader() throws XMLStreamException {
    Reader reader = new StringReader(content);

    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
    return factory.createXMLStreamReader(reader);
  }

  private static DocumentBuilder getDocumentBuilder(boolean namespaceAware) throws ParserConfigurationException {
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
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    // Implementations of DocumentBuilder usually provide Error Handlers, which may add some extra logic, such as logging.
    // This line disable these custom handlers during parsing, as we don't need it
    documentBuilder.setErrorHandler(null);
    return documentBuilder;
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
    int attrIndex = 0;
    XmlFilePosition currentLocation = start.moveAfterWhitespaces();

    while (currentLocation.has("=", end)) {
      XmlFilePosition attributeNameEnd = currentLocation.moveBefore("=");

      XmlFilePosition attributeValueStart = attributeNameEnd.moveAfter("=").moveAfterWhitespaces();
      char c = attributeValueStart.readChar();
      XmlFilePosition attributeValueEnd = attributeValueStart.shift(1).moveAfter(String.valueOf(c));

      Node attr = attributes.item(attrIndex);
      setLocation(attr, Location.NAME, currentLocation, attributeNameEnd);
      setLocation(attr, Location.VALUE, attributeValueStart, attributeValueEnd);
      setLocation(attr, Location.NODE, currentLocation, attributeValueEnd);

      currentLocation = attributeValueEnd.moveAfterWhitespaces();
      attrIndex++;
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
