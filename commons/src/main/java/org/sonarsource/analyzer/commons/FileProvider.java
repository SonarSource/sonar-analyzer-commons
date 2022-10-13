/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.sonar.api.utils.WildcardPattern;

public class FileProvider {

  private final Path baseDir;
  private final WildcardPattern pattern;

  public FileProvider(File baseDir, String pattern) {
    this.baseDir = baseDir.toPath();
    this.pattern = WildcardPattern.create(pattern);
  }

  public List<File> getMatchingFiles() {
    try (var walk = Files.walk(baseDir)) {
      return walk
              .filter(p -> !Files.isDirectory(p) && pattern.match(toUnixString(baseDir.relativize(p))))
              .map(Path::toFile)
              .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get matching files.", e);
    }
  }

  private static String toUnixString(Path path) {
    return path.toString().replace('\\', '/');
  }

}
