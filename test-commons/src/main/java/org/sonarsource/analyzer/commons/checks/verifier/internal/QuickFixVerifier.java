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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public class QuickFixVerifier implements Consumer<Set<InternalIssue>> {

  private static final Pattern QUICK_FIX_DEFINITION = Pattern.compile("\\s*quickfixes=(?<id>\\S+)\\]\\].*+");
  private static final Pattern QUICK_FIX_MESSAGE = Pattern.compile("\\s*fix@(?<id>\\S++)\\s++\\{\\{(?<message>.*)\\}\\}");
  private static final Pattern QUICK_FIX_EDIT = Pattern.compile("\\s*edit@(?<id>\\S+).++");

  private static final UnaryOperator<String> VALUE_PATTERN = str -> str + "(?<value>[^;]+).+";
  private static final Pattern START_LINE = Pattern.compile(VALUE_PATTERN.apply("\\s*sl="));
  private static final Pattern END_LINE = Pattern.compile(VALUE_PATTERN.apply("\\s*el="));
  private static final Pattern START_COLUMN = Pattern.compile(VALUE_PATTERN.apply("\\s*sc="));
  private static final Pattern END_COLUMN = Pattern.compile(VALUE_PATTERN.apply("\\s*ec="));
  private static final String PROPS_START_DELIMITER = "[[";
  private static final String PROPS_END_DELIMITER = "]]";
  public static final String VALUE_GROUP = "value";

  private final Map<TextSpan, List<QuickFix>> actualQuickFixes = new HashMap<>();
  private final Map<TextSpan, List<QuickFix>> expectedQuickFixes;

  //we support only 1 quickfix per issue
  private final Map<TextSpan, String> quickFixesForTextSpan = new LinkedHashMap<>();

  //in these two maps we put the messages and edits, not caring if the qf definition was already found or not
  private final Map<String, String> quickfixesMessages = new LinkedHashMap<>();
  private final Map<String, List<TextEdit>> quickfixesEdits = new LinkedHashMap<>();

  public QuickFixVerifier(List<Comment> expectedQuickFixesComments) {
    this.expectedQuickFixes = buildExpectedQuickFixes(expectedQuickFixesComments);
    if (expectedQuickFixes.isEmpty()) {
      throw new AssertionError("[Quick Fix] No quick fixes found in the expected comments");
    }
  }

  @Override
  public void accept(Set<InternalIssue> issues) {
    for (InternalIssue issue : issues) {
      TextSpan primaryLocation = getInternalIssueLocation(issue);
      List<QuickFix> quickFixes = issue.quickFix != null ? List.of(issue.quickFix) : new ArrayList<>();
      actualQuickFixes.put(primaryLocation, quickFixes);
      List<QuickFix> actual = actualQuickFixes.get(primaryLocation);
      String expectedQfId = quickFixesForTextSpan.getOrDefault(primaryLocation, null);
      if (expectedQfId == null) {
        // We don't have to always test quick fixes, we do nothing if there is no expected quick fix.
        continue;
      }
      List<QuickFix> expected = expectedQuickFixes.get(primaryLocation);
      validateQuickfixes(expected, actual, issue);
    }
  }

  private static void validateQuickfixes(List<QuickFix> expected, @Nullable List<QuickFix> actual, InternalIssue issue) {
    TextSpan primaryLocation = getInternalIssueLocation(issue);
    if (actual == null || actual.isEmpty()) {
      // At this point, we know that expected is not empty
      throw new AssertionError(String.format("[Quick Fix] Missing quick fix for issue on line %d", primaryLocation.startLine));
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

  private Map<TextSpan, List<QuickFix>> buildExpectedQuickFixes(List<Comment> expectedQuickFixesComments) {
    for (Comment comment : expectedQuickFixesComments) {
      parseQuickFix(comment.content, comment.line);
    }
    Map<TextSpan, List<QuickFix>> result = new HashMap<>();
    for (Map.Entry<TextSpan, String> entry : quickFixesForTextSpan.entrySet()) {
      String qfId = quickFixesForTextSpan.get(entry.getKey());
      List<TextEdit> edits = quickfixesEdits.get(qfId);
      if (edits == null || edits.isEmpty()) {
        throw new AssertionError(String.format("[Quick Fix] Quick fix edits not found for quick fix id %s", qfId));
      }
      //we only support 1 qf per issue
      String description = quickfixesMessages.get(qfId);
      if (description == null || description.isEmpty()) {
        throw new AssertionError(String.format("[Quick Fix] Quick fix message not found for quick fix id %s", qfId));
      }
      List<QuickFix> quickFixes = List.of(QuickFix.newQuickFix(description, entry.getKey()).addTextEdits(edits).build());
      result.put(entry.getKey(), quickFixes);
    }
    return result;
  }

  private static TextSpan editorTextSpan(TextSpan textSpan) {
    return new TextSpan(textSpan.startLine, textSpan.startCharacter + 1, textSpan.endLine, textSpan.endCharacter + 1);
  }

  private static int getIssueLine(InternalIssue issue) {
    if (issue.location instanceof IssueLocation.Line) {
      return ((IssueLocation.Line) issue.location).getLine();
    } else {
      return -1;
    }
  }

  void parseQuickFix(String comment, int line) {
    Matcher definitionMatcher = QUICK_FIX_DEFINITION.matcher(comment);
    if (definitionMatcher.find()) {
      parseQuickFixDefinition(definitionMatcher.group("id"), comment, line);
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
      if(replacement == null) {
        throw new AssertionError(String.format("[Quick Fix] Missing replacement %d: %s", line, comment));
      }
      TextEdit quickFixEdit = parseTextEdit(comment, line, replacement);
      quickfixesEdits.computeIfAbsent(quickFixId, k -> new ArrayList<>()).add(quickFixEdit);
    }
  }

  private void parseQuickFixDefinition(String quickFixId, String comment, int line) {
    Matcher scMatcher = START_COLUMN.matcher(comment);
    if (!scMatcher.find()) {
      throw new AssertionError(String.format("[Quick Fix] Missing start column for quick fix definition on line %d", line));
    }
    Matcher ecMatcher = END_COLUMN.matcher(comment);
    if (!ecMatcher.find()) {
      throw new AssertionError(String.format("[Quick Fix] Missing end column for quick fix definition on line %d", line));
    }
    Matcher slMatcher = START_LINE.matcher(comment);
    Matcher elMatcher = END_LINE.matcher(comment);
    int startLine = slMatcher.find() ? Integer.parseInt(slMatcher.group(VALUE_GROUP)) : line;
    int startColumn = Integer.parseInt(scMatcher.group(VALUE_GROUP));
    int endLine = elMatcher.find() ? Integer.parseInt(elMatcher.group(VALUE_GROUP)) : line;
    int endColumn = Integer.parseInt(ecMatcher.group(VALUE_GROUP));
    TextSpan textSpan = new TextSpan(startLine, startColumn, endLine, endColumn);
    quickFixesForTextSpan.put(textSpan, quickFixId);
  }

  private static String parseMessage(String cleanedComment, int horizon) {
    String delimitedComment = cleanedComment.substring(0, horizon);
    int firstIndex = delimitedComment.indexOf("{{");
    if (firstIndex != -1) {
      int lastIndex = delimitedComment.lastIndexOf("}}");
      if (lastIndex != -1) {
        return delimitedComment.substring(firstIndex + 2, lastIndex);
      }
    }
    return null;
  }

  private static TextEdit parseTextEdit(String comment, int line, String replacement) {
    int start = comment.indexOf(PROPS_START_DELIMITER);
    int end = comment.indexOf(PROPS_END_DELIMITER);
    if (start != -1 && end != -1) {
      String qfPropertiesString = comment.substring(start + PROPS_START_DELIMITER.length(), end);
      var propMap = extractTextEditProps(qfPropertiesString);
      int startLine = line + propMap.getOrDefault("sl", 0);
      int startCharacter = propMap.getOrDefault("sc", 0);
      int endLine = line + propMap.getOrDefault("el", 0);
      int endCharacter = propMap.getOrDefault("ec", 0);
      return TextEdit.replaceTextSpan(TextEdit.textSpan(startLine, startCharacter, endLine, endCharacter), replacement);
    } else {
      throw new AssertionError(String.format("[Quick Fix] Invalid quickfix format line %d: %s", line, comment));
    }
  }

  private static Map<String, Integer> extractTextEditProps(String attributesString) {
    Map<String, Integer> attributesMap = new HashMap<>();
    String[] attributes = attributesString.split(";");
    for (String attr : attributes) {
      String[] keyValue = attr.split("=");
      attributesMap.put(keyValue[0], Integer.parseInt(keyValue[1]));
    }
    return attributesMap;
  }

  private static TextSpan getInternalIssueLocation(InternalIssue issue) {
    if (issue.location instanceof IssueLocation.Range) {
      IssueLocation.Range range = (IssueLocation.Range) issue.location;
      return new TextSpan(range.getLine(), range.getColumn(), range.getEndLine(), range.getEndColumn());
    } else {
      IssueLocation.Line line = (IssueLocation.Line) issue.location;
      return new TextSpan(line.getLine());
    }
  }

}
