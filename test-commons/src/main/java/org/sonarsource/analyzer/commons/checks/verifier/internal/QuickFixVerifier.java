package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.quickfixes.QuickFix;
import org.sonarsource.analyzer.commons.quickfixes.TextEdit;
import org.sonarsource.analyzer.commons.quickfixes.TextSpan;

public class QuickFixVerifier implements Consumer<Set<InternalIssue>> {

  private static final Pattern QUICK_FIX_MESSAGE = Pattern.compile("\\s*fix@(?<id>\\S+)\\s+\\{\\{(?<message>.*)\\}\\}");
  private static final Pattern QUICK_FIX_EDIT = Pattern.compile("\\s*edit@(?<id>\\S+).+");
  private static final String PROPS_START_DELIMITER = "[[";
  private static final String PROPS_END_DELIMITER = "]]";

  private final Map<TextSpan, List<QuickFix>> expectedQuickFixes;
  private final Map<TextSpan, List<QuickFix>> actualQuickFixes;

  //  private final Map<TextSpan, List<String>> quickFixesForTextSpan = new LinkedHashMap<>();
  //  private final Map<TextSpan, List<QuickFix>> quickFixes;
  private final Map<String, String> quickfixesMessages = new LinkedHashMap<>();
  private final Map<String, List<TextEdit>> quickfixesEdits = new LinkedHashMap<>();

  public QuickFixVerifier(List<Comment> expectedQuickFixesComments, Map<TextSpan, List<QuickFix>> actualQuickFixes) {
    this.expectedQuickFixes = buildExpectedQuickFixes(expectedQuickFixesComments);
    this.actualQuickFixes = actualQuickFixes;
  }

  @Override
  public void accept(Set<InternalIssue> issues) {
    for (InternalIssue issue : issues) {
      if (issue.quickFix == null) {
        continue;
      }
      TextSpan primaryLocation = issue.quickFix.getTextEdits().get(0).getTextSpan();
      List<QuickFix> expected = expectedQuickFixes.get(primaryLocation);
      if (expected == null) {
        // We don't have to always test quick fixes, we do nothing if there is no expected quick fix.
        continue;
      }
      List<QuickFix> actual = actualQuickFixes.get(primaryLocation);
      if (expected.isEmpty()) {
        if (actual != null && !actual.isEmpty()) {
          throw new AssertionError(String.format("[Quick Fix] Issue on line %d contains quick fixes while none where expected", primaryLocation.startLine));
        }
        // Else: no issue in both expected and actual, nothing to do
      } else {
        validateIfSameSize(expected, actual, issue);
      }
    }
  }

  private static void validateIfSameSize(List<QuickFix> expected, @Nullable List<QuickFix> actual, InternalIssue issue) {
    TextSpan primaryLocation = issue.quickFix.getTextEdits().get(0).getTextSpan();
    if (actual == null || actual.isEmpty()) {
      // At this point, we know that expected is not empty
      throw new AssertionError(String.format("[Quick Fix] Missing quick fix for issue on line %d", primaryLocation.startLine));
    }
    int actualSize = actual.size();
    int expectedSize = expected.size();
    if (actualSize != expectedSize) {
      throw new AssertionError(
        String.format("[Quick Fix] Number of quickfixes expected is not equal to the number of expected on line %d: expected: %d , actual: %d",
          primaryLocation.startLine,
          expectedSize,
          actualSize));
    }
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
      throw new AssertionError(String.format("[Quick Fix] Wrong number of edits for issue on line %d.%nExpected: {{%d}}%nbut was:     {{%d}}",
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
        throw new AssertionError(String.format("[Quick Fix] Wrong text replacement of edit %d for issue on line %d.%nExpected: {{%s}}%nbut was:     {{%s}}",
          (i + 1),
          getIssueLine(actualIssue),
          expectedReplacement,
          actualReplacement));
      }
      TextSpan actualNormalizedTextSpan = actualTextEdit.getTextSpan();
      if (!actualNormalizedTextSpan.equals(expectedTextEdit.getTextSpan())) {
        throw new AssertionError(String.format("[Quick Fix] Wrong change location of edit %d for issue on line %d.%nExpected: {{%s}}%nbut was:     {{%s}}",
          (i + 1),
          getIssueLine(actualIssue),
          editorTextSpan(expectedTextEdit.getTextSpan()),
          editorTextSpan(actualNormalizedTextSpan)));
      }
    }
  }

  private Map<TextSpan, List<QuickFix>> buildExpectedQuickFixes(List<Comment> expectedQuickFixesComments) {
    Map<TextSpan, List<QuickFix>> expectedQuickFixes = new HashMap<>();
    for (Comment comment : expectedQuickFixesComments) {
      parseQuickFix(comment.content, comment.line);
    }
    return expectedQuickFixes;
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
      TextEdit quickFixEdit = parseTextEdit(comment, line, replacement);
      quickfixesEdits.computeIfAbsent(quickFixId, k -> new ArrayList<>()).add(quickFixEdit);
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
      throw new AssertionError(String.format("Invalid quickfix format line %d: %s", line, comment));
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

}
