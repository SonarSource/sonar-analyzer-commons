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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpression;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.analyzer.commons.xml.XmlFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleXPathBasedCheckTest {

  @Rule
  public LogTester logTester = new LogTester();

  @Test(expected = IllegalStateException.class)
  public void test_invalid_xpath_compilation_throws_exception() throws Exception {
    new SimpleXPathBasedCheck() {

      XPathExpression failing = getXPathExpression("boolean(a");

      @Override
      public void scanFile(XmlFile file) {
        // do nothing
      }
    };
  }

  @Test
  public void test_file_making_xpath_evaluation_fail_log_issues() throws Exception {
    XmlFile xmlFile = getXmlFile("src/test/resources/checks/SimpleXPathBasedCheck/xPathFailure.xml");

    XPathTesterCheck check = new XPathTesterCheck() {
      public XPathExpression comments = getXPathExpression("//comment()");

      @Override
      public void scanFile(XmlFile file) {
        NodeList result = evaluate(comments, file.getDocument());
        assertThat(result).isNull();
        isExecuted();
      }
    };

    logTester.clear();
    logTester.setLevel(LoggerLevel.INFO);
    check.scanFile(null, RuleKey.of("tst", "failingXpath"), xmlFile);
    assertThat(check.hasBeenExecuted()).isTrue();
    assertThat(logTester.logs()).isEmpty();

    logTester.clear();
    logTester.setLevel(LoggerLevel.DEBUG);
    check.scanFile(null, RuleKey.of("tst", "failingXpath"), xmlFile);
    assertThat(logTester.logs()).isNotEmpty();
    List<String> debugs = logTester.logs(LoggerLevel.DEBUG);
    assertThat(debugs).isEmpty();
    List<String> errors = logTester.logs(LoggerLevel.ERROR);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).startsWith("[tst:failingXpath] Unable to evaluate XPath expression on file ");
  }

  @Test
  public void test_nodeList() throws Exception {
    XmlFile xmlFile = getXmlFile("src/test/resources/checks/SimpleXPathBasedCheck/simple.xml");

    XPathTesterCheck check = new XPathTesterCheck() {
      XPathExpression bs = getXPathExpression("//b");
      XPathExpression ds = getXPathExpression("//d");

      @Override
      public void scanFile(XmlFile file) {
        Document node = file.getDocument();

        NodeList result = evaluate(bs, node);
        assertThat(result).isNotNull();
        assertThat(result.getLength()).isEqualTo(4);
        List<Node> asList = evaluateAsList(bs, node);
        assertThat(asList).hasSize(4);

        NodeList empty = evaluate(ds, node);
        assertThat(empty).isNotNull();
        assertThat(empty.getLength()).isEqualTo(0);
        List<Node> emptyAsList = evaluateAsList(ds, node);
        assertThat(emptyAsList).isEmpty();

        isExecuted();
      }
    };

    logTester.setLevel(LoggerLevel.DEBUG);
    check.scanFile(null, RuleKey.of("tst", "nodeList"), xmlFile);
    assertThat(check.hasBeenExecuted()).isTrue();
    assertThat(logTester.logs()).isEmpty();
  }

  private static XmlFile getXmlFile(String fileName) throws IOException {
    File file = new File(fileName);

    String content;
    try (Stream<String> lines = Files.lines(file.toPath())) {
      content = lines.collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Unable to load content of file %s", file.getPath()), e);
    }

    DefaultInputFile defaultInputFile = TestInputFileBuilder.create("", fileName)
      .setType(InputFile.Type.MAIN)
      .initMetadata(content)
      .setLanguage("xml")
      .setCharset(StandardCharsets.UTF_8)
      .build();

    return XmlFile.create(defaultInputFile);
  }

  abstract private static class XPathTesterCheck extends SimpleXPathBasedCheck {

    private boolean executed = false;

    public void isExecuted() {
      this.executed = true;
    }

    public boolean hasBeenExecuted() {
      return executed;
    }
  }
}
