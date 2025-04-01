/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public class QuickFixParser {

  private static final Pattern QUICK_FIX_MESSAGE = Pattern.compile("\\s*fix@(?<id>\\S++)\\s++\\{\\{(?<message>.*)\\}\\}");
  private static final Pattern QUICK_FIX_EDIT = Pattern.compile("\\s*edit@(?<id>\\S+).++");

  private static final String PROPS_START_DELIMITER = "[[";
  private static final String PROPS_END_DELIMITER = "]]";

  private final Map<String, QuickFix> expectedQuickFixes = new HashMap<>();
  private final Map<TextSpan, List<String>> quickFixesForIssue = new HashMap<>();

  private final Map<String, String> quickfixesMessages = new HashMap<>();
  private final Map<String, List<RelativeTextEdit>> quickfixesEdits = new HashMap<>();
  //this is used to calculate absolute line numbers for text edits
  private final Map<String, Integer> quickfixesLineReference = new HashMap<>();

  public QuickFixParser(List<Comment> expectedQuickFixesComments, Map<Integer, LineIssues> expectedIssues) {
    buildExpectedQuickFixes(expectedQuickFixesComments, expectedIssues);
  }

  private void buildExpectedQuickFixes(List<Comment> expectedQuickFixesComments, Map<Integer, LineIssues> expectedIssues) {
    populateIssueData(expectedIssues);

    for (Comment comment : expectedQuickFixesComments) {
      parseQuickFix(comment.content, comment.line);
    }

    for (Map.Entry<TextSpan, List<String>> entry : quickFixesForIssue.entrySet()) {
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
          .addTextEdits(edits.stream().map(rel -> rel.toAbsoluteTextEdit(quickfixesLineReference.get(qfId))).collect(Collectors.toList()))
          .build();
        expectedQuickFixes.put(qfId, quickFix);
      }
    }
  }

  private void populateIssueData(Map<Integer, LineIssues> expectedIssues) {
    expectedIssues.values().forEach(lineIssues -> {
      String qfIds = lineIssues.params.get("quickfixes");
      if (qfIds != null && !"!".equals(qfIds)) {
        String[] ids = qfIds.split(",");
        for (String id : ids) {
          quickfixesLineReference.put(id, lineIssues.line);
          quickFixesForIssue.computeIfAbsent(getTextSpan(lineIssues), k -> new ArrayList<>()).add(id);
        }
      }
    });
  }

  void parseQuickFix(String comment, int line) {
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

  private static TextSpan getTextSpan(LineIssues issue) {
    String sc = issue.params.get("sc");
    String ec = issue.params.get("ec");
    return new TextSpan(
      issue.line,
      sc != null ? Integer.parseInt(sc) : 1,
      issue.line,
      ec != null ? Integer.parseInt(ec) : 1);
  }

  private static class RelativeTextEdit {
    private RelativeLine sl = new RelativeLine();
    private int sc = 0;
    private RelativeLine el = new RelativeLine();
    private int ec = 0;
    private String replacement = "";

    private static class RelativeLine {
      private int line = 0;
      private boolean isRelative = true;

      public RelativeLine() {
      }

      public RelativeLine(String line) {
        this.line = Integer.parseInt(line);
        if (!line.startsWith("+") && !line.startsWith("-")) {
          this.isRelative = false;
        }
      }
    }

    public TextEdit toAbsoluteTextEdit(int referenceLine) {
      int startLine = sl.isRelative ? (referenceLine + sl.line) : sl.line;
      int endLine = el.isRelative ? (referenceLine + el.line) : el.line;
      return TextEdit.replaceTextSpan(new TextSpan(startLine, sc, endLine, ec), replacement);
    }

  }

  public Map<String, QuickFix> getExpectedQuickFixes() {
    return expectedQuickFixes;
  }

}
