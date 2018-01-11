/*
 * SonarQube Analyzer Commons
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
package org.sonarsource.analyzer.commons;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InputFileContentExtractorTest {

  private File baseDir = new File("src/test/resources/org/sonarsource/analyzer/commons");
  private File file = new File(baseDir, "InputFileContentExtractor.txt");
  private SensorContextTester context = SensorContextTester.create(baseDir);
  private InputFile inputFile = mock(InputFile.class);

  @Test
  public void sq56() throws Exception {
    setRuntime(Version.create(5, 6));
    setFileSystemEncoding(StandardCharsets.UTF_8);
    when(inputFile.file()).thenReturn(file);
    Assertions.assertThat(content(inputFile)).isEqualTo("Hello!\n");
  }

  @Test
  public void sq60() throws Exception {
    setRuntime(Version.create(6, 0));
    setFileSystemEncoding(StandardCharsets.UTF_16);
    when(inputFile.file()).thenReturn(file);
    when(inputFile.charset()).thenReturn(StandardCharsets.UTF_8);
    Assertions.assertThat(content(inputFile)).isEqualTo("Hello!\n");
  }

  @Test
  public void sq62() throws Exception {
    setRuntime(Version.create(6, 2));
    when(inputFile.contents()).thenReturn("Hello 6.2!");
    Assertions.assertThat(content(inputFile)).isEqualTo("Hello 6.2!");
  }

  private String content(InputFile inputFile) throws IOException {
    return new InputFileContentExtractor(context).content(inputFile);
  }

  private void setRuntime(Version version) {
    context.setRuntime(SonarRuntimeImpl.forSonarQube(version, SonarQubeSide.SERVER));
  }

  private void setFileSystemEncoding(Charset fileSystemEncoding) {
    DefaultFileSystem fs = mock(DefaultFileSystem.class);
    when(fs.encoding()).thenReturn(fileSystemEncoding);
    context.setFileSystem(fs);
  }
}
