/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public class QuickFixVerifier implements Consumer<Set<InternalIssue>> {

  private static final Pattern ISSUE_DEFINITION = Pattern.compile("\\s*quickfixes=(?<id>\\S+)\\]\\].*+");
  private static final Pattern QUICK_FIX_MESSAGE = Pattern.compile("\\s*fix@(?<id>\\S++)\\s++\\{\\{(?<message>.*)\\}\\}");
  private static final Pattern QUICK_FIX_EDIT = Pattern.compile("\\s*edit@(?<id>\\S+).++");
  private static final Pattern ISSUE_LINE_MODIFIER = Pattern.compile("\\s*Noncompliant@(?<mod>\\S+)\\s*\\[\\[");

  private static final UnaryOperator<String> VALUE_PATTERN = str -> str + "(?<value>[^;]+).+";
  private static final Pattern START_LINE = Pattern.compile(VALUE_PATTERN.apply("\\s*sl="));
  private static final Pattern END_LINE = Pattern.compile(VALUE_PATTERN.apply("\\s*el="));
  private static final Pattern START_COLUMN = Pattern.compile(VALUE_PATTERN.apply("\\s*sc="));
  private static final Pattern END_COLUMN = Pattern.compile(VALUE_PATTERN.apply("\\s*ec="));
  private static final String PROPS_START_DELIMITER = "[[";
  private static final String PROPS_END_DELIMITER = "]]";
  public static final String VALUE_GROUP = "value";

  private final Map<IssueIdentifier, List<QuickFix>> expectedQuickFixes = new HashMap<>();
  private final Map<IssueIdentifier, List<String>> quickFixesForIssue = new HashMap<>();

  //in these two maps we put the messages and edits, not caring if the qf definition was already found or not
  private final Map<String, String> quickfixesMessages = new HashMap<>();
  private final Map<String, List<RelativeTextEdit>> quickfixesEdits = new HashMap<>();
  //this is used to calculate absolute line numbers for text edits
  private final Map<String, Integer> quickfixesLineReference = new HashMap<>();

  public QuickFixVerifier(List<Comment> expectedQuickFixesComments) {
    buildExpectedQuickFixes(expectedQuickFixesComments);
  }

  @Override
  public void accept(Set<InternalIssue> issues) {
    for (InternalIssue issue : issues) {
      IssueIdentifier issueIdentifier = getIssueIdentifier(issue);
      List<QuickFix> actual = issue.quickFixes;
      List<String> expectedQfId = quickFixesForIssue.getOrDefault(issueIdentifier, null);
      if (expectedQfId == null) {
        // We don't have to always test quick fixes, we do nothing if there is no expected quick fix.
        continue;
      }
      List<QuickFix> expected = expectedQuickFixes.get(issueIdentifier);
      validateQuickfixes(expected, actual, issue);
      expectedQuickFixes.remove(issueIdentifier);
    }
    if (!expectedQuickFixes.isEmpty()) {
      throw new AssertionError("[Quick Fix] Missing quick fixes for the following issues: " +
        expectedQuickFixes.keySet().stream().map(IssueIdentifier::toString).collect(Collectors.joining(", ")));
    }
  }

  private static void validateQuickfixes(List<QuickFix> expected, List<QuickFix> actual, InternalIssue issue) {
    IssueIdentifier issueIdentifier = getIssueIdentifier(issue);
    if (actual.isEmpty()) {
      // At this point, we know that expected is not empty
      throw new AssertionError(String.format("[Quick Fix] Missing quick fix for issue on line %d", issueIdentifier.location.startLine));
    }
    int actualSize = actual.size();
    for (int i = 0; i < actualSize; i++) {
      validate(issue, actual.get(i), expected.get(i));
    }
  }

  private static void validate(InternalIssue actualIssue, QuickFix actual, QuickFix expected) {
    String actualDescription = actual.getDescription();
    String expectedDescription = expected.getDescription();
    if (!actualDescription.equals(expectedDescription)) {
      throw new AssertionError(String.format("[Quick Fix] Wrong description for issue on line %d.%nExpected: {{%s}}%nbut was:     {{%s}}",
        getIssueLine(actualIssue),
        expectedDescription,
        actualDescription));
    }
    List<TextEdit> actualTextEdits = actual.getTextEdits();
    List<TextEdit> expectedTextEdits = expected.getTextEdits();
    if (actualTextEdits.size() != expectedTextEdits.size()) {
      throw new AssertionError(String.format("[Quick Fix] Wrong number of edits for issue on line %d.%nExpected: {{%d}}%nbut was:     " +
          "{{%d}}",
        getIssueLine(actualIssue),
        expectedTextEdits.size(),
        actualTextEdits.size()));
    }
    for (int i = 0; i < actualTextEdits.size(); i++) {
      TextEdit actualTextEdit = actualTextEdits.get(i);
      TextEdit expectedTextEdit = expectedTextEdits.get(i);

      String expectedReplacement = expectedTextEdit.getReplacement();
      String actualReplacement = actualTextEdit.getReplacement();
      if (expectedReplacement.contains("\\n")) {
        // new lines are expected
        expectedReplacement = expectedReplacement.replace("\\n", "\n");
      }
      if (!actualReplacement.equals(expectedReplacement)) {
        throw new AssertionError(String.format("[Quick Fix] Wrong text replacement of edit %d for issue on line %d.%nExpected: " +
            "{{%s}}%nbut was:     {{%s}}",
          (i + 1),
          getIssueLine(actualIssue),
          expectedReplacement,
          actualReplacement));
      }
      TextSpan actualNormalizedTextSpan = actualTextEdit.getTextSpan();
      if (!actualNormalizedTextSpan.equals(expectedTextEdit.getTextSpan())) {
        throw new AssertionError(String.format("[Quick Fix] Wrong change location of edit %d for issue on line %d.%nExpected: {{%s}}%nbut" +
            " was:     {{%s}}",
          (i + 1),
          getIssueLine(actualIssue),
          editorTextSpan(expectedTextEdit.getTextSpan()),
          editorTextSpan(actualNormalizedTextSpan)));
      }
    }
  }

  private void buildExpectedQuickFixes(List<Comment> expectedQuickFixesComments) {
    for (Comment comment : expectedQuickFixesComments) {
      parseQuickFix(comment.content, comment.line);
    }
    for (Map.Entry<IssueIdentifier, List<String>> entry : quickFixesForIssue.entrySet()) {
      for (String qfId : entry.getValue()) {
        List<RelativeTextEdit> edits = quickfixesEdits.get(qfId);
        if (edits == null) {
          throw new AssertionError(String.format("[Quick Fix] Quick fix edits not found for quick fix id %s", qfId));
        }
        String description = quickfixesMessages.get(qfId);
        if (description == null) {
          throw new AssertionError(String.format("[Quick Fix] Quick fix description not found for quick fix id %s", qfId));
        }
        QuickFix quickFix = QuickFix.newQuickFix(description, entry.getKey())
          .addTextEdits(edits.stream().map(rel -> rel.toAbsoluteTextEdit(qfId, quickfixesLineReference)).collect(Collectors.toList()))
          .build();
        expectedQuickFixes.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(quickFix);
      }
    }
  }

  private static TextSpan editorTextSpan(TextSpan textSpan) {
    return new TextSpan(textSpan.startLine, textSpan.startCharacter + 1, textSpan.endLine, textSpan.endCharacter + 1);
  }

  private static int getIssueLine(InternalIssue issue) {
    if (issue.location instanceof IssueLocation.Line) {
      return ((IssueLocation.Line) issue.location).getLine();
    } else {
      //This would be reached only by issues on file, for which quickfixes are not supported
      return -1;
    }
  }

  void parseQuickFix(String comment, int line) {
    Matcher definitionMatcher = ISSUE_DEFINITION.matcher(comment);
    if (definitionMatcher.find()) {
      parseIssueDefinition(definitionMatcher.group("id"), comment, line);
      return;
    }
    Matcher messageMatcher = QUICK_FIX_MESSAGE.matcher(comment);
    if (messageMatcher.find()) {
      String quickFixId = messageMatcher.group("id");
      String quickFixMessage = messageMatcher.group("message");
      quickfixesMessages.put(quickFixId, quickFixMessage);
      return;
    }
    Matcher editMatcher = QUICK_FIX_EDIT.matcher(comment);
    if (editMatcher.find()) {
      String quickFixId = editMatcher.group("id");
      String replacement = parseMessage(comment, comment.length());
      if (replacement == null) {
        throw new AssertionError(String.format("[Quick Fix] Missing replacement for edit at line %d", line));
      }
      RelativeTextEdit relativeTextEdit = parseTextEdit(comment, replacement);
      quickfixesEdits.computeIfAbsent(quickFixId, k -> new ArrayList<>()).add(relativeTextEdit);
    }
  }

  // Populates the quickFixesForIssue map with identifiers of issues and their corresponding quickfixes ids
  private void parseIssueDefinition(String quickFixId, String comment, int line) {
    String[] ids = quickFixId.split(",");
    Matcher scMatcher = START_COLUMN.matcher(comment);
    Matcher ecMatcher = END_COLUMN.matcher(comment);
    Matcher slMatcher = START_LINE.matcher(comment);
    Matcher elMatcher = END_LINE.matcher(comment);
    Matcher lineModMatcher = ISSUE_LINE_MODIFIER.matcher(comment);
    int lineMod = lineModMatcher.find() ? Integer.parseInt(lineModMatcher.group("mod")) : 0;
    int startLine = slMatcher.find() ? Integer.parseInt(slMatcher.group(VALUE_GROUP)) : line + lineMod;
    int startColumn = scMatcher.find() ? Integer.parseInt(scMatcher.group(VALUE_GROUP)) : 1;
    int endLine = elMatcher.find() ? Integer.parseInt(elMatcher.group(VALUE_GROUP)) : line + lineMod;
    int endColumn = ecMatcher.find() ? Integer.parseInt(ecMatcher.group(VALUE_GROUP)) : 1;
    var issueId = new IssueIdentifier(startLine, startColumn, endLine, endColumn);
    for(String id : ids){
      quickfixesLineReference.put(id, line + lineMod);
      quickFixesForIssue.computeIfAbsent(issueId, k -> new ArrayList<>()).add(id);
    }
  }

  private static String parseMessage(String cleanedComment, int horizon) {
    String delimitedComment = cleanedComment.substring(0, horizon);
    int firstIndex = delimitedComment.indexOf("{{");
    if (firstIndex != -1) {
      int lastIndex = delimitedComment.lastIndexOf("}}");
      if (lastIndex != -1) {
        return delimitedComment.substring(firstIndex + 2, lastIndex);
      }
      throw new AssertionError(String.format("[Quick Fix] Wrong format in comment: %s", cleanedComment));
    }
    return null;
  }

  private static RelativeTextEdit parseTextEdit(String comment, String replacement) {
    RelativeTextEdit relativeTextEdit = new RelativeTextEdit();
    relativeTextEdit.replacement = replacement;
    int start = comment.indexOf(PROPS_START_DELIMITER);
    int end = comment.indexOf(PROPS_END_DELIMITER);
    if (start != -1 && end != -1) {
      String qfPropertiesString = comment.substring(start + PROPS_START_DELIMITER.length(), end);
      String[] attributes = qfPropertiesString.split(";");
      for (String attr : attributes) {
        String[] keyValue = attr.split("=");
        switch (keyValue[0]) {
          case "sl":
            relativeTextEdit.sl = new RelativeTextEdit.RelativeLine(keyValue[1]);
            break;
          case "sc":
            relativeTextEdit.sc = Integer.parseInt(keyValue[1]);
            break;
          case "el":
            relativeTextEdit.el = new RelativeTextEdit.RelativeLine(keyValue[1]);
            break;
          case "ec":
            relativeTextEdit.ec = Integer.parseInt(keyValue[1]);
            break;
          default:
            throw new AssertionError(String.format("[Quick Fix] Invalid quickfix edit format: %s", comment));
        }
      }
      return relativeTextEdit;
    } else {
      throw new AssertionError(String.format("[Quick Fix] Invalid quickfix edit format: %s", comment));
    }
  }

  private static IssueIdentifier getIssueIdentifier(InternalIssue issue) {
    IssueLocation location = issue.location;
    if (location instanceof IssueLocation.Range) {
      IssueLocation.Range range = (IssueLocation.Range) location;
      return new IssueIdentifier(range.getLine(), range.getColumn(), range.getEndLine(), range.getEndColumn());
    } else if (location instanceof IssueLocation.Line) {
      IssueLocation.Line line = (IssueLocation.Line) location;
      return new IssueIdentifier(line.getLine(), -1, line.getLine(), -1);
    } else {
      // No support for quickfixes on file
      return new IssueIdentifier(-1, -1, -1, -1);
    }
  }

  private static class RelativeTextEdit {
    public RelativeLine sl = new RelativeLine();
    public int sc = 0;
    public RelativeLine el = new RelativeLine();
    public int ec = 0;
    public String replacement = "";

    private static class RelativeLine {
      public int line = 0;
      public boolean isRelative = true;

      public RelativeLine() {
      }

      public RelativeLine(String line) {
        this.line = Integer.parseInt(line);
        if (!line.startsWith("+") && !line.startsWith("-")) {
          this.isRelative = false;
        }
      }
    }

    public TextEdit toAbsoluteTextEdit(String qfId, Map<String, Integer> quickfixesLineReference) {
      int startLine = sl.isRelative ? quickfixesLineReference.get(qfId) + sl.line : sl.line;
      int endLine = el.isRelative ? quickfixesLineReference.get(qfId) + el.line : el.line;
      return TextEdit.replaceTextSpan(new TextSpan(startLine, sc, endLine, ec), replacement);
    }

  }

  private static class IssueIdentifier {
    public final TextSpan location;

    private IssueIdentifier(int sl, int sc, int el, int ec) {
      location = new TextSpan(sl, sc, el, ec);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof IssueIdentifier) {
        IssueIdentifier other = (IssueIdentifier) obj;
        return other.location.equals(location);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return location.hashCode();
    }

    @Override
    public String toString() {
      return location.toString();
    }
  }

}
