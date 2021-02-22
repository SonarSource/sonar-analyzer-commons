package org.sonar.reports.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnitTestClassReportTest {
  @Test
  fun shouldIncrementCounters() {
    val report = UnitTestClassReport()
    report.add(UnitTestResult())
    report.add(UnitTestResult(status = UnitTestResult.STATUS_ERROR, durationMilliseconds = 500L))
    report.add(UnitTestResult(status = UnitTestResult.STATUS_FAILURE, durationMilliseconds = 500L))
    report.add(UnitTestResult(status = UnitTestResult.STATUS_OK, durationMilliseconds = 200L))

    //Some negative duration can occur due to bug in surefire.
    report.add(UnitTestResult(status = UnitTestResult.STATUS_OK, durationMilliseconds = -200L))
    report.add(UnitTestResult(status = UnitTestResult.STATUS_SKIPPED))

    assertThat(report.results().size).isEqualTo(6)
    assertThat(report.skipped).isEqualTo(1)
    assertThat(report.tests).isEqualTo(6)
    assertThat(report.durationMilliseconds).isEqualTo(500L + 200L + 500L)
    assertThat(report.errors).isEqualTo(1)
    assertThat(report.failures).isEqualTo(1)
    assertThat(report.negativeTimeTestNumber).isEqualTo(1L)
  }

  @Test
  fun shouldHaveEmptyReport() {
    val report = UnitTestClassReport()
    assertThat(report.results().size).isZero
    assertThat(report.skipped).isZero
    assertThat(report.tests).isZero
    assertThat(report.durationMilliseconds).isZero
    assertThat(report.errors).isZero
    assertThat(report.failures).isZero
  }
}
