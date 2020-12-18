package org.sonar.reports.parser

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonar.reports.data.UnitTestIndex
import org.sonar.reports.data.UnitTestResult
import java.io.File
import java.net.URISyntaxException
import javax.xml.stream.XMLStreamException

class UnitTestsStaxHandlerTest {
    private lateinit var index: UnitTestIndex

    @BeforeEach
    fun setUp() {
        index = UnitTestIndex()
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldLoadInnerClasses() {
        parse("innerClasses.xml")
        val publicClass = index["org.apache.commons.collections.bidimap.AbstractTestBidiMap"]!!
        Assertions.assertThat(publicClass.tests).isEqualTo(2)
        val innerClass1 = index["org.apache.commons.collections.bidimap.AbstractTestBidiMap\$TestBidiMapEntrySet"]!!
        Assertions.assertThat(innerClass1.tests).isEqualTo(2)
        val innerClass2 = index["org.apache.commons.collections.bidimap.AbstractTestBidiMap\$TestInverseBidiMap"]!!
        Assertions.assertThat(innerClass2.tests).isEqualTo(3)
        Assertions.assertThat(innerClass2.durationMilliseconds).isEqualTo(30 + 1L)
        Assertions.assertThat(innerClass2.errors).isEqualTo(1)
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldHaveSkippedTests() {
        parse("skippedTests.xml")
        val report = index["org.sonar.Foo"]!!
        Assertions.assertThat(report.tests).isEqualTo(3)
        Assertions.assertThat(report.skipped).isEqualTo(1)
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldHaveZeroTests() {
        parse("zeroTests.xml")
        Assertions.assertThat(index.size()).isZero
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldHaveTestOnRootPackage() {
        parse("rootPackage.xml")
        Assertions.assertThat(index.size()).isEqualTo(1)
        val report = index["NoPackagesTest"]!!
        Assertions.assertThat(report.tests).isEqualTo(2)
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldHaveErrorsAndFailures() {
        parse("errorsAndFailures.xml")
        val report = index["org.sonar.Foo"]!!
        Assertions.assertThat(report.errors).isEqualTo(1)
        Assertions.assertThat(report.failures).isEqualTo(1)
        Assertions.assertThat(report.results.size).isEqualTo(2)

        // failure
        val failure = report.results[0]
        Assertions.assertThat(failure.durationMilliseconds).isEqualTo(5L)
        Assertions.assertThat(failure.status).isEqualTo(UnitTestResult.STATUS_FAILURE)
        Assertions.assertThat(failure.name).isEqualTo("testOne")
        Assertions.assertThat(failure.message).startsWith("expected")

        // error
        val error = report.results[1]
        Assertions.assertThat(error.durationMilliseconds).isZero
        Assertions.assertThat(error.status).isEqualTo(UnitTestResult.STATUS_ERROR)
        Assertions.assertThat(error.name).isEqualTo("testTwo")
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldSupportMultipleSuitesInSameReport() {
        parse("multipleSuites.xml")
        Assertions.assertThat(index["org.sonar.JavaNCSSCollectorTest"]?.tests).isEqualTo(11)
        Assertions.assertThat(index["org.sonar.SecondTest"]?.tests).isEqualTo(4)
    }

    @Test
    @Throws(XMLStreamException::class)
    fun shouldSupportSkippedTestWithoutTimeAttribute() {
        parse("skippedWithoutTimeAttribute.xml")
        val publicClass = index["TSuite.A"]!!
        Assertions.assertThat(publicClass.skipped).isEqualTo(2)
        Assertions.assertThat(publicClass.tests).isEqualTo(4)
    }

    @Test
    @Throws(XMLStreamException::class)
    fun output_of_junit_5_2_test_without_display_name() {
        parse("TEST-#29.xml")
        Assertions.assertThat(index[")"]?.tests).isEqualTo(1)
    }

    @Throws(XMLStreamException::class)
    private fun parse(path: String) {
        val parser = UnitTestsStaxParser(index)
        val xmlFile = try {
            File(javaClass.getResource(javaClass.simpleName + "/" + path).toURI())
        } catch (e: URISyntaxException) {
            throw IllegalStateException(e)
        }
        parser.parse(xmlFile)
    }
}