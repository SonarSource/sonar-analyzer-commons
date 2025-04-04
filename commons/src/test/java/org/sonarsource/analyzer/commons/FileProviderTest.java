/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons;

import java.io.File;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileProviderTest {

  private static final File baseDir = new File("src/test/resources/org/sonarsource/analyzer/commons/scanner").getAbsoluteFile();
  private static final File file = new File(baseDir, "dir/f1.txt");

  @Test
  public void noMatchedFile() {
    assertThat(getMatchingFiles("dir/xxx")).isEmpty();
  }

  @Test
  public void simpleFile() {
    assertThat(getMatchingFiles("dir/f1.txt")).containsOnly(file);
  }

  @Test
  public void wildCard() {
    assertThat(getMatchingFiles("*/f1.txt")).containsOnly(file);
    assertThat(getMatchingFiles("**/f1.txt")).containsOnly(file, new File(baseDir, "dir/subdir/f1.txt"));
  }

  @Test
  public void test_nonexistent() {
    var provider = new FileProvider(new File("not found"), "*/**");
    assertThatThrownBy(provider::getMatchingFiles)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to get matching files.");
  }


  private List<File> getMatchingFiles(String pattern) {
    return scan(pattern, baseDir);
  }

  private static List<File> scan(String pattern, File dir) {
    FileProvider scanner = new FileProvider(dir, pattern);
    return scanner.getMatchingFiles();
  }

}
