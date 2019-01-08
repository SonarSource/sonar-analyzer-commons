/*
 * SonarQube Analyzer Test Commons
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
package com.sonarsource.checks.verifier.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static com.sonarsource.checks.verifier.internal.PreciseLocationParser.LINE_ADJUSTMENT;

public final class NoncompliantCommentParser {

  private static final Pattern START_WITH_NONCOMPLIANT = Pattern.compile("^(?i) *+Noncompliant");

  private static final Pattern NON_COMPLIANT_REGEX = Pattern.compile("(?i) *+Noncompliant" + LINE_ADJUSTMENT
  // issue count, ex: 2
    + "(?: ++(?<issueCount>\\d++))?"
    + " *+"
    // messages, ex: {{msg1}} {{msg2}}
    + "(?<messages>(\\{\\{.*?\\}\\} *+)+)?"
    // params, ex: [[effortToFix=2;id=main]]
    + "(?:\\[\\[(?<params>[^\\]]++)\\]\\] *+)?"
    + "(?:\r(\n?)|\n)?");

  private NoncompliantCommentParser() {
    // utility class
  }

  @Nullable
  public static LineIssues parse(TestFile testFile, int line, String commentContent) {
    if (START_WITH_NONCOMPLIANT.matcher(commentContent).find()) {
      Matcher matcher = NON_COMPLIANT_REGEX.matcher(commentContent);
      if (!matcher.matches()) {
        throw new IllegalStateException("Invalid comment format line " + line + ": " + commentContent);
      }
      int effectiveLine = PreciseLocationParser.extractEffectiveLine(line, matcher);
      String[] messages = extractMessages(line, matcher.group("issueCount"), matcher.group("messages"));
      Map<String, String> params = extractParams(matcher.group("params"));
      return new LineIssues(testFile, effectiveLine, messages, params, null);
    }
    return null;
  }

  static Map<String, String> extractParams(@Nullable String paramGroup) {
    if (paramGroup == null) {
      return Collections.emptyMap();
    }
    return Arrays.stream(paramGroup.trim().split(";"))
      .map(s -> s.split("=", 2))
      .collect(Collectors.toMap(arr -> arr[0], arr -> arr.length == 2 ? arr[1] : ""));
  }

  private static String[] extractMessages(int line, @Nullable String issueCountGroup, @Nullable String messageGroup) {
    if (messageGroup != null) {
      if (issueCountGroup != null) {
        throw new IllegalStateException("Error, you can not specify issue count and messages at line " + line +
          ", you have to choose either: \n" +
          "  Noncompliant " + issueCountGroup + "\n" +
          "or\n" +
          "  Noncompliant " + messageGroup + "\n");
      }
      String messageContent = messageGroup.trim();
      return messageContent.substring(2, messageContent.length() - 2)
        .split("\\}\\} *+\\{\\{");
    }
    int issueCount = issueCountGroup == null ? 1 : Integer.parseInt(issueCountGroup);
    return new String[issueCount];
  }
}
