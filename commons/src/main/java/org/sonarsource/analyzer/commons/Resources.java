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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class Resources {

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
    return toString(null, path, charset);
  }

  /**
   * Reads all characters from a resource class path into a {@link String}, using the given
   * character set.
   *
   * @param classLoader the class loader to be used when in the presence of a different context
   * @param path the resource path to read from
   * @param charset the character set used when reading the resource
   * @return a string containing all the characters from the resource
   * @throws IOException if an I/O error occurs.
   */
  static String toString(@Nullable Function<String, InputStream> resourceProvider, String path, Charset charset) throws IOException {
    resourceProvider = resourceProvider == null ? Resources.class.getClassLoader()::getResourceAsStream : resourceProvider;
    try (InputStream input = resourceProvider.apply(path)) {
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

  /**
   * Open an InputStream targeting a resource placed at a given path, using the rootClass resource loader to find the resource
   *
   * @param path  the resource path to read from
   * @param rootClass the class to use as base for the classloader
   * @return an inputStream reading in the resource, null if the resource can not be found
   */
  public static Function<String, InputStream> resourceProvider(Class<?> rootClass) {
    return path -> {
      try {
        URL resource = rootClass.getResource(path);
        if (resource != null) {
          return resource.openStream();
        }
      } catch (IOException e) {
        // Unable to read the resource - do nothing
      }
      // resource not found or unable to read
      return null;
    };
  }

}
