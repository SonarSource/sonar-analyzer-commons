/*
 * SonarSource Analyzers XML Parsing Test Commons
 * Copyright (C) 2009-2023 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SonarXmlCheckVerifierTest {

  @Test
  public void test() {
    SonarXmlCheckVerifier.verifyNoIssue("file.xml", new SilentTestCheck());
    SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", new FileTestCheck(), "Test file level message", 1, 2);
    SonarXmlCheckVerifier.verifyIssues("checkTestFile.xml", new TestCheck());
  }

  @Test
  public void missing_file() {
    SilentTestCheck check = new SilentTestCheck();

    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("missingFile.xml", check, "expected"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("Unable to load content of file ")
      .hasMessageEndingWith("missingFile.xml");
  }

  @Test
  public void malformed_xml() {
    SilentTestCheck check = new SilentTestCheck();

    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("malformedFile.xml", check, "expected"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("Unable to scan xml file ")
      .hasMessageEndingWith("malformedFile.xml");
  }

  @Test
  public void issues_while_not_expected() {
    TestCheck check = new TestCheck();
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyNoIssue("checkTestFile.xml", check))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected no issues, but got 3.");
  }

  @Test
  public void verifier_on_file_should_fail_if_no_issue() {
    SilentTestCheck silentTestCheck = new SilentTestCheck();
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", silentTestCheck, "Test file level message"))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected a single issue to be reported, but got 0.");
  }

  @Test
  public void verifier_on_file_should_fail_if_wrong_message() {
    FileTestCheck fileTestCheck = new FileTestCheck();
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", fileTestCheck, "wrong"))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected issue message to be \"wrong\", but got \"Test file level message\".");
  }

  @Test
  public void verifier_on_file_should_fail_if_wrong_number_of_secondaries() {
    FileTestCheck fileTestCheck = new FileTestCheck();
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", fileTestCheck, "Test file level message", 1))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected 1 secondary locations, but got 2.");
  }

  @Test
  public void verifier_on_file_should_fail_if_wrong_secondaries() {
    FileTestCheck fileTestCheck = new FileTestCheck();
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", fileTestCheck, "Test file level message", 2, 3))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected secondary locations to be [2, 3], but got [1, 2].");
  }

  @Test
  public void verifier_on_file_should_fail_if_got_issue_on_a_given_line() {
    SonarXmlCheck issueOnlineInsteadOfOnFile = new SonarXmlCheck() {
      @Override
      public void scanFile(XmlFile file) {
        reportIssue(file.getDocument().getFirstChild(), "expected");
      }
    };
    assertThatThrownBy(() -> SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", issueOnlineInsteadOfOnFile, "expected"))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Expected issue location to be null, but issue is reported on line 1.");
  }

  private static class TestCheck extends SonarXmlCheck {

    @Override
    public void scanFile(XmlFile file) {
      Element a = (Element) file.getDocument().getElementsByTagName("a").item(0);
      Element secondary1 = (Element) file.getDocument().getElementsByTagName("secondary1").item(0);
      Element secondary2 = (Element) file.getDocument().getElementsByTagName("secondary2").item(0);

      reportIssue(XmlFile.startLocation(a), "Test text range issue message with secondary",
        Lists.newArrayList(
          new Secondary(XmlFile.nodeLocation(secondary1), "secondary message"),
          new Secondary(secondary2, null)));

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
