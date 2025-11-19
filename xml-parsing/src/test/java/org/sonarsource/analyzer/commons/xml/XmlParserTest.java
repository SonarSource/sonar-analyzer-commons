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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonarsource.analyzer.commons.xml.PrologElement.PrologAttribute;
import org.sonarsource.analyzer.commons.xml.XmlFile.Location;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
  public void testLineSeprators() throws Exception {
    String testCase = ""
      /* _1 */ + "<foo%s"
      /* _2 */ + "  attr1=\"1\">%s"
      /* _3 */ + "  <!--%s"
      /* _4 */ + "      comment%s"
      /* _5 */ + "  -->%s"
      /* _6 */ + "  <bar%s"
      /* _7 */ + "    attr2=%s"
      /* _8 */ + "      \"2\"%s"
      /* _9 */ + "    attr3%s"
      /* 10 */ + "      =%s"
      /* 11 */ + "        \"3\"%s"
      /* 12 */ + "  />%s"
      /* 13 */ + "%s"
      /* 14 */ + "</foo>%s";

    String[] lf = {"\n"};
    String[] cr = {"\r"};
    String[] crlf = {"\r\n"};
    String[] mixed = {"\r", "\r\n", "\n", "\r\n"};

    for (String[] pattern : Arrays.asList(lf, cr, crlf, mixed)) {
      String xmlContent = replaceEndOfLine(testCase, pattern);
      XmlFile xmlFile = XmlFile.create(xmlContent);

      Document document = xmlFile.getDocument();
      assertRange(document, Location.NODE, 1, 0, 14, 6);

      Node foo = document.getFirstChild();
      assertRange(foo, Location.NODE, 1, 0, 14, 6);

      Attr attr1 = (Attr) foo.getAttributes().getNamedItem("attr1");
      assertRange(attr1, Location.NODE, 2, 2, 2, 11);

      NodeList fooChildren = foo.getChildNodes();

      Node comment = fooChildren.item(1);
      assertRange(comment, Location.NODE, 3, 2, 5, 5);

      Node bar = fooChildren.item(3);
      assertRange(bar, Location.NODE, 6, 2, 12, 4);

      Attr attr2 = (Attr) bar.getAttributes().getNamedItem("attr2");
      assertRange(attr2, Location.NODE, 7, 4, 8, 9);

      Attr attr3 = (Attr) bar.getAttributes().getNamedItem("attr3");
      assertRange(attr3, Location.NODE, 9, 4, 11, 11);
    }
  }

  private static String replaceEndOfLine(String content, String... replacements) {
    String result = content;
    int i = 0;
    while (result.contains("%s")) {
      result = result.replaceFirst("%s", replacements[i]);
      i++;
      i %= replacements.length;
    }
    return result;
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
  public void testAttributesLocations() throws Exception {
    String testCase = "<?xml version='1.0' encoding='UTF-8'?>\n"
      + "<data-sources>\n"
      + "  <data-source id='attr01' provider='attr02' driver='attr03' name='attr04' save-password='attr05' read-only='attr06'>\n"
      + "    <connection host='attr07' port='attr08' server='attr09' database='attr10' url='attr11' user='attr12' password='attr13' />\n"
      + "  </data-source>\n"
      + "</data-sources>\n";

    XmlFile xmlFile = XmlFile.create(testCase);

    Node connection = xmlFile.getDocument().getElementsByTagName("connection").item(0);
    assertThat(connection.hasAttributes()).isTrue();

    NamedNodeMap connectionAttributes = connection.getAttributes();
    assertThat(connectionAttributes.getLength()).isEqualTo(7);

    Node server = connectionAttributes.getNamedItem("server");
    assertThat(server).isNotNull();
    assertRange(server, Location.NODE, 4, 44, 4, 59);

    Node password = connectionAttributes.getNamedItem("password");
    assertThat(password).isNotNull();
    assertRange(password, Location.NODE, 4, 105, 4, 122);
  }

  @Test
  public void testAttributesWithNamespacesLocations() throws Exception {
    String testCase = "<?xml version='1.0' encoding='UTF-8'?>\n"
      + "<a xmlns:foo='http://www.w3.org/barfoo'\n"
      + "   xmlns:bar='http://www.w3.org/qixbar'>\n"
      + "  <foo:b foo:attr2='yolo' bar:attr1='tututte'/>\n"
      + "</a>\n";

    XmlFile file = XmlFile.create(testCase);

    Document documentNsAware = file.getNamespaceAwareDocument();
    Element a = (Element) documentNsAware.getFirstChild();
    Element b = (Element) a.getFirstChild().getNextSibling();

    assertThat(b.getNamespaceURI()).isEqualTo("http://www.w3.org/barfoo");
    assertThat(b.getNodeName()).isEqualTo("foo:b");
    assertThat(b.getLocalName()).isEqualTo("b");

    NamedNodeMap bAttributes = b.getAttributes();
    assertThat(bAttributes.getLength()).isEqualTo(2);

    Node attr1 = bAttributes.getNamedItem("bar:attr1");
    assertThat(attr1).isNotNull();
    assertRange(attr1, Location.NODE, 4, 26, 4, 45);

    Node attr2 = bAttributes.getNamedItem("foo:attr2");
    assertThat(attr2).isNotNull();
    assertRange(attr2, Location.NODE, 4, 9, 4, 25);
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
  public void testEmptyCdataPrefixedByCharacters() {
    Document document = XmlFile.create("<tag>abc<![CDATA[]]></tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 20, 1, 26);
    assertThat(topTag.getChildNodes().item(0).getTextContent()).isEqualTo("abc");
  }

  @Test
  public void testEmptyCdataFollowedByCharacters() {
    Document document = XmlFile.create("<tag><![CDATA[]]>def</tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 20, 1, 26);
    assertThat(topTag.getChildNodes().item(0).getTextContent()).isEqualTo("def");
  }

  @Test
  public void testEmptyCdataWrappedByCharacters() {
    Document document = XmlFile.create("<tag>abc<![CDATA[]]>def</tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 23, 1, 29);
    assertThat(topTag.getChildNodes().item(0).getTextContent()).isEqualTo("abcdef");
  }

  @Test
  public void testMultipleEmptyCdataWrappedByCharacters() {
    Document document = XmlFile.create("<tag>abc<![CDATA[]]><![CDATA[]]>def</tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 35, 1, 41);
    assertThat(topTag.getChildNodes().item(0).getTextContent()).isEqualTo("abcdef");
  }

  @Test
  public void testEmptyCdataFollowedByTag() {
    Document document = XmlFile.create("<tag><![CDATA[]]><int /></tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 24, 1, 30);
    assertThat(topTag.getChildNodes().item(0).getNodeName()).isEqualTo("int");
  }

  @Test
  public void testEmptyCdataPrefixedByTag() {
    Document document = XmlFile.create("<tag><int /><![CDATA[]]></tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(1);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 24, 1, 30);
    assertThat(topTag.getChildNodes().item(0).getNodeName()).isEqualTo("int");
  }

  @Test
  public void testEmptyCdataWrappedByMix() {
    Document document = XmlFile.create("<tag><int /><![CDATA[]]>def</tag>").getDocument();
    Node topTag = document.getFirstChild();
    assertThat(topTag.getChildNodes().getLength()).isEqualTo(2);
    assertRange(topTag, Location.START, 1, 0, 1, 5);
    assertRange(topTag, Location.END, 1, 27, 1, 33);
    assertThat(topTag.getChildNodes().item(0).getNodeName()).isEqualTo("int");
    assertThat(topTag.getChildNodes().item(1).getTextContent()).isEqualTo("def");
  }

  @Test
  public void testEmptyCdataInTag() {
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

  /**
   * Detailed in SONARXML-73, should be fixed with https://github.com/FasterXML/woodstox/issues/67
   */
  @Test
  public void testCommentInDoctypeProduceWrongLocations() throws Exception {
    Document document = XmlFile.create(
      "<?xml version=\"1.0\"?>\n" +
      "<!DOCTYPE menu [\n" +
      "<!--\n" +
      "Some comment\n" +
      "-->\n" +
      "<!ELEMENT menu (modulo)* >\n" +
      "]>\n" +
      "<menu value=\"foo\"></menu>").getDocument();
    assertThat(document.getChildNodes().getLength()).isEqualTo(2);
    DocumentType documentType = (DocumentType) document.getFirstChild();
    assertRange(documentType, Location.NODE, 2, 0, 7, 2);
    Node lastChild = document.getLastChild();
    // location of the node is wrong, should be line 8, events are not at the correct place
    // See https://github.com/FasterXML/woodstox/issues/67
    assertRange(lastChild, Location.NODE, 7, 0, 7, 25);

    document = XmlFile.create(
      "<?xml version=\"1.0\"?>\n" +
      "<!DOCTYPE menu [\n" +
      "<!--" + /* extra space before NewLine */ " " + "\n" +
      "Some comment\n" +
      "-->\n" +
      "<!ELEMENT menu (modulo)* >\n" +
      "]>\n" +
      "<menu value=\"foo\"></menu>").getDocument();
    assertThat(document.getChildNodes().getLength()).isEqualTo(2);
    documentType = (DocumentType) document.getFirstChild();
    assertRange(documentType, Location.NODE, 2, 0, 7, 2);
    lastChild = document.getLastChild();
    // location is correct
    assertRange(lastChild, Location.NODE, 8, 0, 8, 25);
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

  /**
   * Limitation of current parser
   * see https://jira.sonarsource.com/browse/SONARXML-62
   */
  @Test(expected = ParseException.class)
  public void testCommentedProlog() throws Exception {
   XmlFile.create("<!--<?xml version=\"1.0\"?>--><tag/>");
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

  @Test
  public void largeAttributeAreParsed() throws IOException {
    InputFile inputFile = TestInputFileBuilder
      .create("moduleKey", "longAttributes.xml")
      .setModuleBaseDir(new File("src/test/resources/").toPath())
      .setCharset(StandardCharsets.UTF_8)
      .build();

    var file = XmlFile.create(inputFile);

    assertThatNoException()
      .isThrownBy(file::getDocument);
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
