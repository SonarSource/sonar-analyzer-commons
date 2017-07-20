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

import java.util.Map;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.JSObject;

/**
 * Not designed for multi-threads
 */
class JsonParser {

  private JSObject nashornParser;

  JsonParser() {
    try {
      nashornParser = (JSObject) new ScriptEngineManager().getEngineByName("nashorn").eval("JSON.parse");
    } catch (ScriptException e) {
      throw new IllegalStateException("Can not get 'JSON.parse' from 'nashorn' script engine.", e);
    }
  }

  Map<String, Object> parse(String data) {
    return (Map<String, Object>) nashornParser.call(null, data);
  }

}
