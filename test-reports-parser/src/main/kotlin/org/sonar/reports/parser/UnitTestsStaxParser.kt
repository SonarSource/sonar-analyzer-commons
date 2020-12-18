package org.sonar.reports.parser

import com.ctc.wstx.api.WstxInputProperties
import com.ctc.wstx.stax.WstxInputFactory
import org.codehaus.staxmate.SMInputFactory
import org.codehaus.staxmate.`in`.SMHierarchicCursor
import org.sonar.reports.data.UnitTestIndex
import org.sonarsource.analyzer.commons.xml.SafeStaxParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.xml.stream.XMLResolver
import javax.xml.stream.XMLStreamException

class UnitTestsStaxParser(index: UnitTestIndex) {
    private val inf: SMInputFactory
    private val streamHandler: UnitTestsStaxHandler = UnitTestsStaxHandler(index)

    init {
        val xmlInputFactory = SafeStaxParserFactory.createXMLInputFactory()
        if (xmlInputFactory is WstxInputFactory) {
            xmlInputFactory.configureForLowMemUsage()
            xmlInputFactory.config.undeclaredEntityResolver =
                XMLResolver { _: String?, _: String?, _: String?, namespace: String? -> namespace }
            xmlInputFactory.setProperty(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, Int.MAX_VALUE)
        }
        inf = SMInputFactory(xmlInputFactory)
    }

    @Throws(XMLStreamException::class)
    fun parse(xmlFile: File): Unit =
        try {
            FileInputStream(xmlFile).use { input -> parse(inf.rootElementCursor(input)) }
        } catch (e: IOException) {
            throw XMLStreamException(e)
        }

    @Throws(XMLStreamException::class)
    private fun parse(rootCursor: SMHierarchicCursor): Unit =
        try {
            streamHandler.stream(rootCursor)
        } finally {
            rootCursor.streamReader.closeCompletely()
        }
}