package org.sonar.reports.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnitTestIndexTest {

  @Test
  fun shouldIndexNewClassname() {
    val index = UnitTestIndex()
    val report = index.index(PACKAGE)
    assertThat(report.tests).isZero
    assertThat(index.size()).isEqualTo(1)
    assertThat(report).isSameAs(index[PACKAGE])
  }

  @Test
  fun shouldNotReIndex() {
    val index = UnitTestIndex()
    val report1 = index.index(PACKAGE)
    val report2 = index.index(PACKAGE)
    assertThat(report1).isSameAs(report2)
    assertThat(report1.tests).isZero
    assertThat(index.size()).isEqualTo(1)
    assertThat(report1).isSameAs(index[PACKAGE])
  }

  @Test
  fun shouldRemoveClassname() {
    val index = UnitTestIndex()
    index.index(PACKAGE)
    index.remove(PACKAGE)
    assertThat(index.size()).isZero
    assertThat(index[PACKAGE]).isNull()
  }

  @Test
  fun shouldMergeClasses() {
    val index = UnitTestIndex()
    val innerClass = index.index("${PACKAGE}\$Bar")
    innerClass.add(UnitTestResult(status = UnitTestResult.STATUS_ERROR, durationMilliseconds = 500L))
    innerClass.add(UnitTestResult(status = UnitTestResult.STATUS_OK, durationMilliseconds = 200L))
    val publicClass = index.index(PACKAGE)
    publicClass.add(UnitTestResult(status = UnitTestResult.STATUS_ERROR, durationMilliseconds = 1000L))
    publicClass.add(UnitTestResult(status = UnitTestResult.STATUS_FAILURE, durationMilliseconds = 350L))
    index.merge("org.sonar.Foo\$Bar", PACKAGE)
    assertThat(index.size()).isEqualTo(1)
    val report = index[PACKAGE]!!
    assertThat(report.tests).isEqualTo(4)
    assertThat(report.failures).isEqualTo(1)
    assertThat(report.errors).isEqualTo(2)
    assertThat(report.skipped).isZero
    assertThat(report.results().size).isEqualTo(4)
    assertThat(report.durationMilliseconds).isEqualTo(500L + 200L + 1000L + 350L)
  }

  @Test
  fun shouldRenameClassWhenMergingToNewClass() {
    val index = UnitTestIndex()
    val innerClass = index.index("${PACKAGE}\$Bar")
    innerClass.add(UnitTestResult(status = UnitTestResult.STATUS_ERROR, durationMilliseconds = 500L))
    innerClass.add(UnitTestResult(status = UnitTestResult.STATUS_OK, durationMilliseconds = 200L))
    index.merge("${PACKAGE}\$Bar", PACKAGE)
    assertThat(index.size()).isEqualTo(1)
    val report = index[PACKAGE]!!
    assertThat(report.tests).isEqualTo(2)
    assertThat(report.failures).isZero
    assertThat(report.errors).isEqualTo(1)
    assertThat(report.skipped).isZero
    assertThat(report.results().size).isEqualTo(2)
    assertThat(report.durationMilliseconds).isEqualTo(500L + 200L)
  }

  @Test
  fun shouldNotFailWhenMergingUnknownClass() {
    val index = UnitTestIndex()
    index.merge("org.sonar.Foo\$Bar", PACKAGE)
    assertThat(index.size()).isZero
  }

  companion object {
    private const val PACKAGE = "org.sonar.Foo"
  }
}
