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
package org.sonarsource.analyzer.commons.xml.checks;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.analyzer.commons.xml.XmlFile;
import org.sonarsource.analyzer.commons.xml.XmlTextRange;
import org.w3c.dom.Node;

public abstract class SonarXmlCheck {

  private SensorContext context;
  private InputFile inputFile;
  private RuleKey ruleKey;

  public final void scanFile(SensorContext context, RuleKey ruleKey, XmlFile file) {
    this.context = context;
    this.inputFile = file.getInputFile();
    this.ruleKey = ruleKey;
    scanFile(file);
  }

  public final InputFile inputFile() {
    return inputFile;
  }

  public final RuleKey ruleKey() {
    return ruleKey;
  }

  protected SensorContext getContext() {
    return context;
  }

  public abstract void scanFile(XmlFile file);

  public final void reportIssueOnFile(String message, List<Integer> secondaryLocationLines) {
    NewIssue issue = context.newIssue();

    NewIssueLocation location = issue.newLocation()
      .on(inputFile)
      .message(message);

    for (Integer line : secondaryLocationLines) {
      NewIssueLocation secondary = issue.newLocation()
        .on(inputFile)
        .at(inputFile.selectLine(line));
      issue.addLocation(secondary);
    }

    issue
      .at(location)
      .forRule(ruleKey)
      .save();
  }

  public final void reportIssue(XmlTextRange textRange, String message, List<Secondary> secondaries) {
    NewIssue issue = context.newIssue();
    NewIssueLocation location = getLocation(textRange, issue).message(message);
    secondaries.forEach(secondary -> {
      NewIssueLocation secondaryLocation = getLocation(secondary.range, issue);
      if (secondary.message != null) {
        secondaryLocation.message(secondary.message);
      }
      issue.addLocation(secondaryLocation);
    });

    issue
      .at(location)
      .forRule(ruleKey)
      .save();
  }

  private NewIssueLocation getLocation(XmlTextRange textRange, NewIssue issue) {
    return issue.newLocation()
      .on(inputFile)
      .at(inputFile.newRange(
        textRange.getStartLine(),
        textRange.getStartColumn(),
        textRange.getEndLine(),
        textRange.getEndColumn()));
  }

  public final void reportIssue(Node node, String message) {
    XmlTextRange textRange = XmlFile.nodeLocation(node);
    reportIssue(textRange, message, Collections.emptyList());
  }

  public static class Secondary {
    final XmlTextRange range;
    @Nullable final String message;

    public Secondary(XmlTextRange range, @Nullable String message) {
      this.range = range;
      this.message = message;
    }

    public Secondary(Node node, @Nullable String message) {
      this.range = XmlFile.nodeLocation(node);
      this.message = message;
    }
  }
}
