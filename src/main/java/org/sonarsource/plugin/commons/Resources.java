/*
 * SonarQube Plugin Commons
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
package org.sonarsource.plugin.commons;

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
    try (InputStream input = Resources.class.getClassLoader().getResourceAsStream(path)) {
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
