/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileContent {

  private final Path path;
  private final String content;
  private String[] lines = null;

  public FileContent(Path path) {
    this(path, StandardCharsets.UTF_8);
  }

  public FileContent(Path path, Charset charset) {
    this(path, read(path, charset));
  }

  public FileContent(Path path, String content) {
    this.path = path;
    this.content = content;
  }

  public String getName() {
    return path.getFileName().toString();
  }

  public Path getPath() {
    return path;
  }

  public File getFile() {
    return path.toFile();
  }

  public String getContent() {
    return content;
  }

  public String[] getLines() {
    if (lines == null) {
      lines = content.split("\r?\n|\r", -1);
    }
    return lines;
  }

  private static String read(Path path, Charset charset) {
    try {
      return new String(Files.readAllBytes(path), charset);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read '" + path + "': " + e.getMessage(), e);
    }
  }

}
