/*
 * SonarSource Analyzers Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Not designed for multi-threads
 */
class JsonParser {

  private static final ContainerFactory ORDERED_CONTAINERS = new ContainerFactory() {
    @Override
    public Map<String, Object> createObjectContainer() {
      return new LinkedHashMap<>();
    }

    @Override
    public List<Object> creatArrayContainer() {
      return new ArrayList<>();
    }
  };

  private final JSONParser parser = new JSONParser();

  Map<String, Object> parse(String data) {
    try {
      return (Map<String, Object>) parser.parse(data);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Could not parse JSON", e);
    }
  }

  /**
   * Like {@link #parse(String)}, but the objects keep the order of their keys.
   */
  Map<String, Object> parseOrdered(String data) {
    try {
      return (Map<String, Object>) parser.parse(data, ORDERED_CONTAINERS);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Could not parse JSON", e);
    }
  }

  List<Map<String, Object>> parseArray(Reader reader) throws IOException {
    try {
      return (List<Map<String, Object>>) parser.parse(reader);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Could not parse JSON", e);
    }
  }

}
