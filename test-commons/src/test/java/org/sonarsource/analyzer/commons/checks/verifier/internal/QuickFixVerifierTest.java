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

    TextEdit edit1 = mockEdit(5, 18, 5, 23, "\"foo\n\"");
    TextEdit edit2 = mockEdit(6, 5, 6, 10, "\"bar\"");
    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals", edit1, edit2);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23).addQuickFix(qf1);
    verifier.reportIssue("Without quickfix").onRange(8, 1, 8, 10);
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
  public void test_missing_actual_quickfix() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23);
    verifier.reportIssue("Without quickfix").onLine(8);
    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessage("[Quick Fix] Missing quick fix for issue on line 3");
  }

  @Test
  public void test_sample_without_expected_quickfixes() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithoutQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessage("[Quick Fix] No quick fixes found in the expected comments");

    var verifierWithoutExpectingQfs = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifierWithoutExpectingQfs);
    verifierWithoutExpectingQfs.reportIssue("issue").onRange(3, 18, 3, 23);
    verifierWithoutExpectingQfs.assertOneOrMoreIssues();
  }

  @Test
  public void test_actual_missing_edits() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8)
      .withQuickFixes();

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals");

    verifier.reportIssue("issue").onRange(3, 18, 3, 23).addQuickFix(qf1);
    verifier.reportIssue("Without quickfix").onLine(8);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong number of edits for issue on line 3.");
  }

  @Test
  public void test_expected_qf_missing_edits() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/main.js");

    verifier.addComment(4, 1, "Noncompliant [[sc=0;ec=0;quickfixes=qf1]]", 0, 0);
    verifier.addComment(5, 1, "fix@qf1 {{Expected description}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Quick fix edits not found for quick fix id qf1");
  }

  @Test
  public void test_expected_qf_missing_description() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/main.js");

    verifier.addComment(4, 1, "Noncompliant [[sc=0;ec=0;quickfixes=qf1]]", 0, 0);
    verifier.addComment(5, 1, "edit@qf1 [[sl=1;sc=1;el=1;ec=1]] {{replacement}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Quick fix description not found for quick fix id");
  }

  @Test
  public void test_wrong_description() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/code.js");

    TextEdit edit1 = mockEdit(1, 1, 1, 3, "foo");
    QuickFix qf1 = mockQf("Wrong description", edit1);
    verifier.reportIssue("issue").onRange(1, 1, 1, 3).addQuickFix(qf1);
    verifier.addComment(1, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 0, 0);
    verifier.addComment(2, 5, "fix@qf1 {{Expected description}}", 0, 0);
    verifier.addComment(3, 5, "edit@qf1 [[sc=1;ec=1]] {{foo}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong description for issue on line 1.");
  }

  @Test
  public void test_wrong_number_of_edits() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/code.js");

    TextEdit edit1 = mockEdit(1, 1, 1, 3, "foo");
    TextEdit edit2 = mockEdit(1, 1, 1, 3, "goo");
    QuickFix qf1 = mockQf("Description", edit1, edit2);
    verifier.reportIssue("issue").onRange(1, 1, 1, 3).addQuickFix(qf1);
    verifier.addComment(1, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 0, 0);
    verifier.addComment(2, 5, "fix@qf1 {{Description}}", 0, 0);
    verifier.addComment(3, 5, "edit@qf1 [[sc=1;ec=1]] {{foo}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong number of edits for issue on line");
  }

  @Test
  public void test_wrong_edit_replacement() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/main.js");

    TextEdit edit1 = mockEdit(4, 1, 4, 3, "wrong replacement");
    QuickFix qf1 = mockQf("Description", edit1);
    verifier.reportIssue("issue").onRange(4, 1, 4, 3).addQuickFix(qf1);
    verifier.addComment(4, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 0, 0);
    verifier.addComment(5, 5, "fix@qf1 {{Description}}", 0, 0);
    verifier.addComment(6, 5, "edit@qf1 [[sc=1;ec=1]] {{expected replacement}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong text replacement of edit 1 for issue on line 4.");
  }

  @Test
  public void test_wrong_edit_location() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/main.js");

    TextEdit edit1 = mockEdit(4, 3, 4, 5, "expected replacement");
    QuickFix qf1 = mockQf("Description", edit1);
    verifier.reportIssue("issue").onRange(4, 1, 4, 3).addQuickFix(qf1);
    verifier.addComment(4, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 0, 0);
    verifier.addComment(5, 5, "fix@qf1 {{Description}}", 0, 0);
    verifier.addComment(6, 5, "edit@qf1 [[sc=1;ec=1]] {{expected replacement}}", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong change location of edit 1 for issue on line 4.");
  }

  @Test
  public void test_missing_expected_replacement() {
    var verifier = getVerifierWithQuickFixes("src/test/resources/main.js");

    verifier.reportIssue("issue").onLine(1);
    verifier.addComment(4, 5, "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]", 0, 0);
    verifier.addComment(5, 5, "fix@qf1 {{Description}}", 0, 0);
    verifier.addComment(6, 5, "edit@qf1 [[sc=1;ec=1]]", 0, 0);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Missing replacement for edit at line");
  }

  private static SingleFileVerifier getVerifierWithQuickFixes(String path) {
    return SingleFileVerifier.create(Paths.get(path), UTF_8)
      .withQuickFixes();
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
