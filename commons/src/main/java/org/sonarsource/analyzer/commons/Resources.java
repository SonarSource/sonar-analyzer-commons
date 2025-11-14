/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

final class Resources {

  private static final int BUFFER_SIZE = 4_096;

  private Resources() {
    // utility
  }

  /**
   * Reads all characters from a resource class path into a {@link String}, using the given
   * character set.
   *
   * @param path the resource path to read from
   * @param charset the character set used when reading the resource
   * @return a string containing all the characters from the resource
   * @throws IOException if an I/O error occurs.
   */
  static String toString(String path, Charset charset) throws IOException {
    if (!path.startsWith("/")) {
      // to make sure it is always going to be considered as absolute
      path = "/" + path;
    }
    try (InputStream input = Resources.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Resource not found in the classpath: " + path);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
        out.write(buffer, 0, read);
      }
      return new String(out.toByteArray(), charset);
    }
  }

}
