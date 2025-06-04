package org.sonarsource.analyzer.commons.telemetry;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.api.utils.Version;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;


class TelemetryHandlerTest {

  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.TRACE);

  public static final Version LATEST_API_VERSION = Version.create(12, 0);
  public static final SonarRuntime SONARLINT_RUNTIME = SonarRuntimeImpl.forSonarLint(LATEST_API_VERSION);
  public static final SonarRuntime SONARQUBE_RUNTIME = SonarRuntimeImpl.forSonarQube(LATEST_API_VERSION, SonarQubeSide.SERVER, SonarEdition.ENTERPRISE);
  public static final SonarRuntime SONARCLOUD_RUNTIME = SonarRuntimeImpl.forSonarQube(LATEST_API_VERSION, SonarQubeSide.SERVER, SonarEdition.SONARCLOUD);
  public static final SonarRuntime SONARQUBE_RUNTIME_WITHOUT_TELEMETRY_SUPPORT = SonarRuntimeImpl.forSonarQube(Version.create(10, 8), SonarQubeSide.SERVER,
    SonarEdition.ENTERPRISE);


  @Test
  void testReportMetricsWithNull() {
    var ctx = spy(SensorContextTester.create(Path.of(".")).setRuntime(SONARLINT_RUNTIME));
    TelemetryHandler telemetryHandler = new TelemetryHandler(ctx,"test.");
    telemetryHandler.addMetric(null);

    telemetryHandler.report();

    assertThat(logTester.logs(Level.WARN)).contains("Skipping telemetry metric due to null key or value: null");

  }

}
