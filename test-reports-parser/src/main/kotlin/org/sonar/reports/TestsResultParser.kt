package org.sonar.reports

import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.sensor.SensorContext
import org.sonar.api.measures.CoreMetrics
import org.sonar.api.measures.Metric
import org.sonar.api.utils.log.Loggers
import org.sonar.reports.data.UnitTestClassReport
import org.sonar.reports.data.UnitTestIndex
import org.sonar.reports.parser.UnitTestsStaxParser
import java.io.File
import java.io.Serializable
import java.util.*
import javax.xml.stream.XMLStreamException
import kotlin.collections.HashMap

interface TestsResultParser {

    fun collect(context: SensorContext, reportsDirs: List<File>, reportDirSetByUser: Boolean) {
        val xmlFiles = getReports(reportsDirs, reportDirSetByUser)
        if (xmlFiles.isNotEmpty()) {
            parseFiles(context, xmlFiles)
        }
    }

    private fun getReports(dirs: List<File>, reportDirSetByUser: Boolean): List<File> {
        return dirs.asSequence()
            .map { dir: File -> getReports(dir, reportDirSetByUser) }
            .flatMap { array: Array<File> -> array.asSequence() }
            .toList()
    }

    private fun getReports(dir: File, reportDirSetByUser: Boolean): Array<File> {
        if (!dir.isDirectory) {
            if (reportDirSetByUser) {
                LOGGER.error("Reports path not found or is not a directory: " + dir.absolutePath)
            }
            return arrayOf()
        }
        var unitTestResultFiles = findXMLFilesStartingWith(dir, "TEST-")
        if (unitTestResultFiles.isEmpty()) {
            // maybe there's only a test suite result file
            unitTestResultFiles = findXMLFilesStartingWith(dir, "TESTS-")
        }
        if (unitTestResultFiles.isEmpty()) {
            LOGGER.warn("Reports path contains no files matching TEST-.*.xml : " + dir.absolutePath)
        }
        return unitTestResultFiles
    }

    private fun findXMLFilesStartingWith(dir: File, fileNameStart: String): Array<File> {
        return dir.listFiles { _: File?, name: String -> name.startsWith(fileNameStart) && name.endsWith(".xml") }!!
    }

    private fun parseFiles(context: SensorContext, reports: List<File>) {
        val index = UnitTestIndex()
        parseFiles(reports, index)
        sanitize(index)
        save(index, context)
    }

    private fun parseFiles(reports: List<File>, index: UnitTestIndex) {
        val parser = UnitTestsStaxParser(index)
        for (report in reports) {
            try {
                parser.parse(report)
            } catch (e: XMLStreamException) {
                throw RuntimeException("Fail to parse the Surefire report: $report", e)
            }
        }
    }

    private fun sanitize(index: UnitTestIndex) {
        for (classname in index.getClassnames()) {
            if (classname.contains("$")) {
                // Surefire reports classes whereas sonar supports files
                val parentClassName: String = classname.substringBefore("$")
                index.merge(classname, parentClassName)
            }
        }
    }

    private fun save(index: UnitTestIndex, context: SensorContext) {
        var negativeTimeTestNumber: Long = 0
        val indexByInputFile = mapToInputFile(index.indexByClassname)
        for ((key, report) in indexByInputFile) {
            if (report.tests > 0) {
                negativeTimeTestNumber += report.negativeTimeTestNumber
                save(report, key, context)
            }
        }
        if (negativeTimeTestNumber > 0) {
            LOGGER.warn(
                "There is {} test(s) reported with negative time by surefire, total duration may not be accurate.",
                negativeTimeTestNumber
            )
        }
    }

    private fun mapToInputFile(indexByClassname: Map<String, UnitTestClassReport>): Map<InputFile, UnitTestClassReport> {
        val result: MutableMap<InputFile, UnitTestClassReport> = HashMap()
        indexByClassname.forEach { (className: String, index: UnitTestClassReport) ->
            val resource = getUnitTestResource(className, index)
            if (resource != null) {
                val report = result.computeIfAbsent(
                    resource
                ) { UnitTestClassReport() }
                // in case of repeated/parameterized tests (JUnit 5.x) we may end up with tests having the same name
                index.results.forEach(report::add)
            } else {
                LOGGER.debug("Resource not found: {}", className)
            }
        }
        return result
    }

    private fun save(report: UnitTestClassReport, inputFile: InputFile, context: SensorContext) {
        val testsCount = report.tests - report.skipped
        saveMeasure(context, inputFile, CoreMetrics.SKIPPED_TESTS, report.skipped)
        saveMeasure(context, inputFile, CoreMetrics.TESTS, testsCount)
        saveMeasure(context, inputFile, CoreMetrics.TEST_ERRORS, report.errors)
        saveMeasure(context, inputFile, CoreMetrics.TEST_FAILURES, report.failures)
        saveMeasure(context, inputFile, CoreMetrics.TEST_EXECUTION_TIME, report.durationMilliseconds)
    }

    fun findResourceByClassName(className: String): InputFile?

    fun getUnitTestResource(
        className: String,
        unitTestClassReport: UnitTestClassReport
    ): InputFile? {
        return findResourceByClassName(className)
            ?: // fall back on testSuite class name (repeated and parameterized tests from JUnit 5.0 are using test name as classname)
            // Should be fixed with JUnit 5.1, see: https://github.com/junit-team/junit5/issues/1182
            unitTestClassReport.results.stream()
                .map { findResourceByClassName(it.testSuiteClassName!!) }
                .filter { obj: InputFile? -> Objects.nonNull(obj) }
                .findFirst()
                .orElse(null)
    }

    private fun <T : Serializable?> saveMeasure(
        context: SensorContext,
        inputFile: InputFile,
        metric: Metric<T>,
        value: T
    ): Unit = context.newMeasure<T>().forMetric(metric).on(inputFile).withValue(value).save()

    companion object {
        private val LOGGER = Loggers.get(TestsResultParser::class.java)
    }

}