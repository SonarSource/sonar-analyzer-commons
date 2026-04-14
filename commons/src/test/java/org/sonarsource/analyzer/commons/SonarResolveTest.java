/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.analyzer.commons;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonar.api.batch.sensor.issue.IssueResolution;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SonarResolveTest {

  @ParameterizedTest
  @MethodSource("singleLineSuccessCases")
  void parse_supports_single_line_syntax(String directive, int line, SonarResolve expected) {
    assertThat(parseSingleLine(directive, line)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("singleLineFailureCases")
  void parse_fails_for_invalid_single_line_syntax(String directive, String expectedErrorMessage) {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);

    SonarResolve.StreamingParser.State state = parser.consumeLine(42, directive);
    if (state == SonarResolve.StreamingParser.State.INCOMPLETE) {
      state = parser.finish();
    }

    assertThat(state).isEqualTo(SonarResolve.StreamingParser.State.INVALID);
    assertThat(parser.errorMessage()).isEqualTo(expectedErrorMessage);
  }

  @ParameterizedTest
  @MethodSource("multiLineSuccessCases")
  void consume_line_supports_multi_line_syntax(String[] lines, int line, SonarResolve expected) {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(line);
    for (int i = 0; i < lines.length - 1; i++) {
      assertThat(parser.consumeLine(line + i, lines[i])).isEqualTo(SonarResolve.StreamingParser.State.INCOMPLETE);
    }
    assertThat(parser.consumeLine(line + lines.length - 1, lines[lines.length - 1])).isEqualTo(SonarResolve.StreamingParser.State.COMPLETE);
    assertThat(parser.result()).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("multiLineFailureCases")
  void consume_line_fails_for_invalid_multi_line_syntax(String[] lines, String expectedErrorMessage) {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);
    for (int i = 0; i < lines.length - 1; i++) {
      assertThat(parser.consumeLine(42 + i, lines[i])).isEqualTo(SonarResolve.StreamingParser.State.INCOMPLETE);
    }

    SonarResolve.StreamingParser.State state = parser.consumeLine(42 + lines.length - 1, lines[lines.length - 1]);
    if (state == SonarResolve.StreamingParser.State.INCOMPLETE) {
      state = parser.finish();
    }

    assertThat(state).isEqualTo(SonarResolve.StreamingParser.State.INVALID);
    assertThat(parser.errorMessage()).isEqualTo(expectedErrorMessage);
  }

  @Test
  void finish_before_consuming_any_line_throws() {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);

    assertThatThrownBy(parser::finish)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot finish parser before consuming any lines.");
  }

  @Test
  void consume_line_after_complete_throws() {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);
    assertThat(parser.consumeLine(42, "sonar-resolve cpp:S100 \"reason\"")).isEqualTo(SonarResolve.StreamingParser.State.COMPLETE);

    assertThatThrownBy(() -> parser.consumeLine(43, "ignored"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot consume additional lines after parser reached a terminal state.");
  }

  @Test
  void consume_line_after_invalid_throws() {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);
    assertThat(parser.consumeLine(42, "sonar-resolve [accepted] cpp:S100 \"reason\"")).isEqualTo(SonarResolve.StreamingParser.State.INVALID);

    assertThatThrownBy(() -> parser.consumeLine(43, "ignored"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot consume additional lines after parser reached a terminal state.");
  }

  @Test
  void consume_line_preserves_empty_continuation_lines_inside_justification() {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(42);

    assertThat(parser.consumeLine(42, "sonar-resolve cpp:S100 \"line1")).isEqualTo(SonarResolve.StreamingParser.State.INCOMPLETE);
    assertThat(parser.consumeLine(43, "")).isEqualTo(SonarResolve.StreamingParser.State.INCOMPLETE);
    assertThat(parser.consumeLine(44, "line3\"")).isEqualTo(SonarResolve.StreamingParser.State.COMPLETE);

    assertThat(parser.result()).isEqualTo(
      new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line1\n\nline3"));
  }

  private static SonarResolve parseSingleLine(String directive, int line) {
    SonarResolve.StreamingParser parser = new SonarResolve.StreamingParser(line);
    assertThat(parser.consumeLine(line, directive)).isEqualTo(SonarResolve.StreamingParser.State.COMPLETE);
    return parser.result();
  }

  private static Stream<Arguments> singleLineSuccessCases() {
    return Stream.of(
      arguments(
        "sonar-resolve cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100, cpp:M23_123,c:S200 , objc:S300 \"line comment\"",
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.DEFAULT,
          Set.of(
            RuleKey.of("cpp", "S100"),
            RuleKey.of("cpp", "M23_123"),
            RuleKey.of("c", "S200"),
            RuleKey.of("objc", "S300")),
          "line comment")),
      arguments(
        "sonar-resolve [accept] cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve [fp] cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.FALSE_POSITIVE, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve [fp] cpp:S100, cpp:M23_123 \"line comment\"",
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.FALSE_POSITIVE,
          Set.of(RuleKey.of("cpp", "S100"), RuleKey.of("cpp", "M23_123")),
          "line comment")),
      arguments(
        "SONAR-RESOLVE cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "Sonar-Resolve [FP] cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.FALSE_POSITIVE, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sOnAr-ReSoLvE [AcCePt] cpp:S100 \"line comment\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100 `line comment`",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100 'line \\'comment\\''",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line 'comment'")),
      arguments(
        "sonar-resolve cpp:S100 (line comment)",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100 [line \\]comment]",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line ]comment")),
      arguments(
        "sonar-resolve cpp:S100 [line comment)]",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment)")),
      arguments(
        "sonar-resolve cpp:S100 {line comment}",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100 \"line \\\"comment\\\"\"",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line \"comment\"")),
      arguments(
        "sonar-resolve cpp:S100 \"line comment\" the rest is ignored",
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S100")), "line comment")),
      arguments(
        "sonar-resolve cpp:S100 \"line\\nline\\rline\\tline\\\\line\"",
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.DEFAULT,
          Set.of(RuleKey.of("cpp", "S100")),
          "line\nline\rline\tline\\line")));
  }

  private static Stream<Arguments> singleLineFailureCases() {
    return Stream.of(
      arguments("cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: missing 'sonar-resolve'"),
      arguments("sonar-resolvecpp:S100 \"line comment\"", "Invalid sonar-resolve directive: expected whitespace after 'sonar-resolve'"),
      arguments("sonar-resolve[fp] cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: expected whitespace after 'sonar-resolve'"),
      arguments("sonar-resolve \"line comment\"", "Invalid sonar-resolve directive: missing rule key"),
      arguments("sonar-resolve cppS1234 \"line comment\"", "Invalid sonar-resolve directive: invalid rule key 'cppS1234'"),
      arguments("sonar-resolve [accepted] cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: invalid status '[accepted]'"),
      arguments("sonar-resolve [Accepted] cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: invalid status '[Accepted]'"),
      arguments("sonar-resolve [fp cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: unterminated status"),
      arguments("sonar-resolve cpp:S100, cpp:S100 \"line comment\"", "Invalid sonar-resolve directive: duplicate rule key 'cpp:S100'"),
      arguments("sonar-resolve cpp:S100, \"line comment\"", "Invalid sonar-resolve directive: invalid rule key list"),
      arguments("sonar-resolve cpp:S100", "Invalid sonar-resolve directive: missing justification"),
      arguments("sonar-resolve cpp:S100 <line comment>", "Invalid sonar-resolve directive: missing justification"),
      arguments("sonar-resolve cpp:S100 \"line comment", "Invalid sonar-resolve directive: unterminated justification"),
      arguments("sonar-resolve cpp:S100 [line comment", "Invalid sonar-resolve directive: unterminated justification"),
      arguments("sonar-resolve cpp:S100 {line comment", "Invalid sonar-resolve directive: unterminated justification"));
  }

  private static Stream<Arguments> multiLineSuccessCases() {
    return Stream.of(
      arguments(
        new String[] {
          "SONAR-RESOLVE",
          "cpp:S1234 \"reason\""
        },
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S1234")), "reason")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S1234 \"reason",
          "reason\""
        },
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S1234")), "reason\nreason")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S1234 \"first",
          "second",
          "third\""
        },
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S1234")), "first\nsecond\nthird")),
      arguments(
        new String[] {
          "Sonar-Resolve",
          "[FP]",
          "cpp:S100,",
          "cpp:M23_123",
          "\"reason\""
        },
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.FALSE_POSITIVE,
          Set.of(RuleKey.of("cpp", "S100"), RuleKey.of("cpp", "M23_123")),
          "reason")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S100,",
          "cpp:M23_123 \"reason\""
        },
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.DEFAULT,
          Set.of(RuleKey.of("cpp", "S100"), RuleKey.of("cpp", "M23_123")),
          "reason")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S1234 \"prefix\\n",
          "middle\\t",
          "suffix\\\"\" the rest is ignored"
        },
        42,
        new SonarResolve(
          42,
          42,
          IssueResolution.Status.DEFAULT,
          Set.of(RuleKey.of("cpp", "S1234")),
          "prefix\n\nmiddle\t\nsuffix\"")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S1234 [first line",
          "second line]"
        },
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S1234")), "first line\nsecond line")),
      arguments(
        new String[] {
          "sonar-resolve cpp:S1234 [first line)",
          "second line]"
        },
        42,
        new SonarResolve(42, 42, IssueResolution.Status.DEFAULT, Set.of(RuleKey.of("cpp", "S1234")), "first line)\nsecond line")));
  }

  private static Stream<Arguments> multiLineFailureCases() {
    return Stream.of(
      arguments(
        new String[] {
          "sonar-resolve [f",
          "p] cpp:S100 \"reason\""
        },
        "Invalid sonar-resolve directive: invalid status '[f\np]'"),
      arguments(
        new String[] {
          "sonar-resolve",
          "\"reason\""
        },
        "Invalid sonar-resolve directive: missing rule key"),
      arguments(
        new String[] {
          "sonar-resolve cpp:S100,",
          "\"reason\""
        },
        "Invalid sonar-resolve directive: invalid rule key list"),
      arguments(
        new String[] {
          "sonar-resolve [fp",
          "cpp:S100 \"reason\""
        },
        "Invalid sonar-resolve directive: unterminated status"),
      arguments(
        new String[] {
          "sonar-resolve cpp:S100 \"reason",
          "still reason"
        },
        "Invalid sonar-resolve directive: unterminated justification"),
      arguments(
        new String[] {
          "sonar-resolve cpp:S100 [reason",
          "still reason"
        },
        "Invalid sonar-resolve directive: unterminated justification"));
  }
}
