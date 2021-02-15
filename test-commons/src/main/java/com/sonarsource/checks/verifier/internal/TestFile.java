/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2021 SonarSource SA
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

import com.sonarsource.checks.verifier.FileContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

class TestFile extends FileContent {

  private final Map<Integer, List<Comment>> commentListPreLineMap = new HashMap<>();

  private final String lineNumberPrefixFormat;

  TestFile(FileContent file) {
    super(file.getPath(), file.getContent());
    int maxLineNumberLength = String.valueOf(file.getLines().length).length();
    lineNumberPrefixFormat = "%0" + Math.max(3, maxLineNumberLength) + "d: ";
  }

  String linePrefix(int lineNumber) {
    return String.format(lineNumberPrefixFormat, lineNumber);
  }

  void addNoncompliantComment(Comment comment) {
    if (!comment.path.equals(getPath())) {
      throw new IllegalStateException("This comment is not related to file '" + getPath() + "' but '" + comment.path + "'");
    }
    commentListPreLineMap.computeIfAbsent(comment.line, key -> new ArrayList<>()).add(comment);
  }

  String line(int lineNumber) {
    if (lineNumber < 1 || lineNumber > getLines().length) {
      throw new IllegalStateException("No line " + lineNumber + " in " + getName());
    }
    return getLines()[lineNumber - 1];
  }

  String lineWithoutNoncompliantComment(int line) {
    String code = line(line);
    List<Comment> comments = commentListPreLineMap.get(line);
    if (comments != null) {
      for (Comment comment : comments) {
        code = hideComment(code, comment);
      }
    }
    // Replace tabulation by a visible char to better understand report alignment problems
    return code.replace('\t', '➞');
  }

  private static String hideComment(String code, Comment comment) {
    if ((comment.column - 1) < code.length()) {
      int end = comment.column - 1;
      while (end > 0 && code.charAt(end - 1) == ' ') {
        end--;
      }
      return code.substring(0, end);
    }
    return code;
  }

  @Nullable
  String commentAt(int line) {
    List<Comment> comments = commentListPreLineMap.get(line);
    if (comments != null && !comments.isEmpty()) {
      return line(line).substring(comments.get(0).column - 1);
    }
    return null;
  }

}
