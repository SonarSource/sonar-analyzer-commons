/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) 2009-2022 SonarSource SA
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonarsource.analyzer.commons.xml.XmlFile.Location;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlFileTest {

  private static final String FILE_CONTENT = "<a>Hello</a>";

  @Test
  public void testFromStringCreation() throws Exception {
    XmlFile xmlFile = XmlFile.create(FILE_CONTENT);

    assertThat(xmlFile.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(xmlFile.getContents()).isEqualTo(FILE_CONTENT);
    assertThat(xmlFile.getDocument().getFirstChild().getTextContent()).isEqualTo("Hello");
    assertThat(xmlFile.getInputFile()).isNull();
  }

  @Test
  public void testFromInputFileCreation() throws Exception {
    InputFile inputFile = TestInputFileBuilder
      .create("moduleKey", "file.xml")
      .setModuleBaseDir(new File("src/test/resources/").toPath())
      .setCharset(StandardCharsets.UTF_8)
      .build();

    XmlFile xmlFile = XmlFile.create(inputFile);

    assertThat(xmlFile.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(xmlFile.getContents()).isEqualTo(FILE_CONTENT + "\n");
    assertThat(xmlFile.getDocument().getFirstChild().getTextContent()).isEqualTo("Hello");
    assertThat(xmlFile.getInputFile()).isEqualTo(inputFile);
  }

  @Test
  public void testStaticMethods() throws Exception {
    XmlFile xmlFile = XmlFile.create("<a attr='foo'><![CDATA[<Hello\n>]]> <b>world</b>\n</a>");
    Element a = (Element) xmlFile.getDocument().getFirstChild();
    Attr attr = a.getAttributeNode("attr");
    List<Node> children = XmlFile.children(a);
    CDATASection cdata = (CDATASection) children.get(0);
    // CDATA, space, 'b' element, new-line
    assertThat(children).hasSize(4);

    // for every Node
    assertRange(XmlFile.nodeLocation(a)).containsExactly(1, 0, 3, 4);
    assertRange(XmlFile.nodeLocation(attr)).containsExactly(1, 3, 1, 13);
    assertRange(XmlFile.nodeLocation(cdata)).containsExactly(1, 14, 2, 4);
    assertRange(XmlFile.nodeLocation(children.get(1))).containsExactly(2, 4, 2, 5);


    // for Element
    assertRange(XmlFile.startLocation(a)).containsExactly(1, 0, 1, 14);
    assertRange(XmlFile.endLocation(a)).containsExactly(3, 0, 3, 4);
    assertRange(XmlFile.nameLocation(a)).containsExactly(1, 1, 1, 2);
    assertThat(XmlFile.getRange(a, Location.VALUE)).isEmpty();

    // for Attribute
    assertRange(XmlFile.attributeNameLocation(attr)).containsExactly(1, 3, 1, 7);
    assertRange(XmlFile.attributeValueLocation(attr)).containsExactly(1, 8, 1, 13);

    // for CData
    assertRange(XmlFile.startLocation(cdata)).containsExactly(1, 14, 1, 23);
    assertRange(XmlFile.endLocation(cdata)).containsExactly(2, 1, 2, 4);
    assertThat(XmlFile.children(cdata)).isEmpty();

    assertThat(XmlFile.asList(null)).isEmpty();
  }

  @Test
  public void testNodeAttribute() throws Exception {
    XmlFile xmlFile = XmlFile.create(
      "<a attr='foo'>\n"
        + "  <!-- comment -->\n"
        + "  <b>world</b>\n"
        + "</a>");

    Node aNode = xmlFile.getDocument().getFirstChild();
    assertThat(XmlFile.nodeAttribute(aNode, "attr")).isNotNull();
    assertThat(XmlFile.nodeAttribute(aNode, "unknown")).isNull();

    Node commentNode = aNode.getFirstChild();
    assertThat(XmlFile.nodeAttribute(commentNode, "unknown")).isNull();
  }

  private AbstractListAssert<?, List<?>, Object, ObjectAssert<Object>> assertRange(XmlTextRange textRange) {
    return assertThat(textRange).extracting("startLine", "startColumn", "endLine", "endColumn");
  }

}
