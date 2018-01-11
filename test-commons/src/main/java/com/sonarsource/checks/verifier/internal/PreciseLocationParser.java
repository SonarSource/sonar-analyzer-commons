/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PreciseLocationParser {

  private static final Pattern TRIGGER = Pattern.compile("^ *+\\^");

  // line adjustment, ex: @+1
  public static final String LINE_ADJUSTMENT = "(?:@(?<lineAdjustment>(?<relativeAdjustment>[+-])?\\d++))?";

  private static final Pattern LOCATION_REGEX = Pattern.compile(" *+" +
  // highlighted range, ex: ^^^^ |OR| ^^^@12 |OR| ^^^@-2
    "(?<range>\\^(?:\\[(?<params>[^\\]]++)\\]|\\^++)?)" + LINE_ADJUSTMENT +
    // count, ex: 3 |OR| direction, ex: < |OR| direction with index, ex: < 1 |OR| direction and flowId, ex: < 2.1
    " *+(?:(?<count>\\d++)|(?:(?<direction><|>) *+((?<majorIndex>\\d++)(\\.(?<minorIndex>\\d++))?)?))?" +
    // message, ex: {{msg}}
    " *+(?:\\{\\{(?<message>.*?)\\}\\})? *+" +
    "(?:\r(\n?)|\n)?");

  private PreciseLocationParser() {
    // utility class
  }

  public static List<PreciseLocation> parse(int line, int column, String commentContent) {
    if (TRIGGER.matcher(commentContent).lookingAt()) {
      List<PreciseLocation> result = new ArrayList<>();
      Matcher matcher = LOCATION_REGEX.matcher(commentContent);
      matcher.region(0, commentContent.length());
      while (matcher.lookingAt()) {
        result.add(matcherToLocation(line, column, matcher));
        matcher.region(matcher.end(), commentContent.length());
      }
      if (matcher.regionStart() != commentContent.length()) {
        String position = "line " + line + " col " + (column + matcher.regionStart());
        throw new IllegalStateException("Precise Location: unexpected character found at " + position + " in: " + commentContent);
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static PreciseLocation matcherToLocation(int line, int column, Matcher matcher) {
    int effectiveLine = extractEffectiveLine(line - 1, matcher);
    UnderlinedRange range = fileRange(effectiveLine, column, matcher);
    String direction = matcher.group("direction");
    String minorIndexGroup = matcher.group("minorIndex");
    if (direction == null) {
      String countGroup = matcher.group("count");
      Integer additionalCount = countGroup == null ? null : Integer.valueOf(countGroup);
      return new PrimaryLocation(range, additionalCount);
    } else if (minorIndexGroup == null) {
      String majorIndex = matcher.group("majorIndex");
      Integer index = majorIndex == null ? null : Integer.valueOf(majorIndex);
      return new SecondaryLocation(range, direction.equals("<"), index, matcher.group("message"));
    } else {
      int majorIndex = Integer.parseInt(matcher.group("majorIndex"));
      int minorIndex = Integer.parseInt(minorIndexGroup);
      return new FlowLocation(range, direction.equals("<"), majorIndex, minorIndex, matcher.group("message"));
    }
  }

  public static int extractEffectiveLine(int line, Matcher matcher) {
    String lineAdjustmentGroup = matcher.group("lineAdjustment");
    String relativeAdjustmentGroup = matcher.group("relativeAdjustment");
    int referenceLine = relativeAdjustmentGroup != null ? line : 0;
    return lineAdjustmentGroup == null ? line : (referenceLine + Integer.parseInt(lineAdjustmentGroup));
  }

  private static UnderlinedRange fileRange(int line, int column, Matcher matcher) {
    int rangeLine = line;
    int rangeColumn = column + matcher.start("range");
    int rangeEndLine = line;
    int rangeEndColumn = column + matcher.end("range") - 1;
    String params = matcher.group("params");
    if (params != null) {
      rangeEndColumn = rangeColumn;
      Map<String, String> paramMap = NoncompliantCommentParser.extractParams(params);
      rangeLine = consumePropertyAndAdjustValue(rangeLine, paramMap, "sl");
      rangeColumn = consumePropertyAndAdjustValue(rangeColumn, paramMap, "sc");
      rangeEndColumn = consumePropertyAndAdjustValue(rangeEndColumn, paramMap, "ec");
      rangeEndLine = consumePropertyAndAdjustValue(rangeEndLine, paramMap, "el");
      if (!paramMap.isEmpty()) {
        throw new IllegalStateException("Unknown attributes at line " + line + " in: " + params);
      }
    }
    return new UnderlinedRange(rangeLine, rangeColumn, rangeEndLine, rangeEndColumn);
  }

  private static int consumePropertyAndAdjustValue(int referenceValue, Map<String, String> paramMap, String propertyName) {
    String shift = paramMap.remove(propertyName);
    if (shift == null) {
      return referenceValue;
    }
    if (shift.startsWith("-") || shift.startsWith("+")) {
      return referenceValue + Integer.parseInt(shift.substring(1));
    } else {
      return Integer.parseInt(shift);
    }
  }
}
