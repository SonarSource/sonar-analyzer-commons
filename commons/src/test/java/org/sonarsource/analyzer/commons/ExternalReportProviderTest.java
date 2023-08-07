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

import java.io.File;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.testfixtures.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalReportProviderTest {

  @Rule
  public final LogTester logTester = new LogTester().setLevel(Level.TRACE);
  private final String EXTERNAL_REPORTS_PROPERTY = "sonar.foo.mylinter.reportPaths";

  @Test
  public void test_return_empty_when_no_value() throws Exception {
    SensorContextTester context = SensorContextTester.create(new File("."));

    List<File> reportFiles = ExternalReportProvider.getReportFiles(context, EXTERNAL_REPORTS_PROPERTY);

    assertThat(reportFiles).isEmpty();
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void test_resolve_abs_and_relative() throws Exception {
    SensorContextTester context = SensorContextTester.create(new File("src/test/resources"));
    context.settings().setProperty(EXTERNAL_REPORTS_PROPERTY, "foo.out, " + new File("src/test/resources/bar.out").getAbsolutePath());
    List<File> reportFiles = ExternalReportProvider.getReportFiles(context, EXTERNAL_REPORTS_PROPERTY);

    assertThat(reportFiles).hasSize(2);
    assertThat(reportFiles.get(0).getAbsolutePath()).isEqualTo(new File("src/test/resources/foo.out").getAbsolutePath());
    assertThat(reportFiles.get(1).getAbsolutePath()).isEqualTo(new File("src/test/resources/bar.out").getAbsolutePath());

    assertThat(logTester.logs()).isEmpty();
  }

}
