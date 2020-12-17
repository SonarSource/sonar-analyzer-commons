package org.sonar.reports.data

import java.util.*

data class UnitTestResult(
    var name: String = UUID.randomUUID().toString(),
    var testSuiteClassName: String? = null,
    var status: String? = null,
    var stackTrace: String? = null,
    var message: String? = null,
    var durationMilliseconds: Long = 0L) {

    fun isErrorOrFailure(): Boolean = STATUS_ERROR == status || STATUS_FAILURE == status

    fun isError(): Boolean = STATUS_ERROR == status

    companion object {
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        const val STATUS_FAILURE = "failure"
        const val STATUS_SKIPPED = "skipped"
    }
}