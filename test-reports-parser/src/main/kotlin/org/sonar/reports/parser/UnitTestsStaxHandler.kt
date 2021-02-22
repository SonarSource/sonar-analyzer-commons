package org.sonar.reports.parser

import org.codehaus.staxmate.`in`.ElementFilter
import org.codehaus.staxmate.`in`.SMEvent
import org.codehaus.staxmate.`in`.SMHierarchicCursor
import org.codehaus.staxmate.`in`.SMInputCursor
import org.sonar.api.utils.ParsingUtils
import org.sonar.reports.data.UnitTestClassReport
import org.sonar.reports.data.UnitTestIndex
import org.sonar.reports.data.UnitTestResult
import java.text.ParseException
import java.util.Locale
import javax.xml.stream.XMLStreamException

class UnitTestsStaxHandler(private val index: UnitTestIndex) {

  @Throws(XMLStreamException::class)
  fun stream(rootCursor: SMHierarchicCursor) {
    val testSuite: SMInputCursor = rootCursor.constructDescendantCursor(ElementFilter("testsuite"))
    var testSuiteEvent: SMEvent? = testSuite.next
    while (testSuiteEvent != null) {
      if (testSuiteEvent.compareTo(SMEvent.START_ELEMENT) == 0) {
        val testSuiteClassName: String = testSuite.getAttrValue("name")
        parseTestCase(testSuiteClassName, testSuite.childCursor(ElementFilter("testcase")))
      }
      testSuiteEvent = testSuite.next
    }
  }

  @Throws(XMLStreamException::class)
  private fun parseTestCase(testSuiteClassName: String, testCase: SMInputCursor) {
    var event: SMEvent? = testCase.next
    while (event != null) {
      if (event.compareTo(SMEvent.START_ELEMENT) == 0) {
        val testClassName = getClassname(testCase, testSuiteClassName)
        val classReport = index.index(testClassName)
        parseTestCase(testCase, testSuiteClassName, classReport)
      }
      event = testCase.next
    }
  }

  @Throws(XMLStreamException::class)
  private fun getClassname(testCaseCursor: SMInputCursor, defaultClassname: String): String {
    var testClassName: String? = testCaseCursor.getAttrValue("classname")
    if (!testClassName.isNullOrBlank() && testClassName.endsWith(")")) {
      val openParenthesisIndex = testClassName.indexOf('(')
      if (openParenthesisIndex > 0) {
        testClassName = testClassName.substring(0, openParenthesisIndex)
      }
    }
    return if (testClassName.isNullOrBlank()) defaultClassname else testClassName
  }

  @Throws(XMLStreamException::class)
  private fun parseTestCase(testCaseCursor: SMInputCursor, testSuiteClassName: String, report: UnitTestClassReport) {
    report.add(parseTestResult(testCaseCursor, testSuiteClassName))
  }

  @Throws(XMLStreamException::class)
  private fun setStackAndMessage(result: UnitTestResult, stackAndMessageCursor: SMInputCursor) {
    result.message = stackAndMessageCursor.getAttrValue("message")
    val stack: String = stackAndMessageCursor.collectDescendantText()
    result.stackTrace = stack
  }

  @Throws(XMLStreamException::class)
  private fun parseTestResult(testCaseCursor: SMInputCursor, testSuiteClassName: String): UnitTestResult {
    val detail = UnitTestResult()
    val name = getTestCaseName(testCaseCursor)
    detail.name = name
    detail.testSuiteClassName = testSuiteClassName
    var status = UnitTestResult.STATUS_OK
    val time: String = testCaseCursor.getAttrValue("time") ?: ""
    var duration: Long? = null
    val childNode: SMInputCursor = testCaseCursor.descendantElementCursor()
    if (childNode.next != null) {
      when (childNode.localName) {
          "failure" -> {
              status = UnitTestResult.STATUS_FAILURE
              setStackAndMessage(detail, childNode)
          }
          "skipped" -> {
              status = UnitTestResult.STATUS_SKIPPED
              // bug with surefire reporting wrong time for skipped tests
              duration = 0L
          }
          "error" -> {
              status = UnitTestResult.STATUS_ERROR
              setStackAndMessage(detail, childNode)
          }
      }
    }
    while (childNode.next != null) {
      // make sure we loop till the end of the elements cursor
    }
    detail.durationMilliseconds = duration ?: getTimeAttributeInMS(time)
    detail.status = status
    return detail
  }

  @Throws(XMLStreamException::class)
  private fun getTimeAttributeInMS(value: String): Long {
    // hardcoded to Locale.ENGLISH see http://jira.codehaus.org/browse/SONAR-602
    return try {
      val time: Double = ParsingUtils.parseNumber(value, Locale.ENGLISH)
      if (!time.isNaN())
        ParsingUtils.scaleValue(time * 1000, 3).toLong()
      else 0L
    } catch (e: ParseException) {
      throw XMLStreamException(e)
    }
  }

  @Throws(XMLStreamException::class)
  private fun getTestCaseName(testCaseCursor: SMInputCursor): String {
    val classname: String = testCaseCursor.getAttrValue("classname") ?: ""
    val name: String = testCaseCursor.getAttrValue("name") ?: ""
    return if (classname.contains("$"))
      "${classname.substringAfter("$")}/$name"
    else name
  }
}
