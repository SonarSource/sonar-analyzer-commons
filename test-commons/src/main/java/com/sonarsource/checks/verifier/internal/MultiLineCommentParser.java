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

import com.sonarsource.checks.verifier.FileContent;
import java.util.ArrayList;
import java.util.List;

public class MultiLineCommentParser implements Comment.Parser {

  public final String commentPrefix;
  public final String commentSuffix;

  public MultiLineCommentParser(String commentPrefix, String commentSuffix) {
    this.commentPrefix = commentPrefix;
    this.commentSuffix = commentSuffix;
  }

  @Override
  public List<Comment> parse(FileContent file) {
    String content = file.getContent();
    StringPosition position = new StringPosition(content);
    List<Comment> comments = new ArrayList<>();
    int commentStartIndex = content.indexOf(commentPrefix);
    while (commentStartIndex != -1) {
      position.forwardTo(commentStartIndex);
      int commentEndIndex = content.indexOf(commentSuffix, commentStartIndex + commentPrefix.length());
      if (commentEndIndex == -1) {
        throw new IllegalStateException("Missing multi-line comment suffix " + commentSuffix + " for the comment starting at line : " + position.getLine());
      }
      int contentColumn = position.getColumn() + commentPrefix.length();
      String commentContent = content.substring(commentStartIndex + commentPrefix.length(), commentEndIndex);
      comments.add(new Comment(file.getPath(), position.getLine(), position.getColumn(), contentColumn, commentContent));
      commentStartIndex = content.indexOf(commentPrefix, commentEndIndex + commentSuffix.length());
    }
    return comments;
  }

  private static class StringPosition {
    private final String content;
    private int offset = 0;
    private int line = 1;
    private int column = 1;
    private boolean newLine = false;

    public StringPosition(String content) {
      this.content = content;
    }

    public char charAt(int index) {
      return index < content.length() ? content.charAt(index) : 0;
    }

    public void forwardTo(int newOffset) {
      if (newOffset < offset) {
        throw new IllegalArgumentException("Can only move forward, offset: " + offset + ", newOffset: " + newOffset);
      }
      while (offset < newOffset) {
        offset++;
        if (newLine) {
          line++;
          column = 1;
        } else {
          column++;
        }
        newLine = (charAt(offset) == '\r' && charAt(offset + 1) != '\n') || charAt(offset) == '\n';
      }
    }

    public int getLine() {
      return line;
    }

    public int getColumn() {
      return column;
    }
  }

}
