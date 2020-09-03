/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ProgressReportTest {

  @Rule
  public LogTester logTester = new LogTester();

  @Test(timeout = 5000)
  public void testSonarLogger() throws Exception {
    ProgressReport report = new ProgressReport(ProgressReportTest.class.getName(), 100);

    report.start(Arrays.asList("foo.java", "foo.java"));
    report.stop();

    assertThat(logTester.logs(LoggerLevel.INFO)).isNotEmpty();
  }

  @Test(timeout = 5000)
  public void test() throws Exception {
    Logger logger = mock(Logger.class);

    ProgressReport report = new ProgressReport(ProgressReportTest.class.getName(), 100, logger, "analyzed");

    report.start(Arrays.asList("foo.java", "foo.java"));

    // Wait for start message
    waitForMessage(logger);

    // Wait for at least one progress message
    waitForMessage(logger);

    report.stop();

    // Waits for the thread to die
    // Note: We cannot simply wait for a message here, because it could either be a progress or a stop one
    report.join();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(logger, atLeast(3)).info(captor.capture());

    List<String> messages = captor.getAllValues();
    assertThat(messages.size()).isGreaterThanOrEqualTo(3);
    assertThat(messages.get(0)).isEqualTo("2 source files to be analyzed");
    for (int i = 1; i < messages.size() - 1; i++) {
      assertThat(messages.get(i)).isEqualTo("0/2 files analyzed, current file: foo.java");
    }
    assertThat(messages.get(messages.size() - 1)).isEqualTo("2/2" + " source files have been analyzed");
  }

  @Test(timeout = 5000)
  public void testCancel() throws InterruptedException {
    Logger logger = mock(Logger.class);

    ProgressReport report = new ProgressReport(ProgressReport.class.getName(), 100, logger, "analyzed");
    report.start(Arrays.asList("foo.java", "foo.java"));

    // Wait for start message
    waitForMessage(logger);

    report.cancel();
    report.join();
  }

  private static void waitForMessage(Logger logger) throws InterruptedException {
    synchronized (logger) {
      logger.wait();
    }
  }

}

