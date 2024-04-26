package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.CommentParser;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QuickFixVerifierTest {

  @Test
  public void test_correct_quickfix() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8)
      .withQuickFixes();

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    TextEdit edit1 = mockEdit(5, 18, 5, 23, "\"foo\"");
    TextEdit edit2 = mockEdit(6, 5, 6, 10, "\"bar\"");
    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals", edit1, edit2);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23).addQuickFix(qf1);
    verifier.reportIssue("Without quickfix").onLine(8);
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_without_quickfixes_enabled() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23);
    verifier.reportIssue("Without quickfix").onLine(8);
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_without_missing_quickfix() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23);
    assertThatThrownBy(() -> verifier.assertOneOrMoreIssues())
      .hasMessage("[Quick Fix] Missing quick fix for issue on line 3");
  }

  @Test
  public void test_code_without_quickfixes() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithoutQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    assertThatThrownBy(() -> verifier.assertOneOrMoreIssues())
      .hasMessage("[Quick Fix] No quick fixes found in the expected comments");

    var verifierWithoutExpectingQfs = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifierWithoutExpectingQfs);
    verifierWithoutExpectingQfs.reportIssue("issue").onRange(3, 18, 3, 23);
    verifierWithoutExpectingQfs.assertOneOrMoreIssues();
  }

  @Test
  public void test_qf_no_message() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFixNoMessage.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    assertThatThrownBy(() -> verifier.assertOneOrMoreIssues())
      .hasMessage("[Quick Fix] Quick fix message not found for quick fix id qf1");
  }

  @Test
  public void test_missing_edits() {
    Path path = Paths.get("src/test/resources/code.js");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();

    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals");
    verifier.reportIssue("issue").onLine(1).addQuickFix(qf1);
    verifier.addComment(1, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 2, 0);

    assertThatThrownBy(() -> verifier.assertOneOrMoreIssues())
      .hasMessage("[Quick Fix] Quick fix edits not found for quick fix id qf1");
  }

  private static QuickFix mockQf(String descr, TextEdit... edits) {
    return QuickFix
      .newQuickFix(descr)
      .addTextEdits(List.of(edits))
      .build();
  }

  private static TextEdit mockEdit(int startLine, int startCol, int endLine, int endCol, String newText) {
    return TextEdit.replaceTextSpan(
      TextEdit.textSpan(startLine, startCol, endLine, endCol), newText);
  }

}
