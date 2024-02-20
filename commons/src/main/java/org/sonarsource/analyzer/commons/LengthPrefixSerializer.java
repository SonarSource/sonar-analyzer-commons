/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2023 SonarSource SA
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LengthPrefixSerializer {

  /**
   * A utility class to de/serialize a List<byte[]> and Map<String, byte[]> from/to byte[].
   * Simple length-prefixed serialization is used without any integrity checks.
   * Lengths are saved as int. Thus, the maximum size of an entry is Integer.MAX_VALUE.
   */
  private LengthPrefixSerializer() {
    // utility
  }

  public static byte[] serializeList(List<byte[]> toSerializeList) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(byteArrayOutputStream)) {
      for (byte[] toSerializeElement : toSerializeList) {
        writeLengthAndBytes(dos, toSerializeElement);
      }
      return byteArrayOutputStream.toByteArray();
    }
  }

  public static List<byte[]> unserializeList(byte[] data) throws IOException {
    return unserializeList(new ByteArrayInputStream(data));
  }

  public static List<byte[]> unserializeList(InputStream data) throws IOException {
    List<byte[]> result = new ArrayList<>();
    try (DataInputStream dis = new DataInputStream(data)) {
      while (dis.available() > 0) {
        result.add(readBytes(dis));
      }
    }
    return result;
  }

  public static byte[] serializeMap(Map<String, byte[]> toSerializeMap) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(byteArrayOutputStream)) {
      for (Map.Entry<String, byte[]> toSerializeElement : toSerializeMap.entrySet()) {
        writeLengthAndBytes(dos, toSerializeElement.getKey().getBytes(StandardCharsets.UTF_8));
        writeLengthAndBytes(dos, toSerializeElement.getValue());
      }
      return byteArrayOutputStream.toByteArray();
    }
  }

  public static Map<String, byte[]> unserializeMap(byte[] data) throws IOException {
    return unserializeMap(new ByteArrayInputStream(data));
  }

  public static Map<String, byte[]> unserializeMap(InputStream data) throws IOException {
    Map<String, byte[]> result = new HashMap<>();
    try (DataInputStream dis = new DataInputStream(data)) {
      while (dis.available() > 0) {
        String key = new String(readBytes(dis), StandardCharsets.UTF_8);
        byte[] value = readBytes(dis);
        result.put(key, value);
      }
    }
    return result;
  }

  private static byte[] readBytes(DataInputStream dis) throws IOException {
    int length = dis.readInt();
    validateLength(length);
    byte[] bytes = new byte[length];
    dis.readFully(bytes);
    return bytes;
  }

  private static void writeLengthAndBytes(DataOutputStream dos, byte[] bytes) throws IOException {
    dos.writeInt(bytes.length);
    dos.write(bytes);
  }

  private static void validateLength(int length) throws IOException {
    if (length < 0) {
      throw new IOException("Invalid length while unserializing: " + length);
    }
  }
}
