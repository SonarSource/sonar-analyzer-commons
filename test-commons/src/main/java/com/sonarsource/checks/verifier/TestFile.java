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
package com.sonarsource.checks.verifier;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

public class TestFile {

  public final String name;

  public final String content;

  public final String[] lines;

  public final String commentPrefix;

  public TestFile(String name, String commentPrefix, String content) {
    this.name = name;
    this.content = content;
    this.commentPrefix = commentPrefix;
    lines = content.split("\r\n|\n|\r", -1);
  }

  public static TestFile read(Path path, Charset charset, String commentPrefix) {
    String name = path.getFileName().toString();
    try {
      return new TestFile(name, commentPrefix, new String(Files.readAllBytes(path), charset));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read '" + name + "': " + e.getMessage(), e);
    }
  }

  public String line(int lineNumber) {
    if (lineNumber < 1 || lineNumber > lines.length) {
      throw new IllegalStateException("No line " + lineNumber + " in " + name);
    }
    return lines[lineNumber - 1];
  }

  public String lineWithoutComment(int line) {
    String code = line(line);
    int commentPos = code.indexOf(commentPrefix);
    if (commentPos != -1) {
      while(commentPos > 0 && code.charAt(commentPos - 1) == ' ') {
        commentPos--;
      }
      code = code.substring(0, commentPos);
    }
    return code;
  }

  @Nullable
  public String commentAt(int line) {
    String code = line(line);
    int commentPos = code.indexOf(commentPrefix);
    if (commentPos == -1) {
      return null;
    }
    return code.substring(commentPos);
  }

}
