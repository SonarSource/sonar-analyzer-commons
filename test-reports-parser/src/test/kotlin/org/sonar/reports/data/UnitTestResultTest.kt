package org.sonar.reports.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnitTestResultTest {

  @Test
  fun shouldBeError() {
    val result = UnitTestResult(status = UnitTestResult.STATUS_ERROR)
    assertThat(result.status).isEqualTo(UnitTestResult.STATUS_ERROR)
    assertThat(result.isError()).isTrue
    assertThat(result.isErrorOrFailure()).isTrue
  }

  @Test
  fun shouldBeFailure() {
    val result = UnitTestResult(status = UnitTestResult.STATUS_FAILURE)
    assertThat(result.status).isEqualTo(UnitTestResult.STATUS_FAILURE)
    assertThat(result.isError()).isFalse
    assertThat(result.isErrorOrFailure()).isTrue
  }

  @Test
  fun shouldBeSuccess() {
    val result = UnitTestResult(status = UnitTestResult.STATUS_OK)
    assertThat(result.status).isEqualTo(UnitTestResult.STATUS_OK)
    assertThat(result.isError()).isFalse
    assertThat(result.isErrorOrFailure()).isFalse
  }
}
