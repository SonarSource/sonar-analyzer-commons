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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class ReportDiff {

  public static String diff(String expected, String actual) {
    StringBuilder out = new StringBuilder();
    List<DiffBlock> blocks = diff(new LineBlock(expected), new LineBlock(actual));
    for (int i = 0; i < blocks.size(); i++) {
      boolean isLastBlock = i == blocks.size() - 1;
      blocks.get(i).print(out, isLastBlock);
    }
    return out.toString();
  }

  private static List<DiffBlock> diff(LineBlock expected, LineBlock actual) {
    if (expected.lines.isEmpty() && actual.lines.isEmpty()) {
      return Collections.emptyList();
    }
    if (actual.lines.isEmpty()) {
      return Collections.singletonList(new DiffBlock(DiffType.DELETE, expected.lines));
    }
    if (expected.lines.isEmpty()) {
      return Collections.singletonList(new DiffBlock(DiffType.INSERT, actual.lines));
    }
    if (expected.lines.equals(actual.lines)) {
      return Collections.singletonList(new DiffBlock(DiffType.EQUAL, actual.lines));
    }
    CommonBlock commonBlock = largestCommonBlock(expected, actual);
    if (commonBlock == null) {
      return Arrays.asList(
        new DiffBlock(DiffType.DELETE, expected.lines),
        new DiffBlock(DiffType.INSERT, actual.lines));
    }
    List<DiffBlock> result = new ArrayList<>();
    result.addAll(diff(commonBlock.left.before(), commonBlock.right.before()));
    result.add(new DiffBlock(DiffType.EQUAL, commonBlock.lines()));
    result.addAll(diff(commonBlock.left.after(), commonBlock.right.after()));
    return result;
  }

  @Nullable
  private static CommonBlock largestCommonBlock(LineBlock left, LineBlock right) {
    CommonBlock largestCommon = null;
    for (int startLeft = 0; startLeft < left.lines.size(); startLeft++) {
      for (int startRight = 0; startRight < right.lines.size(); startRight++) {
        CommonBlock common = commonBlock(startLeft, left, startRight, right);
        if (largestCommon == null || (common != null && largestCommon.size() < common.size())) {
          largestCommon = common;
        }
      }
    }
    return largestCommon;
  }

  @Nullable
  private static CommonBlock commonBlock(int startLeft, LineBlock left, int startRight, LineBlock right) {
    int size = 0;
    while (startLeft + size < left.lines.size() &&
      startRight + size < right.lines.size() &&
      left.lines.get(startLeft + size).equals(right.lines.get(startRight + size))) {
      size++;
    }
    if (size == 0) {
      return null;
    }
    return new CommonBlock(new SubLineBlock(left, startLeft, size), new SubLineBlock(right, startRight, size));
  }

  static class LineBlock {
    final List<String> lines;

    LineBlock(String text) {
      lines = Arrays.asList(text.split("\n"));
    }

    LineBlock(List<String> lines) {
      this.lines = lines;
    }
  }

  static class SubLineBlock {
    final LineBlock block;
    final int start;
    final int size;

    SubLineBlock(LineBlock block, int start, int size) {
      this.block = block;
      this.start = start;
      this.size = size;
    }

    LineBlock before() {
      return new LineBlock(block.lines.subList(0, start));
    }

    LineBlock after() {
      return new LineBlock(block.lines.subList(start + size, block.lines.size()));
    }
  }

  enum DiffType {
    EQUAL("  "), INSERT("+ "), DELETE("- ");

    final String prefix;

    DiffType(String prefix) {
      this.prefix = prefix;
    }
  }

  static class DiffBlock extends LineBlock {
    final DiffType type;

    DiffBlock(DiffType type, List<String> lines) {
      super(lines);
      this.type = type;
    }

    void print(StringBuilder out, boolean isLastBlock) {
      if (type == DiffType.EQUAL && !lines.isEmpty()) {
        String lastLine = lines.get(lines.size() - 1);
        if (!isLastBlock && !lastLine.trim().isEmpty()) {
          print(out, lastLine);
        }
      } else {
        lines.forEach(line -> print(out, line));
      }
    }

    void print(StringBuilder out, String line) {
      out.append(type.prefix).append(line).append('\n');
    }
  }

  static class CommonBlock {
    private final SubLineBlock left;
    private final SubLineBlock right;

    CommonBlock(SubLineBlock left, SubLineBlock right) {
      this.left = left;
      this.right = right;
    }

    List<String> lines() {
      return left.block.lines.subList(left.start, left.start + left.size);
    }

    int size() {
      return left.size;
    }
  }

}
