/*
 * SonarSource Analyzers Test Commons
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

import com.sonarsource.checks.verifier.FileContent;
import java.util.ArrayList;
import java.util.List;

public class SingleLineCommentParser implements Comment.Parser {

  public final String commentPrefix;

  public SingleLineCommentParser(String commentPrefix) {
    this.commentPrefix = commentPrefix;
  }

  @Override
  public List<Comment> parse(FileContent file) {
    List<Comment> comments = new ArrayList<>();
    String[] lines = file.getContent().split("\r?\n|\r", -1);
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String line = lines[lineIndex];
      int commentIndex = line.indexOf(commentPrefix);
      if (commentIndex != -1) {
        int lineNumber = lineIndex + 1;
        int column = commentIndex + 1;
        int contentColumn = column + commentPrefix.length();
        String commentContent = line.substring(contentColumn - 1);
        comments.add(new Comment(file.getPath(), lineNumber, column, contentColumn, commentContent));
      }
    }
    return comments;
  }
}
