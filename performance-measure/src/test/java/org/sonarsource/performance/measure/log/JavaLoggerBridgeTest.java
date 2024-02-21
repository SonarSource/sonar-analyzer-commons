/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaLoggerBridgeTest {

  @Test
  void log() {
    java.util.logging.Logger javaLogger = java.util.logging.Logger.getLogger(JavaLoggerBridgeTest.class.getName());
    TestHandler handler = new TestHandler();
    javaLogger.addHandler(handler);

    Logger.overrideFactory(Logger.DEFAULT_FACTORY, "org.sonar.api.UnknownClassName");
    Logger logger = Logger.get(JavaLoggerBridgeTest.class);
    assertThat(logger).isInstanceOf(JavaLoggerBridge.class);
    assertThat(handler.logs).isEmpty();

    javaLogger.setLevel(Level.INFO);
    logger.debug(() -> "A debug message");
    assertThat(handler.logs).isEmpty();

    javaLogger.setLevel(Level.FINER);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(handler.logs).hasToString("" +
      "[FINE] A debug message\n" +
      "[INFO] An info message\n" +
      "[WARNING] A warning message\n" +
      "[SEVERE] An error message\n");
  }

  static class TestHandler extends Handler {

    StringBuilder logs = new StringBuilder();

    @Override
    public void publish(LogRecord record) {
      logs.append("[").append(record.getLevel().getName()).append("] ").append(record.getMessage()).append("\n");
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

  }
}
