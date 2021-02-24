package org.sonar.reports

import org.junit.jupiter.api.extension.RegisterExtension
import org.sonar.api.utils.log.LogTesterJUnit5
import org.sonar.api.utils.log.LoggerLevel
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.fs.internal.TestInputFileBuilder
import org.sonar.api.batch.sensor.SensorContext
import org.sonar.api.batch.sensor.internal.SensorContextTester
import org.sonar.api.measures.CoreMetrics
import java.io.File
import java.net.URISyntaxException
import java.util.stream.Collectors
import java.util.stream.Stream

class DefaultTestParser : TestsResultParser {
  override fun findResourceByClassName(className: String): InputFile? =
    TestInputFileBuilder("", className).build()
}

class TestsResultParserTest {

  companion object {
    private const val FOO_CLASS = ":java.Foo"

        @JvmField
        @RegisterExtension
        val logTester: LogTesterJUnit5 = LogTesterJUnit5()
  }

  private var parser: TestsResultParser = DefaultTestParser()

  @Test
  @Throws(Exception::class)
  fun should_store_zero_tests_when_directory_is_null_or_non_existing_or_a_file() {
    var context: SensorContext = mock(SensorContext::class.java)
    parser.collect(context, getDirs("nonExistingReportsDirectory"), false)

    verify(context, never()).newMeasure<Int>()
    verify(context, never()).newMeasure<Long>()

    context = mock(SensorContext::class.java)
    parser.collect(context, getDirs("file.txt"), true)
    verify(context, never()).newMeasure<Int>()
    verify(context, never()).newMeasure<Long>()
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldAggregateReports() {
    val context = mockContext()
    parser.collect(context, getDirs("multipleReports"), true)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.MetricsCollectorRegistryTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.CloverCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.CheckstyleCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.SonarMojoTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.JDependsCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.JavaNCSSCollectorTest")).hasSize(5)
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldAggregateReportsFromMultipleDirectories() {
    val context = mockContext()
    parser.collect(context, getDirs("multipleDirectories/dir1", "multipleDirectories/dir2"), true)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.MetricsCollectorRegistryTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.CloverCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.CheckstyleCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.SonarMojoTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.JDependsCollectorTest")).hasSize(5)
    Assertions.assertThat(context.measures(":ch.hortis.sonar.mvn.mc.JavaNCSSCollectorTest")).hasSize(5)
  }

  // SONAR-2841: if there's only a test suite report, then it should be read.
  @Test
  @Throws(URISyntaxException::class)
  fun shouldUseTestSuiteReportIfAlone() {
    val context = mockContext()
    parser.collect(context, getDirs("onlyTestSuiteReport"), true)
    Assertions.assertThat(context.measures(":org.sonar.SecondTest")).hasSize(5)
    Assertions.assertThat(context.measures(":org.sonar.JavaNCSSCollectorTest")).hasSize(5)
  }

  /**
   * See http://jira.codehaus.org/browse/SONAR-2371
   */
  @Test
  @Throws(URISyntaxException::class)
  fun shouldInsertZeroWhenNoReports() {
    val context: SensorContext = mock(SensorContext::class.java)
    parser.collect(context, getDirs("noReports"), true)
    verify(context, never()).newMeasure<Int>()
    verify(context, never()).newMeasure<Long>()
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldNotInsertZeroOnFiles() {
    val context: SensorContext = mock(SensorContext::class.java)
    parser.collect(context, getDirs("noTests"), true)
    verify(context, never()).newMeasure<Int>()
    verify(context, never()).newMeasure<Long>()
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldMergeInnerClasses() {
    val context = mockContext()
    parser.collect(context, getDirs("innerClasses"), true)
    Assertions.assertThat(
      context.measure(
        ":org.apache.commons.collections.bidimap.AbstractTestBidiMap",
        CoreMetrics.TESTS
      ).value()
    ).isEqualTo(7)
    Assertions.assertThat(
      context.measure(
        ":org.apache.commons.collections.bidimap.AbstractTestBidiMap",
        CoreMetrics.TEST_ERRORS
      ).value()
    ).isEqualTo(1)
    Assertions.assertThat(context.measures(":org.apache.commons.collections.bidimap.AbstractTestBidiMap\$TestBidiMapEntrySet"))
      .isEmpty()
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldMergeNestedInnerClasses() {
    val context = mockContext()
    parser.collect(context, getDirs("nestedInnerClasses"), true)
    Assertions.assertThat(context.measure(":org.sonar.plugins.surefire.NestedInnerTest", CoreMetrics.TESTS).value())
      .isEqualTo(3)
  }

  @Test
  @Throws(URISyntaxException::class)
  fun shouldMergeInnerClassReportInExtraFile() {
    val context = mockContext()
    parser.collect(context, getDirs("innerClassExtraFile"), true)
    Assertions.assertThat(context.measure(":com.example.project.CalculatorTests", CoreMetrics.TESTS).value())
      .isEqualTo(6)
  }

  @Test
  @Throws(URISyntaxException::class)
  fun should_not_count_negative_tests() {
    val context = mockContext()
    parser.collect(context, getDirs("negativeTestTime"), true)
    //Test times : -1.120, 0.644, 0.015 -> computed time : 0.659, ignore negative time.
    Assertions.assertThat(context.measure(FOO_CLASS, CoreMetrics.SKIPPED_TESTS).value()).isZero
    Assertions.assertThat(context.measure(FOO_CLASS, CoreMetrics.TESTS).value()).isEqualTo(6)
    Assertions.assertThat(context.measure(FOO_CLASS, CoreMetrics.TEST_ERRORS).value()).isZero
    Assertions.assertThat(context.measure(FOO_CLASS, CoreMetrics.TEST_FAILURES).value()).isZero
    Assertions.assertThat(context.measure(FOO_CLASS, CoreMetrics.TEST_EXECUTION_TIME).value()).isEqualTo(659)
  }

  @Test
  @Throws(URISyntaxException::class)
  fun should_handle_parameterized_tests() {
    val context = mockContext()

    class ParametrizedTestParser : TestsResultParser {
      override fun findResourceByClassName(className: String): InputFile? {
        if (className == "org.foo.Junit4ParameterizedTest" || className.startsWith("org.foo.Junit5_0ParameterizedTest")
          || className.startsWith("org.foo.Junit5_1ParameterizedTest")
        ) {
          return TestInputFileBuilder("", className).build()
        }
        return null
      }
    }

    parser = ParametrizedTestParser()

    parser.collect(context, getDirs("junitParameterizedTests"), true)

    // class names are wrong in JUnit 4.X parameterized tests, with class name being the name of the test
    val measure = context.measure(":org.foo.Junit4ParameterizedTest", CoreMetrics.TESTS)
    Assertions.assertThat(measure.value())
      .isEqualTo(7)
    Assertions.assertThat(
      context.measure(":org.foo.Junit4ParameterizedTest", CoreMetrics.TEST_EXECUTION_TIME).value()
    ).isEqualTo(1)

    // class names and test names are wrong in JUnit 5.0, resulting in repeated/parameterized tests sharing the same name,
    // with class name being the name of the test (cf. https://github.com/junit-team/junit5/issues/1182)
    Assertions.assertThat(context.measure(":org.foo.Junit5_0ParameterizedTest", CoreMetrics.TESTS).value())
      .isEqualTo(13)
    Assertions.assertThat(
      context.measure(":org.foo.Junit5_0ParameterizedTest", CoreMetrics.TEST_EXECUTION_TIME).value()
    ).isEqualTo(48)

    // test file with expected fix from JUnit 5.1 (TODO: to be confirmed once 5.1 released)
    Assertions.assertThat(context.measure(":org.foo.Junit5_1ParameterizedTest", CoreMetrics.TESTS).value())
      .isEqualTo(13)
    Assertions.assertThat(
      context.measure(":org.foo.Junit5_1ParameterizedTest", CoreMetrics.TEST_EXECUTION_TIME).value()
    ).isEqualTo(48)
  }

    @Test
    @Throws(Exception::class)
    fun should_log_missing_resource_with_debug_level() {

        class NullUnitTestParser : TestsResultParser {
            override fun findResourceByClassName(className: String): InputFile?  = null
        }

        parser = NullUnitTestParser()
        val context = mockContext()
        parser.collect(context, getDirs("resourceNotFound"), true)
        Assertions.assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty()
        Assertions.assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Resource not found: org.sonar.Foo")
    }

  @Throws(URISyntaxException::class)
  private fun getDirs(vararg directoryNames: String): List<File> {
    return Stream.of(*directoryNames)
      .map { directoryName: String ->
        File(
          "src/test/resources/org/sonar/reports/UnitTestsLanguageParserTest/$directoryName"
        )
      }
      .collect(Collectors.toList())
  }

  private fun mockContext(): SensorContextTester {
    return SensorContextTester.create(File(""))
  }
}
