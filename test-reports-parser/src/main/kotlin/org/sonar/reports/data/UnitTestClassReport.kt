package org.sonar.reports.data

class UnitTestClassReport {
    var errors = 0
        private set
    var failures = 0
        private set
    var skipped = 0
        private set
    var tests = 0
        private set
    var durationMilliseconds = 0L
        private set
    var negativeTimeTestNumber = 0L
        private set
    val results: MutableList<UnitTestResult> = mutableListOf()

    fun add(other: UnitTestClassReport): UnitTestClassReport {
        for (otherResult in other.results) {
            add(otherResult)
        }
        return this
    }

    fun add(result: UnitTestResult): UnitTestClassReport {
        val hasName = results.stream().map<Any>(UnitTestResult::name).anyMatch(result.name::equals)
        if (hasName && "$" in result.name) {
            return this
        }
        results.add(result)
        when(result.status) {
            UnitTestResult.STATUS_SKIPPED -> skipped++
            UnitTestResult.STATUS_FAILURE -> failures++
            UnitTestResult.STATUS_ERROR -> errors++
        }
        tests++
        if (result.durationMilliseconds < 0) {
            negativeTimeTestNumber++
        } else {
            durationMilliseconds += result.durationMilliseconds
        }
        return this
    }

}