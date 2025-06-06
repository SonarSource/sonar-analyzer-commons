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

public class QuickFixParserTest {

  @Test
  public void test_correct_quickfix() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8);

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    TextEdit edit1 = mockEdit(3, 18, 3, 23, "\"foo\\n\"");
    TextEdit edit2 = mockEdit(3, 5, 3, 10, "\"bar\"");
    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals", edit1, edit2);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23).addQuickFix(qf1);
    verifier.reportIssue("Without quickfix").onRange(8, 1, 8, 10);

    verifier.reportIssue("On file for coverage").onFile();
    verifier.addComment(10, 1, "Noncompliant@0 {{On file for coverage}}", 0, 0);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_correct_quickfixes_locations() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=1;ec=1;quickfixes=qf1]]",
      "fix@qf1 {{first}}",
      "edit@qf1 [[sl=+2;sc=1;el=+3;ec=1]] {{Replacement}}" // edit with relative line numbers
    );
    verifier.reportIssue("Issue").onRange(1, 1, 1, 1).addQuickFix(
      mockQf("first", mockEdit(3, 1, 4, 1, "Replacement")) //edit with absolute line numbers
    );
    verifier.assertOneOrMoreIssues();

    var verifier2 = getVerifierWithComments(
      "Noncompliant@+1 [[quickfixes=qf1]]", // issue's at line 1, but starts at +1, so startLine = 2
      "empty line", // empty line 1
      "fix@qf1 {{first}}",
      "edit@qf1 [[sl=-1;sc=1;el=+0;ec=1]] {{Replacement}}" // edit should be startLine-1 = 1, endLine+0 = 2
    );
    verifier2.reportIssue("Issue").onRange(2, 1, 2, 1).addQuickFix(
      mockQf("first", mockEdit(1, 1, 2, 1, "Replacement")) //edit with absolute line numbers
    );
    verifier2.assertOneOrMoreIssues();

    var verifier3 = getVerifierWithComments(
      "Noncompliant@+1 [[sc=3;ec=3;quickfixes=qf1]]", // issue's at line 1, but starts at +1, so startLine = 2
      "empty line", // empty line 1
      "fix@qf1 {{first}}",
      "edit@qf1 [[sl=2;sc=1;el=2;ec=1]] {{Replacement}}"
    );
    verifier3.reportIssue("Issue").onRange(2, 3, 2, 3).addQuickFix(
      mockQf("first", mockEdit(2, 1, 2, 1, "Replacement")) //edit with absolute line numbers
    );
    verifier3.assertOneOrMoreIssues();
  }

  @Test
  public void test_multiple_quickfixes_per_issue() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[quickfixes=qf1,qf2]]",
      "fix@qf1 {{first}}",
      "edit@qf1 [[sc=1;ec=1]] {{Replacement}}",
      "fix@qf2 {{second}}",
      "edit@qf2 [[sl=+4;sc=3;el=+4;ec=3]] {{Replacement2}}"
    );
    verifier.reportIssue("Issue").onRange(1, 1, 1, 1)
      .addQuickFix(mockQf("first", mockEdit(1, 1, 1, 1, "Replacement")))
      .addQuickFix(mockQf("second", mockEdit(5, 3, 5, 3, "Replacement2")));
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_expected_qfs_when_no_issue_is_expected() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[quickfixes=qf1,qf2]]",
      "fix@qf1 {{first}}",
      "edit@qf1 [[sc=1;ec=1]] {{Replacement}}",
      "fix@qf2 {{second}}",
      "edit@qf2 [[sl=+4;sc=3;el=+4;ec=3]] {{Replacement2}}"
    );
    verifier.assertNoIssuesRaised();
  }

  @Test
  public void test_more_quickfixes_than_expected() {
    var verifier = getVerifierWithComments(
      "Noncompliant"
    );
    verifier.reportIssue("Issue").onRange(1, 1, 1, 1)
      .addQuickFix(mockQf("first", mockEdit(1, 1, 1, 1, "Replacement")));
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_expecting_no_quickfixes() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[quickfixes=!]]"
    );
    verifier.reportIssue("Issue").onRange(1, 1, 1, 1)
      .addQuickFix(mockQf("first", mockEdit(1, 1, 1, 1, "Replacement")));
    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("Issue at line 1 was expecting to have no quickfixes but had 1");

    var verifier2 = getVerifierWithComments(
      "Noncompliant [[quickfixes=!]]"
    );
    verifier2.reportIssue("Issue").onRange(1, 1, 1, 1);
    verifier2.assertOneOrMoreIssues();
  }

  @Test
  public void test_quickfixes_not_provided() {
    var verifier = getVerifierWithComments(
      "Noncompliant@+1 {{Issue}} [[sl=2;sc=1;el=2;ec=1;quickfixes=qf1]]",
      "fix@qf1 {{first}}",
      "edit@qf1 [[sl=+2;sc=1;el=+3;ec=1]] {{Replacement}}"
    );
    verifier.reportIssue("Issue").onRange(2, 1, 2, 1);
    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("Expected quickfix qf1 at line 2 was not matched by any provided quickfixes");
  }

  @Test
  public void test_different_issues_on_same_line_with_quickfixes() {
    var verifier = getVerifierWithComments(
      "Noncompliant@+1 {{Issue}} [[sc=1;ec=1;quickfixes=qf1]]",
      "Noncompliant {{Issue2}} [[sc=1;ec=1;quickfixes=qf2]]",
      "fix@qf1 {{first}}",
      "edit@qf1 [[sc=1;ec=1]] {{Replacement}}",
      "fix@qf2 {{second}}",
      "edit@qf2 [[sc=1;ec=1]] {{Replacement2}}"
    );
    verifier.reportIssue("Issue").onRange(2, 123, 2, 321);
    verifier.reportIssue("Issue2").onRange(2, 1, 2, 112).addQuickFix(
      mockQf("second", mockEdit(2, 1, 2, 1, "Replacement2"))
    );
    //This test is not supposed to pass, the issue parsing only collects multiple issue messages for the same line
    //it does not care about columns or quickfixes for the first one
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_without_quickfixes_enabled() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withoutQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue("issue").onRange(3, 18, 3, 23);
    verifier.reportIssue("Without quickfix").onLine(8);
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_actual_missing_edits() {
    Path path = Paths.get("src/test/resources/quickfixes/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8);

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    QuickFix qf1 = mockQf("Move \"bar\" on the left side of .equals");

    verifier.reportIssue("issue").onRange(3, 18, 3, 23).addQuickFix(qf1);
    verifier.reportIssue("Without quickfix").onLine(8);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining(String.format(
        "Expected quickfix qf1 at line 3 was not matched by any provided quickfixes %n" +
          "Expected description: {{Move \"bar\" on the left side of .equals}} %n" +
          "Expected edits: %n" +
          "(3:18)-(3:23) -> \"foo\\n\"%n" +
          "(3:5)-(3:10) -> \"bar\"")
      );
  }

  @Test
  public void test_expected_qf_missing_edits() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=0;ec=0;quickfixes=qf1]]",
      "fix@qf1 {{Expected description}}"
    );

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Quick fix edits not found for quick fix id qf1");
  }

  @Test
  public void test_expected_qf_missing_description() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=0;ec=0;quickfixes=qf1]]",
      "edit@qf1 [[sl=1;sc=1;el=1;ec=1]] {{replacement}}"
    );

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Quick fix description not found for quick fix id");
  }

  @Test
  public void test_wrong_description() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]",
      "fix@qf1 {{Expected description}}",
      "edit@qf1 [[sc=1;ec=1]] {{foo}}"
    );

    TextEdit edit1 = mockEdit(1, 1, 1, 1, "foo");
    QuickFix qf1 = mockQf("Wrong description", edit1);
    verifier.reportIssue("issue").onRange(1, 1, 1, 1).addQuickFix(qf1);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining(String.format(
        "Expected quickfix qf1 at line 1 was not matched by any provided quickfixes %n" +
          "Expected description: {{Expected description}} %n" +
          "Expected edits: %n" +
          "(1:1)-(1:1) -> foo"));
  }

  @Test
  public void test_wrong_number_of_edits() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 [[sc=1;ec=1]] {{foo}}");

    TextEdit edit1 = mockEdit(1, 1, 1, 3, "foo");
    TextEdit edit2 = mockEdit(1, 1, 1, 3, "goo");
    QuickFix qf1 = mockQf("Description", edit1, edit2);
    verifier.reportIssue("issue").onRange(1, 1, 1, 3).addQuickFix(qf1);

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("Expected quickfix qf1 at line 1 was not matched by any provided quickfixes");
  }

  @Test
  public void test_missing_expected_replacement() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=1;ec=3;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 [[sc=1;ec=1]]"
    );
    verifier.reportIssue("").onRange(1, 1, 1, 3).addQuickFix(
      mockQf("Description", mockEdit(1, 1, 1, 1, ""))
    );
    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Missing replacement for edit at line");
  }

  @Test
  public void test_wrong_text_edit_format() {
    var verifier = getVerifierWithComments(
      "Noncompliant [[sc=3;ec=5;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 [[sc=1;ec=1 {{Replacement}}"
    );

    assertThatThrownBy(verifier::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Invalid quickfix edit format: edit@qf1 [[sc=1;ec=1 {{Replacement}}");

    var verifier2 = getVerifierWithComments(
      "Noncompliant [[sc=3;ec=5;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 sc=1;ec=1]] {{Replacement}}"
    );

    assertThatThrownBy(verifier2::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Invalid quickfix edit format: edit@qf1 sc=1;ec=1]] {{Replacement}}");

    var verifier3 = getVerifierWithComments(
      "Noncompliant [[sc=3;ec=5;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 [[sc=1;ec=1]] {{Replacement"
    );

    assertThatThrownBy(verifier3::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Wrong format in comment: edit@qf1 [[sc=1;ec=1]] {{Replacement");

    var verifier4 = getVerifierWithComments(
      "Noncompliant [[sc=3;ec=5;quickfixes=qf1]]",
      "fix@qf1 {{Description}}",
      "edit@qf1 [[sc=1;cc=1]] {{Replacement}}"
    );

    assertThatThrownBy(verifier4::assertOneOrMoreIssues)
      .hasMessageContaining("[Quick Fix] Invalid quickfix edit format: edit@qf1 [[sc=1;cc=1]] {{Replacement}}");
  }

  private static SingleFileVerifier getVerifier() {
    return SingleFileVerifier.create(Paths.get("src/test/resources/empty.js"), UTF_8);
  }

  private static SingleFileVerifier getVerifierWithComments(String... comments) {
    var verifier = getVerifier();
    for (int i = 0; i < comments.length; i++) {
      verifier.addComment(1 + i, 1, comments[i], 0, 0);
    }
    return verifier;
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
