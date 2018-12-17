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

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.sonarsource.analyzer.commons.xml.XmlFile;
import org.w3c.dom.Element;

public class SonarXmlCheckTest {

  @Test
  public void test() throws Exception {
    TestCheck testCheck = new TestCheck();

    SonarXmlCheckVerifier.verifyNoIssue("file.xml", new SilentTestCheck());
    SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", new FileTestCheck(), "Test file level message", 1, 2);
    SonarXmlCheckVerifier.verifyIssues("checkTestFile.xml", testCheck);
  }

  private static class TestCheck extends SonarXmlCheck {

    @Override
    public void scanFile(XmlFile file) {
      Element a = (Element) file.getDocument().getElementsByTagName("a").item(0);
      Element secondary1 = (Element) file.getDocument().getElementsByTagName("secondary1").item(0);
      Element secondary2 = (Element) file.getDocument().getElementsByTagName("secondary2").item(0);

      reportIssue(XmlFile.startLocation(a), "Test text range issue message with secondary",
        Lists.newArrayList(
          XmlFile.nodeLocation(secondary1),
          XmlFile.nodeLocation(secondary2)));

      Element b = (Element) file.getDocument().getElementsByTagName("b").item(0);
      reportIssue(b, "Test node issue message");

      reportIssue(XmlFile.nodeLocation(a.getLastChild()), "Test text range issue message", Lists.emptyList());
    }
  }

  private static class FileTestCheck extends SonarXmlCheck {

    @Override
    public void scanFile(XmlFile file) {
      reportIssueOnFile("Test file level message", Lists.newArrayList(1, 2));
    }
  }

  private static class SilentTestCheck extends SonarXmlCheck {

    @Override
    public void scanFile(XmlFile file) {
      // do nothing
    }
  }
}
