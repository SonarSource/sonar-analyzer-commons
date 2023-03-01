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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

@EnableRuleMigrationSupport
class SonarSourceLoggerBridgeTest {

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @Test
  void log() {
    Logger.overrideFactory(Logger.DEFAULT_FACTORY, Logger.DEFAULT_SONAR_API_LOGGER);
    Logger logger = Logger.get(SonarSourceLoggerBridgeTest.class);
    assertThat(logger).isInstanceOf(SonarSourceLoggerBridge.class);

    assertThat(logTester.logs()).isEmpty();

    logTester.setLevel(LoggerLevel.INFO);
    logger.debug(() -> "A debug message");
    assertThat(logTester.logs()).isEmpty();

    logTester.setLevel(LoggerLevel.DEBUG);
    logger.debug(() -> "A debug message");
    logger.info(() -> "An info message");
    logger.warning(() -> "A warning message");
    logger.error(() -> "An error message");
    assertThat(logTester.logs(LoggerLevel.TRACE)).isEmpty();
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsExactly("A debug message");
    assertThat(logTester.logs(LoggerLevel.INFO)).containsExactly("An info message");
    assertThat(logTester.logs(LoggerLevel.WARN)).containsExactly("A warning message");
    assertThat(logTester.logs(LoggerLevel.ERROR)).containsExactly("An error message");
  }

}
