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

import java.util.Arrays;
import org.junit.Test;
import org.sonarsource.analyzer.commons.xml.PrologElement.PrologAttribute;
import org.sonarsource.analyzer.commons.xml.XmlFile.Location;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlParserTest {

  @Test
  public void testSimple() throws Exception {
    XmlFile xmlFile = XmlFile.create("<foo attr=\"1\">\n </foo>");

    Document document = xmlFile.getDocument();
    assertRange(document, Location.NODE, 1, 0, 2, 7);

    Node firstChild = document.getFirstChild();
    assertThat(firstChild.getNodeName()).isEqualTo("foo");
    assertRange(firstChild, Location.START, 1, 0, 1, 14);
    assertRange(firstChild, Location.END, 2, 1, 2, 7);
    assertRange(firstChild, Location.NODE, 1, 0, 2, 7);
    assertRange(firstChild, Location.NAME, 1, 1, 1, 4);
    assertNoData(firstChild, Location.VALUE);


    Attr attr = (Attr) firstChild.getAttributes().getNamedItem("attr");
    assertThat(attr.getValue()).isEqualTo("1");
    assertRange(attr, Location.NAME, 1, 5, 1, 9);
    assertRange(attr, Location.VALUE, 1, 10, 1, 13);
    assertRange(attr, Location.NODE, 1, 5, 1, 13);
    assertNoData(attr, Location.START, Location.END);

    assertThat(xmlFile.getPrologElement()).isEmpty();
  }

  @Test
  public void testSelfClosing() throws Exception {
    Document document = XmlFile.create("<foo />").getDocument();
    Node firstChild = document.getFirstChild();
    assertThat(firstChild.getNodeName()).isEqualTo("foo");
    assertRange(firstChild, Location.NAME, 1, 1, 1, 4);
    assertRange(firstChild, Location.NODE, 1, 0, 1, 7);
    assertRange(firstChild, Location.START, 1, 0, 1, 7);
    assertRange(firstChild, Location.END, 1, 0, 1, 7);
  }

  @Test
  public void testLongText() throws Exception{
    StringBuilder sb = new StringBuilder();
    int length = 200;
    for (int i = 0; i < length; i++) {
      sb.append("a");
    }
    String bigString = sb.toString();

    Document document;
    document = XmlFile.create("<tag>" + bigString + "</tag>").getDocument();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, length + 11);
    assertRange(document.getFirstChild().getFirstChild(), Location.NODE, 1, 5, 1, length + 5);

    document = XmlFile.create("<tag attr=\"" + bigString + "\"></tag>").getDocument();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, length + 19);
    assertRange(document.getFirstChild().getAttributes().item(0), Location.VALUE, 1, 10, 1, length + 12);

    document = XmlFile.create("<tag>ab<![CDATA[" + bigString + "]]></tag>").getDocument();
    Node cdata = document.getFirstChild().getFirstChild().getNextSibling();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, length + 25);
    assertRange(cdata, Location.START, 1, 7, 1, 16);
    assertRange(cdata, Location.NODE, 1, 7, 1, length + 19);
  }

  @Test
  public void testText() throws Exception {
    Document document = XmlFile.create("<foo>Hello, \nworld</foo>\n" +
      "").getDocument();
    Node text = document.getFirstChild().getFirstChild();
    assertThat(text.getNodeName()).isEqualTo("#text");
    assertThat(text.getNodeValue()).isEqualTo("Hello, \nworld");
    assertRange(text, Location.NODE, 1, 5, 2, 5);
    assertNoData(text, Location.START, Location.END, Location.NAME, Location.VALUE);
  }

  @Test
  public void testEntityReference() throws Exception {
    // standard XML entity
    Document document = XmlFile.create("<a>&lt;</a>").getDocument();
    Node textNode = document.getFirstChild().getFirstChild();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, 11);
    assertThat(textNode.getTextContent()).isEqualTo("<");

    // numeric entity
    document = XmlFile.create("<a>&#931;</a>").getDocument();
    textNode = document.getFirstChild().getFirstChild();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, 13);
    assertThat(textNode.getTextContent()).isEqualTo("Σ");
  }

  @Test(expected = ParseException.class)
  public void testFailingNonBuiltinEntity() throws Exception {
    XmlFile.create("<a>&ouml;</a>");
  }

  @Test
  public void testInternalEntityReference() throws Exception {
    Document document = XmlFile.create(
      "<!DOCTYPE element [<!ENTITY abc \"abcValue\">]>\n" +
        "<element>Before&abc;After</element>").getDocument();

    EntityReference entityReference = ((EntityReference) document.getElementsByTagName("element").item(0).getFirstChild().getNextSibling());
    assertThat(entityReference.getNodeType()).isEqualTo(Node.ENTITY_REFERENCE_NODE);
    assertRange(entityReference, Location.NODE, 2, 15, 2, 20);

    Node textNode = entityReference.getFirstChild();
    assertThat(textNode).isInstanceOf(Text.class);
    assertThat(textNode.getTextContent()).isEqualTo("abcValue");
    assertRange(textNode, Location.NODE, 2, 15, 2, 20);
  }

  @Test
  public void testDoctypeElement() throws Exception {
    Document document = XmlFile.create(
      "<!DOCTYPE note [\n" +
        "<!ELEMENT note (body)>\n" +
        "<!ELEMENT body (#PCDATA)>\n" +
        "]>\n" +
        "<note>\n" +
        "<body>Don't forget me this weekend</body>\n" +
        "</note>").getDocument();

    DocumentType doctypeElement = (DocumentType) document.getFirstChild();
    assertThat(doctypeElement.getNodeType()).isEqualTo(Node.DOCUMENT_TYPE_NODE);
    assertRange(doctypeElement, Location.NODE, 1, 0, 4, 2);
  }

  @Test
  public void testAttributeWithBracket() throws Exception {
    Document document = XmlFile.create("<a attr='>'></a>").getDocument();
    assertRange(document.getFirstChild(), Location.START, 1, 0, 1, 12);

    document = XmlFile.create("<a attr='>'/>").getDocument();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, 13);

    document = XmlFile.create("<a attr='>\"'/>").getDocument();
    assertRange(document.getFirstChild(), Location.NODE, 1, 0, 1, 14);
  }

  @Test
  public void testXmlStylesheet() throws Exception {
    Document document = XmlFile.create("<?xml-stylesheet type='text/xsl' href='http://www.foo.con/stylus.xslt' ?><a/>").getDocument();
    ProcessingInstruction processingInstruction = (ProcessingInstruction) document.getFirstChild();
    assertRange(processingInstruction, Location.NODE, 1, 0, 1, 73);
    assertNoData(processingInstruction, Location.START, Location.END, Location.NAME);
  }

  @Test
  public void testTextWithSibling() throws Exception {
    Document document = XmlFile.create("<a> <b foo=\"1\" bar=\"2\" /></a>").getDocument();
    Node text = document.getFirstChild().getFirstChild();
    assertThat(text.getNodeValue()).isEqualTo(" ");
    assertRange(text, Location.NODE, 1, 3, 1, 4);
    assertNoData(text, Location.START, Location.END, Location.NAME, Location.VALUE);
    assertThat(document.getFirstChild().getLastChild().getNodeName()).isEqualTo("b");
  }

  @Test
  public void testComplexTree() throws Exception {
    Document nested = XmlFile.create(
      "<a>1\n"
        + "  <b>2\n"
        + "    <c>3</c>\n"
        + "  4</b>\n"
        + "5</a>")
      .getDocument();
    // c
    assertRange(nested.getElementsByTagName("c").item(0), Location.NODE, 3, 4, 3, 12);

    Document twoSiblings = XmlFile.create(
      "<a>1\n"
        + "  <b>2</b>\n"
        + "  <c>3</c>\n"
        + "4</a>")
      .getDocument();
    // c
    assertRange(twoSiblings.getElementsByTagName("c").item(0), Location.NODE, 3, 2, 3, 10);
  }

  @Test
  public void testCdata() throws Exception {
    Document document = XmlFile.create("<tag><![CDATA[<tag/><!-- Comment -->]]></tag>").getDocument();
    Node topTag = document.getFirstChild();
    CDATASection cdata = ((CDATASection) topTag.getChildNodes().item(0));
    assertThat(cdata.getData()).isEqualTo("<tag/><!-- Comment -->");
    assertRange(cdata, Location.START, 1, 5, 1, 14);
    assertRange(cdata, Location.END, 1, 36, 1, 39);
    assertRange(cdata, Location.NODE, 1, 5, 1, 39);

    assertNoData(cdata, Location.NAME, Location.VALUE);
  }

  @Test
  public void testEmptyCdataIsMissingInDocument() throws Exception {
    Document document = XmlFile.create("<tag><![CDATA[]]></tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isZero();
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 17, 1, 23);
  }

  @Test
  public void testCdataWithText() throws Exception {
    Document document = XmlFile.create("<tag>Text<![CDATA[<tag/><!-- Comment -->]]></tag>").getDocument();
    Node topTag = document.getFirstChild();
    CDATASection cdata = ((CDATASection) topTag.getChildNodes().item(1));
    assertThat(cdata.getData()).isEqualTo("<tag/><!-- Comment -->");
    assertRange(cdata, Location.START, 1, 9, 1, 18);
    assertRange(cdata, Location.END, 1, 40, 1, 43);
    assertRange(cdata, Location.NODE, 1, 9, 1, 43);
  }

  @Test
  public void testDtd() throws Exception {
    Document document = XmlFile.create("<!DOCTYPE foo [<!ENTITY bar \"&#xA0;\">  ]> <tag/>").getDocument();
    DocumentType docType = ((DocumentType) document.getFirstChild());
    Node tag = docType.getNextSibling();

    assertThat(tag.getNodeName()).isEqualTo("tag");
    assertThat(docType.getNodeName()).isEqualTo("foo");
    assertRange(docType, Location.NODE, 1, 0, 1, 41);
    assertNoData(docType, Location.START, Location.END, Location.NAME, Location.VALUE);
  }

  @Test
  public void testComment() throws Exception {
    Document document = XmlFile.create("<!-- comment --><tag/>").getDocument();
    Comment comment = ((Comment) document.getFirstChild());
    Node tag = comment.getNextSibling();

    assertThat(tag.getNodeName()).isEqualTo("tag");
    assertThat(comment.getData()).isEqualTo(" comment ");

    assertRange(comment, Location.NODE, 1, 0, 1, 16);
    assertNoData(comment, Location.START, Location.END, Location.NAME, Location.VALUE);
  }

  @Test
  public void testProlog() throws Exception {
    XmlFile file = XmlFile.create("<?xml version=\"1.0\"?><tag/>");
    Document document = file.getDocument();
    PrologElement prologElement = file.getPrologElement().get();

    Node tag = document.getFirstChild();
    assertThat(tag.getNodeName()).isEqualTo("tag");

    assertRange(prologElement.getPrologStartLocation(), 1, 0, 1, 5);
    assertRange(prologElement.getPrologEndLocation(), 1, 19, 1, 21);
    assertThat(prologElement.getAttributes()).hasSize(1);
    PrologAttribute attribute = prologElement.getAttributes().get(0);
    assertThat(attribute.getName()).isEqualTo("version");
    assertThat(attribute.getValue()).isEqualTo("1.0");

    assertRange(attribute.getNameLocation(), 1, 6, 1, 13);
    assertRange(attribute.getValueLocation(), 1,  14, 1, 19);
  }

  @Test
  public void testPrologWithCharsAndLineBefore() throws Exception {
    XmlFile file = XmlFile.create("\n  <?xml version=\"1.0\"?><tag/>");
    PrologElement prologElement = file.getPrologElement().get();

    assertRange(prologElement.getPrologStartLocation(), 2, 2, 2, 7);
  }

  @Test
  public void testNesting() throws Exception {
    Document document = XmlFile.create("<a><b/><c /><d></d></a>").getDocument();
    Node a = document.getFirstChild();
    assertThat(a.getNodeName()).isEqualTo("a");
    assertRange(a, Location.NODE, 1, 0, 1, 23);

    NodeList aChildren = a.getChildNodes();
    assertThat(aChildren.getLength()).isEqualTo(3);

    Node b = aChildren.item(0);
    Node c = aChildren.item(1);
    Node d = aChildren.item(2);

    assertThat(b.getNodeName()).isEqualTo("b");
    assertRange(b, Location.NODE, 1, 3, 1, 7);

    assertThat(c.getNodeName()).isEqualTo("c");
    assertRange(c, Location.NODE, 1, 7, 1, 12);

    assertThat(d.getNodeName()).isEqualTo("d");
    assertRange(d, Location.NODE, 1, 12, 1, 19);
  }

  @Test
  public void testNamespace() throws Exception {
    XmlFile file = XmlFile.create("<a xmlns:foo=\"http://www.w3.org/foobar\"><foo:b/></a>");
    Document documentNsAware = file.getNamespaceAwareDocument();
    Element a = (Element) documentNsAware.getFirstChild();
    Element b = (Element) a.getFirstChild();

    assertThat(a.getNamespaceURI()).isNull();
    assertThat(b.getNamespaceURI()).isEqualTo("http://www.w3.org/foobar");

    assertThat(a.getNodeName()).isEqualTo("a");
    assertThat(b.getNodeName()).isEqualTo("foo:b");

    assertThat(a.getLocalName()).isEqualTo("a");
    assertThat(b.getLocalName()).isEqualTo("b");

    assertRange(b, Location.NODE, 1, 40, 1, 48);

    Document documentNsUnaware = file.getNamespaceUnawareDocument();
    a = (Element) documentNsUnaware.getFirstChild();
    b = (Element) a.getFirstChild();

    assertThat(a.getNamespaceURI()).isNull();
    assertThat(b.getNamespaceURI()).isNull();

    assertThat(a.getNodeName()).isEqualTo("a");
    assertThat(b.getNodeName()).isEqualTo("foo:b");

    assertThat(a.getLocalName()).isNull();
    assertThat(b.getLocalName()).isNull();

    assertRange(b, Location.NODE, 1, 40, 1, 48);
  }

  @Test
  public void testDefaultNamespace() throws Exception {
    XmlFile file = XmlFile.create("<a xmlns=\"http://www.w3.org/foobar\"><b/></a>");
    Document documentNsAware = file.getNamespaceAwareDocument();
    Element a = (Element) documentNsAware.getFirstChild();
    Element b = (Element) a.getFirstChild();

    assertThat(a.getNamespaceURI()).isEqualTo("http://www.w3.org/foobar");
    assertThat(b.getNamespaceURI()).isEqualTo("http://www.w3.org/foobar");

    assertThat(a.getNodeName()).isEqualTo("a");
    assertThat(b.getNodeName()).isEqualTo("b");

    assertThat(a.getLocalName()).isEqualTo("a");
    assertThat(b.getLocalName()).isEqualTo("b");

    assertRange(b, Location.NODE, 1, 36, 1, 40);

    Document documentNsUnaware = file.getNamespaceUnawareDocument();
    a = (Element) documentNsUnaware.getFirstChild();
    b = (Element) a.getFirstChild();

    assertThat(a.getNamespaceURI()).isNull();
    assertThat(b.getNamespaceURI()).isNull();

    assertThat(a.getNodeName()).isEqualTo("a");
    assertThat(b.getNodeName()).isEqualTo("b");

    assertThat(a.getLocalName()).isNull();
    assertThat(b.getLocalName()).isNull();

    assertRange(b, Location.NODE, 1, 36, 1, 40);
  }

  @Test
  public void testNoNamespace() throws Exception {
    Document document = XmlFile.create("<a><b/></a>").getDocument();
    Element a = (Element) document.getFirstChild();
    Element b = (Element) a.getFirstChild();

    assertThat(a.getNamespaceURI()).isNull();
    assertThat(b.getNamespaceURI()).isNull();

    assertThat(a.getNodeName()).isEqualTo("a");
    assertThat(b.getNodeName()).isEqualTo("b");

    assertThat(a.getLocalName()).isEqualTo("a");
    assertThat(b.getLocalName()).isEqualTo("b");

    assertRange(b, Location.NODE, 1, 3, 1, 7);
  }

  @Test
  public void testBOM() throws Exception {
    Document document = XmlFile.create("\ufeff<a><b/></a>").getDocument();
    assertRange(document, Location.NODE, 1, 0, 1, 11);
  }

  private void assertRange(Node node, Location locationKind, int startLine, int startColumn, int endLine, int endColumn) {
    XmlTextRange textRange = ((XmlTextRange) node.getUserData(locationKind.name()));
    assertRange(textRange, startLine, startColumn, endLine, endColumn);
  }

  private void assertRange(XmlTextRange textRange, int startLine, int startColumn, int endLine, int endColumn) {
    assertThat(textRange.getStartLine()).as("start line").isEqualTo(startLine);
    assertThat(textRange.getStartColumn()).as("start column").isEqualTo(startColumn);
    assertThat(textRange.getEndLine()).as("end line").isEqualTo(endLine);
    assertThat(textRange.getEndColumn()).as("end column").isEqualTo(endColumn);
  }

  private void assertNoData(Node node, Location... locations) {
    Arrays.stream(locations)
      .forEach(l ->
        assertThat(node.getUserData(l.name())).as(l + " user data not expected").isNull());
  }
}
