/*
 * SonarSource Performance Measure Library
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
package org.sonarsource.performance.measure.log;

import java.util.function.Supplier;

public class StringLogger extends Logger {

  public enum Level {
    DEBUG, INFO, WARNING, ERROR
  }

  private static final StringLogger INSTANCE = new StringLogger();

  private static final ThreadLocal<Level> LEVELS = ThreadLocal.withInitial(() -> Level.INFO);
  private static final ThreadLocal<StringBuilder> BUFFERS = ThreadLocal.withInitial(StringBuilder::new);

  private StringLogger() {
  }

  public static StringLogger initialize(Level level) {
    Logger.overrideFactory(cls -> INSTANCE, Logger.DEFAULT_SONAR_API_LOGGER);
    INSTANCE.clear();
    INSTANCE.setLevel(level);
    return INSTANCE;
  }

  public void setLevel(Level level) {
    LEVELS.set(level);
  }

  public void clear() {
    BUFFERS.get().setLength(0);
  }

  public String logs() {
    return BUFFERS.get().toString();
  }

  @Override
  public void debug(Supplier<String> message) {
    log(Level.DEBUG, message);
  }

  @Override
  public void info(Supplier<String> message) {
    log(Level.INFO, message);
  }

  @Override
  public void warning(Supplier<String> message) {
    log(Level.WARNING, message);
  }

  @Override
  public void error(Supplier<String> message) {
    log(Level.ERROR, message);
  }

  private void log(Level level, Supplier<String> message) {
    if (level.ordinal() >= LEVELS.get().ordinal()) {
      BUFFERS.get().append("[").append(level.name()).append("] ").append(message.get()).append("\n");
    }
  }

}
