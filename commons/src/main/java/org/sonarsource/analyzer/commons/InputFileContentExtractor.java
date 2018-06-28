/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.Version;

/**
 * Use to provide compatibility between SQ API related to InputFile
 *
 * @deprecated since 1.8. This compatibility layer is not required as LTS 6.7 is available.
 */
@Deprecated
public class InputFileContentExtractor {

  public static final Version V6_0 = Version.create(6, 0);
  public static final Version V6_2 = Version.create(6, 2);

  private final SonarQubeAdapter sonarQubeAdapter;

  public InputFileContentExtractor(SensorContext sensorContext) {
    Version version = sensorContext.getSonarQubeVersion();
    if (version.isGreaterThanOrEqual(V6_2)) {
      this.sonarQubeAdapter = new SonarQube62Adapter();
    } else if (version.isGreaterThanOrEqual(V6_0)) {
      this.sonarQubeAdapter = new SonarQube60Adapter();
    } else {
      this.sonarQubeAdapter = new SonarQube56Adapter(sensorContext.fileSystem().encoding());
    }
  }

  public String content(InputFile inputFile) {
    try {
      return sonarQubeAdapter.content(inputFile);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read content of " + inputFile, e);
    }
  }

  private static String readFile(InputFile inputFile, Charset charset) throws IOException {
    return new String(Files.readAllBytes(inputFile.file().toPath()), charset);
  }

  private interface SonarQubeAdapter {
    String content(InputFile inputFile) throws IOException;
  }

  private static class SonarQube56Adapter implements SonarQubeAdapter {

    private final Charset fileSystemEncoding;

    public SonarQube56Adapter(Charset fileSystemEncoding) {
      this.fileSystemEncoding = fileSystemEncoding;
    }

    @Override
    public String content(InputFile inputFile) throws IOException {
      return readFile(inputFile, fileSystemEncoding);
    }
  }

  private static class SonarQube60Adapter implements SonarQubeAdapter {
    @Override
    public String content(InputFile inputFile) throws IOException {
      return readFile(inputFile, inputFile.charset());
    }
  }

  private static class SonarQube62Adapter implements SonarQubeAdapter {
    @Override
    public String content(InputFile inputFile) throws IOException {
      return inputFile.contents();
    }
  }

}
