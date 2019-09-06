/*
 * Sonar Analyzers XML Parsing Commons
 * Copyright (C) 2009-2019 SonarSource SA
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

import com.sonarsource.checks.verifier.SingleFileVerifier;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.analyzer.commons.xml.XmlFile;
import org.sonarsource.analyzer.commons.xml.XmlTextRange;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarXmlCheckVerifier {

  private static final Path BASE_DIR = Paths.get("src/test/resources/checks/");
  private static final RuleKey RULE_KEY = RuleKey.of("repoKey", "ruleKey");

  private final Collection<Issue> issues;
  private final XmlFile file;

  private SonarXmlCheckVerifier(XmlFile file, Collection<Issue> issues) {
    this.file = file;
    this.issues = issues;
  }

  public static void verifyIssueOnFile(String relativePath, SonarXmlCheck check, String expectedIssueMessage, int... secondaryLines) {
    createVerifier(relativePath, check).checkIssueOnFile(expectedIssueMessage, secondaryLines);
  }

  public static void verifyIssues(String relativePath, SonarXmlCheck check) {
    createVerifier(relativePath, check).checkIssues();
  }

  public static void verifyNoIssue(String relativePath, SonarXmlCheck check) {
    createVerifier(relativePath, check).checkNoIssues();
  }

  private static SonarXmlCheckVerifier createVerifier(String fileName, SonarXmlCheck check) {
    File file = new File(new File(BASE_DIR.toFile(), check.getClass().getSimpleName()), fileName);

    SensorContextTester context = SensorContextTester.create(BASE_DIR);

    String filePath = file.getPath();
    String content;
    try (Stream<String> lines = Files.lines(file.toPath())) {
      content = lines.collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Unable to load content of file %s", filePath), e);
    }

    DefaultInputFile defaultInputFile = TestInputFileBuilder.create("", filePath)
      .setType(InputFile.Type.MAIN)
      .initMetadata(content)
      .setLanguage("xml")
      .setCharset(StandardCharsets.UTF_8)
      .build();

    context.fileSystem().add(defaultInputFile);

    XmlFile xmlFile;
    try {
      xmlFile = XmlFile.create(defaultInputFile);
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Unable to scan xml file %s", filePath), e);
    }

    check.scanFile(context, RULE_KEY, xmlFile);
    return new SonarXmlCheckVerifier(xmlFile, context.allIssues());
  }

  private void checkIssues() {
    SingleFileVerifier fileVerifier = SingleFileVerifier.create(file.getInputFile().path(), StandardCharsets.UTF_8);
    addComments(fileVerifier, file.getDocument());

    issues.forEach(issue -> {
      IssueLocation loc = issue.primaryLocation();
      TextRange textRange = loc.textRange();
      SingleFileVerifier.Issue actualIssue = fileVerifier
        .reportIssue(loc.message())
        .onRange(
          textRange.start().line(),
          textRange.start().lineOffset() + 1,
          textRange.end().line(),
          textRange.end().lineOffset());

      issue.flows().forEach(flow -> {
        IssueLocation secondaryLocation = flow.locations().get(0);
        TextRange secondaryRange = secondaryLocation.textRange();
        actualIssue.addSecondary(
          secondaryRange.start().line(),
          secondaryRange.start().lineOffset() + 1,
          secondaryRange.end().line(),
          secondaryRange.end().lineOffset(),
          secondaryLocation.message());
      });
    });

    fileVerifier.assertOneOrMoreIssues();
  }

  private static void addComments(SingleFileVerifier fileVerifier, Node node) {
    if (node.getNodeType() == Node.COMMENT_NODE) {
      Comment comment = (Comment) node;
      XmlTextRange range = XmlFile.nodeLocation(node);
      fileVerifier.addComment(range.getStartLine(), range.getStartColumn() + "<!--".length() + 1, comment.getNodeValue(), 0, 0);
    }

    XmlFile.children(node).forEach(child -> addComments(fileVerifier, child));
  }

  private void checkIssueOnFile(String expectedIssueMessage, int... secondaryLines) {
    assertThat(issues).hasSize(1);
    Issue issue = issues.iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo(expectedIssueMessage);
    assertThat(issue.primaryLocation().textRange()).isNull();

    List<Flow> flows = issue.flows();
    // secondaries are N flows of size 1
    assertThat(flows).hasSize(secondaryLines.length);
    assertThat(flows.stream().map(Flow::locations).collect(Collectors.toList())).allMatch(flow -> flow.size() == 1);

    // only contains lines
    Integer[] expectedLines = IntStream.of(secondaryLines).boxed().toArray(Integer[]::new);
    assertThat(flows.stream().map(Flow::locations).map(locs -> locs.get(0).textRange().start().line()).collect(Collectors.toList()))
      .containsExactly(expectedLines);
  }

  private void checkNoIssues() {
    assertThat(issues).isEmpty();
  }
}
