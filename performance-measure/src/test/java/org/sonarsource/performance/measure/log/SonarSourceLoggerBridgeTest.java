/*
 * SonarSource Performance Measure Library
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
package org.sonarsource.performance.measure.log;

import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;

import static org.assertj.core.api.Assertions.assertThat;

class SonarSourceLoggerBridgeTest {

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private Logger logger;

  @BeforeEach
  void beforeEach() {
    Logger.overrideFactory(Logger.DEFAULT_FACTORY, Logger.DEFAULT_SLF4J_LOGGER);
    logger = Logger.get(SonarSourceLoggerBridgeTest.class);
    assertThat(logger).isInstanceOf(SonarSourceLoggerBridge.class);
  }

  @Test
  void log_trace() {
    logTester.setLevel(Level.TRACE);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(Level.TRACE)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).containsExactly("A debug message");
    assertThat(logTester.logs(Level.INFO)).containsExactly("An info message");
    assertThat(logTester.logs(Level.WARN)).containsExactly("A warning message");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("An error message");
  }

  @Test
  void log_debug() {
    logTester.setLevel(Level.DEBUG);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(Level.TRACE)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).containsExactly("A debug message");
    assertThat(logTester.logs(Level.INFO)).containsExactly("An info message");
    assertThat(logTester.logs(Level.WARN)).containsExactly("A warning message");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("An error message");
  }

  @Test
  void log_info() {
    logTester.setLevel(Level.INFO);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(Level.TRACE)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).isEmpty();
    assertThat(logTester.logs(Level.INFO)).containsExactly("An info message");
    assertThat(logTester.logs(Level.WARN)).containsExactly("A warning message");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("An error message");
  }

  @Test
  void log_warning() {
    logTester.setLevel(Level.WARN);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(Level.TRACE)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).isEmpty();
    assertThat(logTester.logs(Level.INFO)).isEmpty();
    assertThat(logTester.logs(Level.WARN)).containsExactly("A warning message");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("An error message");
  }

  @Test
  void log_error() {
    logTester.setLevel(Level.ERROR);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(Level.TRACE)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).isEmpty();
    assertThat(logTester.logs(Level.INFO)).isEmpty();
    assertThat(logTester.logs(Level.WARN)).isEmpty();
    assertThat(logTester.logs(Level.ERROR)).containsExactly("An error message");
  }

}
